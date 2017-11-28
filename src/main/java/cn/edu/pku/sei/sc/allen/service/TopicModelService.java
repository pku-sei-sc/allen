package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.algorithm.TopicModel;
import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.DataChunkMeta;
import cn.edu.pku.sei.sc.allen.model.TaskStatus;
import cn.edu.pku.sei.sc.allen.model.TrainingTask;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.TrainingTaskStorage;
import cn.edu.pku.sei.sc.allen.view.TrainingProgress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by dell on 2017/11/27.
 */
@Service
public class TopicModelService {

    @Autowired
    private InputDataService inputDataService;

    @Autowired
    private TrainingTaskStorage trainingTaskStorage;

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    private Map<Long, TopicModel> topicModelMap = new ConcurrentHashMap<>();

    public TrainingTask createTrainingTask(List<Long> dataChunkIds, int totalTopics, double alphaSum, int numIteration,
                                           int showTopicsInterval, int showTopicsNum) {
        List<DataChunkMeta> dataChunkMetas = dataChunkMetaStorage.findAll(dataChunkIds);
        if (dataChunkMetas.size() != dataChunkIds.size())
            throw new BadRequestException("包含不存在的数据块");

        TrainingTask trainingTask = new TrainingTask()
                .setDataChunkIds(dataChunkIds)
                .setTotalTopics(totalTopics)
                .setAlphaSum(alphaSum)
                .setNumIteration(numIteration)
                .setShowTopicsInterval(showTopicsInterval)
                .setShowTopicsNum(showTopicsNum)
                .setStatus(TaskStatus.Stopped);

        return trainingTaskStorage.save(trainingTask);
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

        //加载数据块

        List<DataChunk> dataChunks = new ArrayList<>();
        for (DataChunkMeta dataChunkMeta : dataChunkMetas)
            dataChunks.add(inputDataService.loadDataChunk(dataChunkMeta));

        TopicModel topicModel = new TopicModel(trainingTaskId,
                trainingTask.getTotalTopics(),
                trainingTask.getAlphaSum(),
                trainingTask.getNumIteration(),
                trainingTask.getShowTopicsInterval(),
                trainingTask.getShowTopicsNum());

        if (topicModelMap.putIfAbsent(trainingTaskId, topicModel) != null)
            throw new IllegalStateException("当前训练任务已经在运行");
        try {
            trainingTask.setStatus(TaskStatus.Processing);
            trainingTaskStorage.save(trainingTask);

            topicModel.loadDataChunks(dataChunks);
            topicModel.training();



            trainingTask.setStatus(TaskStatus.Finished);
            trainingTaskStorage.save(trainingTask);
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
                        .setFinishedIteration(topicModel.getFinishedIteration())
                        .setMaxIteration(topicModel.getMaxIteration())
                        .setTotalCost(topicModel.getTotalCost());
            }
            default:
                throw new IllegalStateException("未知状态");
        }
    }


}
