package cn.edu.pku.sei.sc.allen.service;

import cc.mallet.topics.TopicInferencer;
import cn.edu.pku.sei.sc.allen.algorithm.TopicModel;
import cn.edu.pku.sei.sc.allen.model.AD;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.InferenceTaskStorage;
import cn.edu.pku.sei.sc.allen.storage.TrainingTaskStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ADService {



    public void ADRun(String manifestID1,String manifestID2,String method) throws IOException {
        String InferTaskID1 = "result" + "/" + manifestID1 + "/result.ret";
        String InferTaskID2 = "result" + "/" + manifestID2 + "/result.ret";
        AD ad = new AD();
        ad.AbnormalDetection(InferTaskID1,InferTaskID2,method,10);
    }
}
