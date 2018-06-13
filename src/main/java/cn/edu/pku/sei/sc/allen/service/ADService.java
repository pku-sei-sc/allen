package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.algorithm.TopicModel;
import cn.edu.pku.sei.sc.allen.model.*;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.InferenceTaskStorage;
import cn.edu.pku.sei.sc.allen.storage.TrainingTaskStorage;
import cn.edu.pku.sei.sc.allen.util.RuleUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ADService {

    @Autowired
    private InferenceTaskStorage inferenceTaskStorage;

    @Autowired
    private InputDataService inputDataService;

    @Autowired
    private TopicModelService topicModelService;

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    @Autowired
    private TrainingTaskStorage trainingTaskStorage;

    @Async
    public void adRun(String manifestID1, String manifestID2, String method, int top) throws IOException {
        String InferTaskID1 = "result" + "/" + manifestID1 + "/result.ret";
        String InferTaskID2 = "result" + "/" + manifestID2 + "/result.ret";

        InferenceTask task1 = inferenceTaskStorage.findByManifestId(manifestID1);
        InferenceTask task2 = inferenceTaskStorage.findByManifestId(manifestID2);


        DataChunk dataChunk1 = inputDataService.loadDataChunk(task1.getDataChunkId());
        DataChunk dataChunk2 = inputDataService.loadDataChunk(task2.getDataChunkId());

        AD ad = new AD();
        ad.abnormalDetection(InferTaskID1, InferTaskID2, method, top, dataChunk1, dataChunk2);
    }

    @Async
    public void match(String modelMenifest, List<Long> datachunkIds, String rulefile) throws IOException {

        TopicModel topicModel = new TopicModel(10001, 123);

        //加载模型
        DataFormat.MVMATopicModel model = topicModelService.loadTopicModel(modelMenifest);

        //加载数据块
        List<DataChunkMeta> dataChunkMetas = dataChunkMetaStorage.findAll(datachunkIds);
        List<DataChunk> dataChunks = new ArrayList<>();
        for (DataChunkMeta dataChunkMeta : dataChunkMetas)
            dataChunks.add(inputDataService.loadDataChunk(dataChunkMeta));

        Long index = new Long(6);

        Rule rule = RuleUtil.loadRule(rulefile);
        topicModel.loadDataChunks2(dataChunks, rule, model);

        //释放读取的数据资源
        dataChunks.clear();
        System.gc();
        topicModel.match();


    }
}
