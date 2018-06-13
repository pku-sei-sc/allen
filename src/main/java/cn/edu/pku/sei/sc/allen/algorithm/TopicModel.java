package cn.edu.pku.sei.sc.allen.algorithm;

import cc.mallet.topics.MVMATopicModel;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.Rule;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.model.data.ResultData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * 默认是MVMALDA算法
 * Created by dell on 2017/11/28.
 */
public class TopicModel {

    private long taskId;

    private int totalTopics;

    private double alphaSum;

    private double betaSum;

    private long randomSeed;

    private int numIteration;

    private int showTopicsInterval;

    private int showTopicsNum;

    private InstanceList[] instanceLists;

    private ArrayList<float[]>[] valueLists;

    private InstanceList instanceList;

    private ArrayList<float[]> valueList;

    private MVMATopicModel mvmaTopicModel;

    private int language;

    public TopicModel(long taskId, int totalTopics, double alphaSum, double betaSum, long randomSeed, int numIteration, int showTopicsInterval, int showTopicsNum) {
        this.taskId = taskId;
        this.totalTopics = totalTopics;
        this.alphaSum = alphaSum;
        this.betaSum = betaSum;
        this.randomSeed = randomSeed;
        this.numIteration = numIteration;
        this.showTopicsInterval = showTopicsInterval;
        this.showTopicsNum = showTopicsNum;
    }

    public TopicModel(long taskId, long randomSeed) {
        this.taskId = taskId;
        this.randomSeed = randomSeed;
    }

