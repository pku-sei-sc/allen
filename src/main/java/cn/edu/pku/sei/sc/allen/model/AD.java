package cn.edu.pku.sei.sc.allen.model;

import cc.mallet.topics.MVMATopicModel;
import cn.edu.pku.sei.sc.allen.algorithm.PMADSimMeasure;
import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.model.data.ResultData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class AD {

    private PMADSimMeasure pmadSimMeasure;
    private List<ResultData> result;
    private Map<String,float[]> map1;
    private Map<String,float[]> map2;

    private static final Logger log = LoggerFactory.getLogger(MVMATopicModel.class);
    private Set<String> set;

    public AD(){

    }


    public void abnormalDetection(String trainingTaskID1, String trainingTaskID2, String method, int top, DataChunk dataChunk1, DataChunk dataChunk2) throws IOException {
        log.info("Abnormal Detected ID Similarity:");
        pmadSimMeasure = new PMADSimMeasure();
        DataFormat.InferenceResult result1= load(trainingTaskID1);
        DataFormat.InferenceResult result2= load(trainingTaskID2);
        Set<String> set1 = new HashSet<>();
        Set<String> set = new HashSet<>();
        float[] p1;
        result = new ArrayList<>();
        map1 = new HashMap<>();
        map2 = new HashMap<>();


        for(int i=0;i<result2.getInstanceTopicDistsCount();i++){
            set1.add(result2.getInstanceTopicDists(i).getInstanceId());
        }
        for (int i=0;i<result1.getInstanceTopicDistsCount();i++){
            if(set1.contains(result1.getInstanceTopicDists(i).getInstanceId())){
                set.add(result1.getInstanceTopicDists(i).getInstanceId());
                p1=new float[result1.getNumTopics()];
                for (int j = 0; j < result1.getInstanceTopicDists(i).getTopicShareList().size(); j++)
                    p1[j]=result1.getInstanceTopicDists(i).getTopicShare(j);
                map1.put(result1.getInstanceTopicDists(i).getInstanceId(), p1);

            }
        }

        for(int i=0;i<result2.getInstanceTopicDistsCount();i++){
            if(set.contains(result2.getInstanceTopicDists(i).getInstanceId())){

                p1=new float[result1.getNumTopics()];
                for (int j = 0; j < result2.getInstanceTopicDists(i).getTopicShareList().size(); j++)
                    p1[j]=result2.getInstanceTopicDists(i).getTopicShare(j);
                map2.put(result2.getInstanceTopicDists(i).getInstanceId(), p1);
//                log.info("Abnormal  Similarity:{} Similarity:{}",map2.get(Integer.valueOf(result2.getInstanceTopicDists(i).getInstanceId())));

            }

        }
//        log.info("Abnormal  Similarity:{} Similarity:{}",set);


//        for(String i :set){
//            switch (method){
//                case "inner":{
////                    log.info("Abnormal  Similarity:{} Similarity:{}",map1.get(i),map2.get(i));
//                    ResultData resultData = new ResultData(i,pmadSimMeasure.innerProduct(map1.get(i),map2.get(i)));
//                    result.add(resultData);
////                    log.info("Abnormal  Similarity:{} Similarity:{}",map1.get(i),map2.get(i));
//
//                    break;
//                }
//                case "cosine":{
//
//                    ResultData resultData = new ResultData(i,pmadSimMeasure.cosineSimilarity(map1.get(i),map2.get(i)));
//                    result.add(resultData);
//                    break;
//                }
//                case "kl":{
//
//                    ResultData resultData = new ResultData(i,pmadSimMeasure.klDivergence(map1.get(i),map2.get(i)));
//                    result.add(resultData);
//                    break;
//                }
//            }
//
//        }

        switch (method) {
            case "inner":{
                for (String s : set) {
                    ResultData resultData = new ResultData(s,pmadSimMeasure.innerProduct(map1.get(s),map2.get(s)));
                    result.add(resultData);
                }
                result.sort(Comparator.comparingDouble(ResultData::getSimilarity));
                break;
            }
            case "cosine":{
                for (String i : set) {
                    ResultData resultData = new ResultData(i,pmadSimMeasure.cosineSimilarity(map1.get(i),map2.get(i)));
                    result.add(resultData);
                }
                result.sort(Comparator.comparingDouble(ResultData::getSimilarity));
                break;
            }
            case "kl":{
                for (String i : set) {
//                    if (i.startsWith("1410001") || i.startsWith("3211002") || i.startsWith("6529001")) continue;
//                    if (!i.startsWith("3301002")) continue;
                    ResultData resultData = new ResultData(i,pmadSimMeasure.klDivergence(map1.get(i),map2.get(i)));
                    result.add(resultData);
                }
                result.sort(Comparator.comparingDouble(ResultData::getSimilarity).reversed());
                break;
            }
        }

        File outputFile = new File("ADresult/result"+"_"+method+".txt");
        FileWriter fileWriter = new FileWriter(outputFile);
        for (int i = 0; i< result.size();i++){
            fileWriter.write(result.get(i).index);
        }
        for (int i = 0; i< result.size();i++){
            String instanceId = result.get(i).index;
            int instanceIdx1 = dataChunk1.getInstanceAlphabet().lookupIndex(instanceId);
            int instanceIdx2 = dataChunk2.getInstanceAlphabet().lookupIndex(instanceId);

            StringBuilder stringBuilder = new StringBuilder().append("Abnormal language 1 tokens:");
            for (DataFormat.Token token : dataChunk1.getInstances().get(instanceIdx1).getTokensList()) {
                stringBuilder.append("[").append(dataChunk1.getTokenAlphabet().lookupObject(token.getType()))
                        .append(":").append(token.getCount()).append("] ");
            }
            fileWriter.write(stringBuilder.toString());

            stringBuilder = new StringBuilder().append("Abnormal language 2 tokens:");
            for (DataFormat.Token token : dataChunk2.getInstances().get(instanceIdx2).getTokensList()) {
                stringBuilder.append("[").append(dataChunk2.getTokenAlphabet().lookupObject(token.getType()))
                        .append(":").append(token.getCount()).append("] ");
            }
            fileWriter.write(stringBuilder.toString());

            fileWriter.write(map1.get(instanceId).toString());
            fileWriter.write(map2.get(instanceId).toString());

        }

        for (int i = 0; i < top; i++) {
            String instanceId = result.get(i).index;
            log.info("Abnormal detected ID:{} {}:{}", result.get(i).index, method, result.get(i).getSimilarity());

            int instanceIdx1 = dataChunk1.getInstanceAlphabet().lookupIndex(instanceId);
            int instanceIdx2 = dataChunk2.getInstanceAlphabet().lookupIndex(instanceId);

            StringBuilder stringBuilder = new StringBuilder().append("Abnormal language 1 tokens:");
            for (DataFormat.Token token : dataChunk1.getInstances().get(instanceIdx1).getTokensList()) {
                stringBuilder.append("[").append(dataChunk1.getTokenAlphabet().lookupObject(token.getType()))
                        .append(":").append(token.getCount()).append("] ");
            }
            log.info(stringBuilder.toString());

            stringBuilder = new StringBuilder().append("Abnormal language 2 tokens:");
            for (DataFormat.Token token : dataChunk2.getInstances().get(instanceIdx2).getTokensList()) {
                stringBuilder.append("[").append(dataChunk2.getTokenAlphabet().lookupObject(token.getType()))
                        .append(":").append(token.getCount()).append("] ");
            }
            log.info(stringBuilder.toString());

            log.info("Abnormal language 1 topics:{}", map1.get(instanceId));
            log.info("Abnormal language 2 topics:{}", map2.get(instanceId));
        }

    }

    public DataFormat.InferenceResult load(String path ) throws IOException {
        File resultFile = new File(path);
        if (!resultFile.isFile())
            throw new BadRequestException("找不到输出文件");

        try (FileInputStream fileInputStream = new FileInputStream(resultFile)) {
            return DataFormat.InferenceResult.parseFrom(fileInputStream);
        }
    }

}
