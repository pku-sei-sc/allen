package cn.edu.pku.sei.sc.allen.algorithm;

import cc.mallet.topics.MVMATopicModel;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.Rule;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;

import java.util.*;

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
                tokenCount += token.getCount();

            FeatureSequence featureSequence = new FeatureSequence(alphabet, tokenCount);
            float[] values = new float[0];
            if (hasValue)
                values = new float[tokenCount];

            tokenCount = 0;
            for (DataFormat.Token token : dataInstance.getTokensList()) {
                Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                entry = rule.getSynonym(dataChunkId, (String) entry);
                if (stopWordSet.contains(((String) entry).toLowerCase())) continue;

                int type = alphabet.lookupIndex(entry);
                for (int j = 0; j < token.getCount(); j++) {
                    featureSequence.add(type);
                    if (hasValue)
                        values[tokenCount++] = token.getValues(j);
                }
            }

            instanceList.add(new Instance(featureSequence, null, dataChunk.getInstanceAlphabet().lookupObject(i), null));
            if (hasValue)
                valueList.add(values);
        }
    }

    public void training() {
        mvmaTopicModel = new MVMATopicModel(totalTopics, (float) alphaSum, (float) betaSum, randomSeed, taskId);

        mvmaTopicModel.addTrainingInstances(instanceLists, valueLists);
        mvmaTopicModel.setNumIterations(numIteration);
        mvmaTopicModel.setTopicDisplay(showTopicsInterval, showTopicsNum);
        mvmaTopicModel.training();
    }



    public void inference(int numIteration, int burnId, int thinning) {

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



}