    @SuppressWarnings("unchecked")
    public void loadDataChunks(List<DataChunk> dataChunks, Rule rule) {
        //不同视图下实例求交集
        Set<Object> commonInstanceIds = new TreeSet<>(Arrays.asList(dataChunks.get(0).getInstanceAlphabet().toArray()));
        for (DataChunk dataChunk : dataChunks) {
            Set<Object> tempInstanceIds = new HashSet<>(Arrays.asList(dataChunk.getInstanceAlphabet().toArray()));
            commonInstanceIds.retainAll(tempInstanceIds);
        }

        instanceLists = new InstanceList[dataChunks.size()];
        valueLists = new ArrayList[dataChunks.size()];

        //视图循环
        for (int i = 0; i < dataChunks.size(); i++) {
            DataChunk dataChunk = dataChunks.get(i);
            long dataChunkId = dataChunk.getMeta().getId();

            Alphabet alphabet = new Alphabet(String.class);

            Set<String> stopWordSet = new HashSet<>();
            for (String stopWord : rule.getStopWords(dataChunkId))
                stopWordSet.add(stopWord.trim().toLowerCase());

            instanceLists[i] = new InstanceList(alphabet, null);
            if (dataChunk.hasValue())
                valueLists[i] = new ArrayList<>();

            //公有实例循环
            for (Object commonInstanceId : commonInstanceIds) {
                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                int tokenCount = 0;
                for (DataFormat.Token token : dataInstance.getTokensList())
                    tokenCount += token.getCount();

                FeatureSequence featureSequence = new FeatureSequence(alphabet, tokenCount);
                float[] values = new float[0];
                if (dataChunk.hasValue())
                    values = new float[tokenCount];

                //加载词语和属性值
                tokenCount = 0;
                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                    entry = rule.getSynonym(dataChunkId, (String) entry); //同义词替换
                    if (stopWordSet.contains(((String) entry).toLowerCase())) continue; //去除停用词

                    int type = alphabet.lookupIndex(entry);
                    for (int j = 0; j < token.getCount(); j++) {
                        featureSequence.add(type);
                        if (dataChunk.hasValue())
                            values[tokenCount++] = token.getValues(j);
                    }
                }

                instanceLists[i].add(new Instance(featureSequence, null, commonInstanceId, null));
                if (dataChunk.hasValue())
                    valueLists[i].add(values);
            }
        }
    }

    public void loadDataChunk(DataFormat.MVMATopicModel model, DataChunk dataChunk, Rule rule, int language) {
        this.language = language;

        int scale = 20;

        mvmaTopicModel = new MVMATopicModel(model, randomSeed, taskId);
        Alphabet alphabet = mvmaTopicModel.getAlphabet(language);
        long dataChunkId = dataChunk.getMeta().getId();
        boolean hasValue = dataChunk.hasValue() && mvmaTopicModel.hasValue(language);


        Set<String> stopWordSet = new HashSet<>();
        for (String stopWord : rule.getStopWords(dataChunkId))
            stopWordSet.add(stopWord.trim().toLowerCase());

        instanceList = new InstanceList(alphabet, null);
        if (hasValue)
            valueList = new ArrayList<>();

        for (int i = 0; i < dataChunk.getInstanceAlphabet().size(); i++) {
            DataFormat.Instance dataInstance = dataChunk.getInstances().get(i);

            int tokenCount = 0;
            for (DataFormat.Token token : dataInstance.getTokensList())
                tokenCount += token.getCount() * scale;

            FeatureSequence featureSequence = new FeatureSequence(alphabet, tokenCount);
            float[] values = new float[0];
            if (hasValue)
                values = new float[tokenCount];

            tokenCount = 0;
            for (DataFormat.Token token : dataInstance.getTokensList()) {
                Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                entry = rule.getSynonym(dataChunkId, (String) entry);
                if (stopWordSet.contains(((String) entry).toLowerCase())) continue;
                if (!alphabet.contains(entry)) continue;

                int type = alphabet.lookupIndex(entry);
                for (int j = 0; j < token.getCount(); j++) {
                    for (int k = 0; k < scale; k++) {
                        featureSequence.add(type);
                        if (hasValue)
                            values[tokenCount++] = token.getValues(j);
                    }
                }
            }

            if (featureSequence.size() != 0 ) {
                instanceList.add(new Instance(featureSequence, null, dataChunk.getInstanceAlphabet().lookupObject(i), null));
                if (hasValue)
                    valueList.add(values);
            }
        }
    }

    public void loadDataChunks2(List<DataChunk> dataChunks, Rule rule, DataFormat.MVMATopicModel model){
        Set<Object> commonInstanceIds = new TreeSet<>(Arrays.asList(dataChunks.get(0).getInstanceAlphabet().toArray()));
        Set<Object> tagset = new TreeSet<>();
        for (DataChunk dataChunk : dataChunks) {
            Set<Object> tempInstanceIds = new HashSet<>(Arrays.asList(dataChunk.getInstanceAlphabet().toArray()));
            commonInstanceIds.retainAll(tempInstanceIds);
        }

        mvmaTopicModel = new MVMATopicModel(model, randomSeed, taskId);

        List<Long> dataChunkIds = new ArrayList<>();
        for(DataChunk datachunk:dataChunks){
            dataChunkIds.add(datachunk.getMeta().getId());
        }

        instanceLists = new InstanceList[dataChunks.size()];
        //求交集
        for(int i = 0; i < dataChunks.size(); i++){
            DataChunk dataChunk = dataChunks.get(i);
            long dataChunkId = dataChunk.getMeta().getId();
            Alphabet alphabet = mvmaTopicModel.getAlphabet(i==1? 0:1);
            Set<String> stopWordSet = new HashSet<>();
            for (String stopWord : rule.getStopWords(dataChunkId))
                stopWordSet.add(stopWord.trim().toLowerCase());
            for (Object commonInstanceId : commonInstanceIds) {
                if(tagset.contains(commonInstanceId))continue;
                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                int tokenCount = 0;
                for (DataFormat.Token token : dataInstance.getTokensList())
                    tokenCount += token.getCount() ;
                FeatureSequence featureSequence = new FeatureSequence(alphabet, tokenCount);
                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                    entry = rule.getSynonym(dataChunkId, (String) entry);
                    if (stopWordSet.contains(((String) entry).toLowerCase())) continue;

                    if (!alphabet.contains(entry)){
                        System.out.println("type"+ entry);
                        continue;
                    }

                    int type = alphabet.lookupIndex(entry);

                    for (int j = 0; j < token.getCount(); j++) {
                        featureSequence.add(type);
                    }
                }
                if(featureSequence.size() == 0){
                    tagset.add(commonInstanceId);
                    continue;
                }
            }
        }

        for(int i = 0; i < dataChunks.size(); i++){
            DataChunk dataChunk = dataChunks.get(i);
            long dataChunkId = dataChunk.getMeta().getId();
            Alphabet alphabet = mvmaTopicModel.getAlphabet(i==1? 0:1);
            Set<String> stopWordSet = new HashSet<>();
            for (String stopWord : rule.getStopWords(dataChunkId))
                stopWordSet.add(stopWord.trim().toLowerCase());
            instanceLists[i] = new InstanceList(alphabet, null);

            for (Object commonInstanceId : commonInstanceIds) {
                if(tagset.contains(commonInstanceId))continue;
                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                int tokenCount = 0;
                for (DataFormat.Token token : dataInstance.getTokensList())
                    tokenCount += token.getCount() ;

                FeatureSequence featureSequence = new FeatureSequence(alphabet, tokenCount);

                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                    entry = rule.getSynonym(dataChunkId, (String) entry);
                    if (stopWordSet.contains(((String) entry).toLowerCase())) continue;

                    if (!alphabet.contains(entry)){
                        continue;
                    }

                    int type = alphabet.lookupIndex(entry);

                    for (int j = 0; j < token.getCount(); j++) {
                            featureSequence.add(type);
                    }
                }
                if(featureSequence.size() == 0){
                    tagset.add(commonInstanceId);
                    continue;
                }

                    instanceLists[i].add(new Instance(featureSequence, null, commonInstanceId, null));

            }
            System.out.println(instanceLists[i].size()+"asdasd");
        }

    }
    public void match() throws IOException {
        List<ResultData> result = new ArrayList<>();

        for(int i = 0;i < instanceLists[0].size(); i++){
            int s = mvmaTopicModel.match(instanceLists[0].get(i) ,instanceLists[1].get(i));
            String name = (String) instanceLists[0].get(i).getName();
            ResultData resultData = new ResultData(name, s);
            result.add(resultData);
        }
        result.sort(Comparator.comparingDouble(ResultData::getSimilarity).reversed());
        File outputFile = new File("ADresult/result"+".txt");
        FileWriter fileWriter = new FileWriter(outputFile);
        for (int i = 0; i< result.size();i++){
            fileWriter.write(result.get(i).index+" "+result.get(i).getSimilarity()+"\n");
        }
        fileWriter.close();
    }

    public void training() throws ExecutionException, InterruptedException {
        mvmaTopicModel = new MVMATopicModel(totalTopics, (float) alphaSum, (float) betaSum, randomSeed, 16, taskId);

        mvmaTopicModel.addTrainingInstances(instanceLists, valueLists);
        mvmaTopicModel.setNumIterations(numIteration);
        mvmaTopicModel.setTopicDisplay(showTopicsInterval, showTopicsNum);
        mvmaTopicModel.training();
    }

    public void inference(int numIteration, int burnIn, int thinning) throws ExecutionException, InterruptedException {
        mvmaTopicModel.inference(instanceList, language, numIteration, burnIn, thinning);
    }

    public DataFormat.InferenceResult getInferenceResult() {
        checkMVMATopicModel();
        return mvmaTopicModel.storeInferenceResult();
    }

    public DataFormat.MVMATopicModel getModel() {
        checkMVMATopicModel();
        return mvmaTopicModel.storeModel();
    }

    private void checkMVMATopicModel() {
        if (mvmaTopicModel == null)
            throw new IllegalStateException("还未开始训练");
    }

    public int getCurrentIteration() {
        checkMVMATopicModel();
        return mvmaTopicModel.getIterationSoFar();
    }

    public int getMaxIteration() {
        checkMVMATopicModel();
        return mvmaTopicModel.getMaxIteration();
    }

    public long getTotalCost() {
        checkMVMATopicModel();
        return mvmaTopicModel.getTotalTime();
    }


//    public List<Float> getAbnormal(DataFormat.MVMATopicModel model, List<DataChunk> dataChunks,Rule rule ){
//        PMADSimMeasure sim = null;
//        List<Float> like = new ArrayList<>() ;
//
//        loadDataChunks(dataChunks,rule);
//        MVMATopicModel mvmaTopicModel = new MVMATopicModel(model.getNumTopics(), model.getAlphaSum(),model.getBetaSum(), randomSeed, taskId);
//        mvmaTopicModel.loadmodel(model);
//
//        for (int i=0;i<instanceLists[0].size();i++){
//            Instance instanceA = instanceLists[0].get(i);
//            Instance instanceB = instanceLists[1].get(i);
//            Float[] p1 = mvmaTopicModel.inference(instanceA,0,10);
//            Float[] p2 =mvmaTopicModel.inference(instanceB,1,10);
//            like.add(sim.innerProduct(p1,p2));
//        }
//        return like;
//    }

}
