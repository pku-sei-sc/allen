package cn.edu.pku.sei.sc.allen.algorithm;

import cc.mallet.topics.MVMATopicModel;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
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

    private int numIteration;

    private int showTopicsInterval;

    private int showTopicsNum;

    private InstanceList[] instanceLists;

    private ArrayList<double[]>[] valueList;

    private MVMATopicModel mvmaTopicModel;

    public TopicModel(long taskId, int totalTopics, double alphaSum, int numIteration, int showTopicsInterval, int showTopicsNum) {
        this.taskId = taskId;
        this.totalTopics = totalTopics;
        this.alphaSum = alphaSum;
        this.numIteration = numIteration;
        this.showTopicsInterval = showTopicsInterval;
        this.showTopicsNum = showTopicsNum;
    }

    @SuppressWarnings("unchecked")
    public void loadDataChunks(List<DataChunk> dataChunks) {
        Set<Object> commonInstanceIds = new TreeSet<>(Arrays.asList(dataChunks.get(0).getInstanceAlphabet().toArray()));
        for (DataChunk dataChunk : dataChunks) {
            Set<Object> tempInstanceIds = new HashSet<>(Arrays.asList(dataChunk.getInstanceAlphabet().toArray()));
            commonInstanceIds.retainAll(tempInstanceIds);
        }

        instanceLists = new InstanceList[dataChunks.size()];
        valueList = new ArrayList[dataChunks.size()];

        //视图循环
        for (int i = 0; i < dataChunks.size(); i++) {
            DataChunk dataChunk = dataChunks.get(i);
            instanceLists[i] = new InstanceList(dataChunk.getTokenAlphabet(), null);
            valueList[i] = new ArrayList<>();

            //公有实例循环
            for (Object commonInstanceId : commonInstanceIds) {
                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                int tokenCount = 0;
                for (DataFormat.Token token : dataInstance.getTokensList())
                    tokenCount += token.getCount();

                FeatureSequence featureSequence = new FeatureSequence(dataChunk.getTokenAlphabet(), tokenCount);
                double[] values = new double[tokenCount];



                //加载词语和属性值
                tokenCount = 0;
                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    for (int j = 0; j < token.getCount(); j++) {
                        featureSequence.add(token.getType());
                        if (dataChunk.hasValue())
                            values[tokenCount++] = token.getValues(j);
                    }
                }

                instanceLists[i].add(new Instance(featureSequence, null, commonInstanceId, null));
                valueList[i].add(values);
            }
        }
    }

    public void training() {
        mvmaTopicModel = new MVMATopicModel(totalTopics, alphaSum, taskId);
        mvmaTopicModel.setNumIterations(numIteration);
        mvmaTopicModel.setTopicDisplay(showTopicsInterval, showTopicsNum);
        mvmaTopicModel.addInstancesWithValues(instanceLists, valueList);
        mvmaTopicModel.estimate();
    }

    private void checkMVMATopicModel() {
        if (mvmaTopicModel == null)
            throw new IllegalStateException("还未开始训练");
    }

    public int getFinishedIteration() {
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
