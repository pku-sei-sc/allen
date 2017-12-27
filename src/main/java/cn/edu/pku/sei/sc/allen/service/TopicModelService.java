package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.algorithm.TopicModel;
import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.*;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.InferenceTaskStorage;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

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
    private InferenceTaskStorage inferenceTaskStorage;

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    private Map<Long, TopicModel> trainingTaskMap = new ConcurrentHashMap<>();

    private Map<Long, TopicModel> inferenceTaskMap = new ConcurrentHashMap<>();

    private String modelPath = "model";

    private String resultPath = "result";

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }

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

        if (resultPath.endsWith("/"))
            resultPath = resultPath.substring(0, resultPath.length() - 1);
        File resultDir = new File(resultPath);
        if (!resultDir.isDirectory() && !resultDir.mkdir())
            throw new IllegalStateException("无法创建result目录，请手动创建目录后重试");
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

    private String getResultFilePath(String manifestId) {
        return resultPath + "/" + manifestId + "/result.ret";
    }

    @Async
    public void startTraining(long trainingTaskId, boolean forced) throws IOException, ExecutionException, InterruptedException {
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

        if (trainingTaskMap.putIfAbsent(trainingTaskId, topicModel) != null)
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

//            topicModel.loadDataChunksReform(dataChunks, rule);
//            topicModel.loadDataChunksReformCompress(dataChunks, rule);
            topicModel.loadDataChunksReformReduction(dataChunks, rule);
//            topicModel.loadDataChunks(dataChunks, rule);

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
            trainingTaskMap.remove(trainingTaskId);
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
                TopicModel topicModel = trainingTaskMap.get(trainingTaskId);
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


    public InferenceTask createInferenceTask(long dataChunkId, String ruleFile, String modelManifest, int language, long randomSeed,
                                             int numIterations, int burnIn, int thinning) {
        if (dataChunkMetaStorage.findOne(dataChunkId) == null)
            throw new BadRequestException("不存在的数据块");

        File modelFile = new File(getModelFilePath(modelManifest));
        if (!modelFile.isFile())
            throw new BadRequestException("不存在的训练模型");

        InferenceTask inferenceTask = new InferenceTask()
                .setDataChunkId(dataChunkId)
                .setRuleFile(ruleFile)
                .setModelManifest(modelManifest)
                .setLanguage(language)
                .setRandomSeed(randomSeed)
                .setNumIterations(numIterations)
                .setBurnIn(burnIn)
                .setThinning(thinning)
                .setStatus(TaskStatus.Stopped);

        return inferenceTaskStorage.save(inferenceTask);
    }

    @Async
    public void startInference(long inferenceTaskId, boolean forced) throws IOException {
        InferenceTask inferenceTask = inferenceTaskStorage.findOne(inferenceTaskId);

        if (inferenceTask == null)
            throw new IllegalArgumentException("不存在的训练任务");

        if (inferenceTask.getStatus() != TaskStatus.Stopped && !forced)
            throw new IllegalStateException("无法开始一个非停止状态的任务，请尝试强制开始");

        DataChunkMeta dataChunkMeta = dataChunkMetaStorage.findOne(inferenceTask.getDataChunkId());
        if (dataChunkMeta.getStatus() != TaskStatus.Finished)
            throw new IllegalStateException("选择的数据块id:" + dataChunkMeta.getId() + "未完成导入");

        TopicModel topicModel = new TopicModel(inferenceTaskId, inferenceTask.getRandomSeed());

        if (inferenceTaskMap.putIfAbsent(inferenceTaskId, topicModel) != null)
            throw new IllegalStateException("当前推断任务已经在运行");
        try {
            logger.info("Inference task id:{} start!", inferenceTaskId);

            inferenceTask.setManifestId(UUID.randomUUID().toString());
            File resultFile = new File(getResultFilePath(inferenceTask.getManifestId()));

            while (resultFile.isFile()) {
                inferenceTask.setManifestId(UUID.randomUUID().toString());
                resultFile = new File(getResultFilePath(inferenceTask.getManifestId()));
            }

            if (!resultFile.getParentFile().isDirectory() && !resultFile.getParentFile().mkdir())
                throw new IllegalStateException("无法创建文件子目录");

            //加载数据块
            DataChunk dataChunk = inputDataService.loadDataChunk(dataChunkMeta);

            //加载模型
            DataFormat.MVMATopicModel model = loadTopicModel(inferenceTask.getModelManifest());

            //加载规则
            Rule rule = RuleUtil.loadRule(inferenceTask.getRuleFile());

            topicModel.loadDataChunk(model, dataChunk, rule, inferenceTask.getLanguage());

            //释放资源
            dataChunk = null;
            model = null;
            rule = null;
            System.gc();

            topicModel.inference(inferenceTask.getNumIterations(), inferenceTask.getBurnIn(), inferenceTask.getThinning());

            DataFormat.InferenceResult result = topicModel.getInferenceResult();
            try (FileOutputStream fileOutputStream = new FileOutputStream(resultFile)) {
                result.writeTo(fileOutputStream);
            }

            inferenceTask.setStatus(TaskStatus.Finished);
            inferenceTaskStorage.save(inferenceTask);

            logger.info("Inference task id:{} finished!", inferenceTaskId);

        } finally {
            inferenceTaskMap.remove(inferenceTaskId);
        }
    }

    private DataFormat.MVMATopicModel loadTopicModel(String modelManifest) throws IOException {
        File modelFile = new File(getModelFilePath(modelManifest));
        if (!modelFile.isFile())
            throw new BadRequestException("找不到模型文件");

        try (FileInputStream fileInputStream = new FileInputStream(modelFile)) {
            return DataFormat.MVMATopicModel.parseFrom(fileInputStream);
        }
    }

}
