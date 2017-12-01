package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.algorithm.TopicModel;
import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.*;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.TrainingTaskStorage;
import cn.edu.pku.sei.sc.allen.util.RuleUtil;
import cn.edu.pku.sei.sc.allen.view.TrainingProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by dell on 2017/11/27.
 */
@Service
@ConfigurationProperties("allen.model")
public class TopicModelService {

    private static Logger logger = LoggerFactory.getLogger(TopicModelService.class);

    @Autowired
    private InputDataService inputDataService;

    @Autowired
    private TrainingTaskStorage trainingTaskStorage;

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    private Map<Long, TopicModel> topicModelMap = new ConcurrentHashMap<>();

    private String modelPath = "model";

    //region getter setter
    public String getModelPath() {
        return modelPath;
    }

    public TopicModelService setModelPath(String modelPath) {
        this.modelPath = modelPath;
        return this;
    }
    //endregion

    @PostConstruct
    private void init() {
        if (modelPath.endsWith("/"))
            modelPath = modelPath.substring(0, modelPath.length() - 1);
        File modelDir = new File(modelPath);
        if (!modelDir.isDirectory() && !modelDir.mkdir())
            throw new IllegalStateException("无法创建model目录，请手动创建目录后重试");
    }

    public TrainingTask createTrainingTask(List<Long> dataChunkIds, String ruleFile, int totalTopics,
                                           double alphaSum, double betaSum, long randomSeed, int numIteration,
                                           int showTopicsInterval, int showTopicsNum) {
        List<DataChunkMeta> dataChunkMetas = dataChunkMetaStorage.findAll(dataChunkIds);
        if (dataChunkMetas.size() != dataChunkIds.size())
            throw new BadRequestException("包含不存在的数据块");

        TrainingTask trainingTask = new TrainingTask()
                .setDataChunkIds(dataChunkIds)
                .setRuleFile(ruleFile)
                .setTotalTopics(totalTopics)
                .setAlphaSum(alphaSum)
                .setBetaSum(betaSum)
                .setRandomSeed(randomSeed)
                .setNumIteration(numIteration)
                .setShowTopicsInterval(showTopicsInterval)
                .setShowTopicsNum(showTopicsNum)
                .setStatus(TaskStatus.Stopped);

        return trainingTaskStorage.save(trainingTask);
    }

    private String getModelFilePath(String manifestId) {
        return modelPath + "/" + manifestId + "/model.mdl";
    }

    @Async
    public void startTraining(long trainingTaskId, boolean forced) throws IOException {
        TrainingTask trainingTask = trainingTaskStorage.findOne(trainingTaskId);

        //检查数据可用性

        if (trainingTask == null)
            throw new IllegalArgumentException("不存在的训练任务");

        if (trainingTask.getStatus() != TaskStatus.Stopped && !forced)
            throw new IllegalStateException("无法开始一个非停止状态的任务，尝试强制开始");

        List<DataChunkMeta> dataChunkMetas = dataChunkMetaStorage.findAll(trainingTask.getDataChunkIds());
        for (DataChunkMeta dataChunkMeta : dataChunkMetas) {
            if (dataChunkMeta.getStatus() != TaskStatus.Finished)
                throw new IllegalStateException("选择的数据块id:" + dataChunkMeta.getId() + "未完成导入");
        }

        TopicModel topicModel = new TopicModel(trainingTaskId,
                trainingTask.getTotalTopics(),
                trainingTask.getAlphaSum(),
                trainingTask.getBetaSum(),
                trainingTask.getRandomSeed(),
                trainingTask.getNumIteration(),
                trainingTask.getShowTopicsInterval(),
                trainingTask.getShowTopicsNum());

        if (topicModelMap.putIfAbsent(trainingTaskId, topicModel) != null)
            throw new IllegalStateException("当前训练任务已经在运行");
        try {
            logger.info("Training task id:{} start!", trainingTaskId);

            trainingTask.setManifestId(UUID.randomUUID().toString());
            File modelFile = new File(getModelFilePath(trainingTask.getManifestId()));

            while (modelFile.isFile()) {
                trainingTask.setManifestId(UUID.randomUUID().toString());
                modelFile = new File(getModelFilePath(trainingTask.getManifestId()));
            }

            if (!modelFile.getParentFile().isDirectory() && !modelFile.getParentFile().mkdir())
                throw new IllegalStateException("无法创建文件子目录");

            //加载数据块
            trainingTask.setStatus(TaskStatus.Processing);
            trainingTaskStorage.save(trainingTask);

            List<DataChunk> dataChunks = new ArrayList<>();
            for (DataChunkMeta dataChunkMeta : dataChunkMetas)
                dataChunks.add(inputDataService.loadDataChunk(dataChunkMeta));

            Rule rule = RuleUtil.loadRule(trainingTask.getRuleFile());

            topicModel.loadDataChunks(dataChunks, rule);

            //释放读取的数据资源
            dataChunks.clear();
            System.gc();

            topicModel.training();

            //导出模型数据并写入模型文件
            DataFormat.MVMATopicModel model = topicModel.getModel();
            try (FileOutputStream fileOutputStream = new FileOutputStream(modelFile)) {
                model.writeTo(fileOutputStream);
            }

            trainingTask.setStatus(TaskStatus.Finished);
            trainingTaskStorage.save(trainingTask);

            logger.info("Training task id:{} finished!", trainingTaskId);
        } finally {
            topicModelMap.remove(trainingTaskId);
        }
    }

    public TrainingProgress getProgress(long trainingTaskId) {
        TrainingTask trainingTask = trainingTaskStorage.findOne(trainingTaskId);
        if (trainingTask == null)
            throw new BadRequestException("不存在的训练任务");
        switch (trainingTask.getStatus()) {
            case Stopped:
                throw new BadRequestException("任务未运行");
            case Finished:
                throw new BadRequestException("任务已完成");
            case Processing: {
                TopicModel topicModel = topicModelMap.get(trainingTaskId);
                if (topicModel == null)
                    throw new BadRequestException("任务未在运行中");
                return new TrainingProgress()
                        .setCurrentIteration(topicModel.getCurrentIteration())
                        .setMaxIteration(topicModel.getMaxIteration())
                        .setTotalCost(topicModel.getTotalCost());
            }
            default:
                throw new IllegalStateException("未知状态");
        }
    }


}
