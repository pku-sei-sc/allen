package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.model.AD;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.DataChunkMeta;
import cn.edu.pku.sei.sc.allen.model.InferenceTask;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.InferenceTaskStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ADService {

    @Autowired
    private InferenceTaskStorage inferenceTaskStorage;

    @Autowired
    private InputDataService inputDataService;

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
}
