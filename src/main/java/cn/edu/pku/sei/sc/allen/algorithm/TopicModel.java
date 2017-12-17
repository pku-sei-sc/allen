package cn.edu.pku.sei.sc.allen.algorithm;

import cc.mallet.topics.MVMATopicModel;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.Rule;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 默认是MVMALDA算法
 * Created by dell on 2017/11/28.
 */
@SuppressWarnings("ALL")
public class TopicModel {

    private static Logger logger = LoggerFactory.getLogger(TopicModel.class);

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


    //FIXME 这个方法需要深度优化提高性能。

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

    /**
     * 加载原始文档数据，并按照词语重组生成训练文档。
     * @param dataChunks
     * @param rule
     */
    @SuppressWarnings("unchecked")
    public void loadDataChunksReform(List<DataChunk> dataChunks, Rule rule) {
        //不同视图下实例求交集
        Set<Object> commonInstanceIds = new TreeSet<>(Arrays.asList(dataChunks.get(0).getInstanceAlphabet().toArray()));
        for (DataChunk dataChunk : dataChunks) {
            Set<Object> tempInstanceIds = new HashSet<>(Arrays.asList(dataChunk.getInstanceAlphabet().toArray()));
            commonInstanceIds.retainAll(tempInstanceIds);
        }

        int numLanguage = dataChunks.size();

        instanceLists = new InstanceList[numLanguage];
        valueLists = new ArrayList[numLanguage];
        Alphabet[] alphabets = new Alphabet[numLanguage];

        Map<String, List<Integer>[]> featureSequenceMap = new HashMap<>();
        Map<String, List<Float>[]> valueSequenceMap = new HashMap<>();

        List<Integer>[] allTypes = new ArrayList[numLanguage];
        List<Integer>[] allCounts = new ArrayList[numLanguage];
        List<Float>[] allValues = new ArrayList[numLanguage];

        for (int i = 0; i < numLanguage; i++) {
            alphabets[i] = new Alphabet(String.class);
            allTypes[i] = new ArrayList<>();
            allCounts[i] = new ArrayList<>();
            allValues[i] = new ArrayList<>();
            instanceLists[i] = new InstanceList(alphabets[i], null);
            if (dataChunks.get(i).hasValue())
                valueList = new ArrayList<>();
        }

        int[] numOriginTokens = new int[numLanguage];

        for (Object commonInstanceId : commonInstanceIds) {


            for (int i = 0; i < numLanguage; i++) {
                DataChunk dataChunk = dataChunks.get(i);
                long dataChunkId = dataChunk.getMeta().getId();

                Alphabet alphabet = alphabets[i];
                List<Integer> types = allTypes[i];
                List<Integer> counts = allCounts[i];
                List<Float> values = allValues[i];

                types.clear();
                counts.clear();
                values.clear();

                Set<String> stopWordSet = new HashSet<>();
                for (String stopWord : rule.getStopWords(dataChunkId))
                    stopWordSet.add(stopWord.trim().toLowerCase());

                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                    entry = rule.getSynonym(dataChunkId, (String) entry);
                    if (stopWordSet.contains(((String) entry).toLowerCase())) continue;

                    types.add(alphabet.lookupIndex(entry));
                    counts.add(token.getCount());
                    numOriginTokens[i] += token.getCount();
                    if (dataChunk.hasValue())
                        values.addAll(token.getValuesList());
                }
            }

            for (int i = 0; i < numLanguage; i++) {

                for (Integer type : allTypes[i]) {
                    String instanceId = i + "#" + type;

                    List<Integer>[] featureSequences = featureSequenceMap.get(instanceId);
                    if (featureSequences == null) {
                        featureSequences = new List[numLanguage];
                        for (int j = 0; j < numLanguage; j++)
                            featureSequences[j] = new ArrayList<>();
                        featureSequenceMap.put(instanceId, featureSequences);
                    }

                    for (int j = 0; j < numLanguage; j++) {
                        List<Integer> featureSequence = featureSequences[j];
                        List<Integer> types = allTypes[j];
                        List<Integer> counts = allCounts[j];

                        for (int k = 0; k < types.size(); k++) {
                            for (int l = 0; l < counts.get(k); l++)
                                featureSequence.add(types.get(k));
                        }
                    }

                    List<Float>[] valueSequences = valueSequenceMap.get(instanceId);
                    if (valueSequences == null) {
                        valueSequences = new List[numLanguage];
                        for (int j = 0; j < numLanguage; j++)
                            valueSequences[j] = new ArrayList<>();
                        valueSequenceMap.put(instanceId, valueSequences);
                    }

                    for (int j = 0; j < numLanguage; j++) {
                        List<Float> valueSequence = valueSequences[j];
                        List<Float> values = allValues[j];
                        if (dataChunks.get(j).hasValue())
                            valueSequence.addAll(values);
                    }
                }
            }

        }

        int[] numReformTokens = new int[numLanguage];

        for (Map.Entry<String, List<Integer>[]> entry : featureSequenceMap.entrySet()) {
            List<Integer>[] featureSequences = entry.getValue();
            List<Float>[] valueSequences = valueSequenceMap.get(entry.getKey());
            for (int i = 0; i < numLanguage; i++) {
                FeatureSequence featureSequence = new FeatureSequence(alphabets[i], featureSequences[i].size());
                for (Integer feature : featureSequences[i])
                    featureSequence.add(feature.intValue());
                instanceLists[i].add(new Instance(featureSequence, null, entry.getKey(), null));
                numReformTokens[i] += featureSequences[i].size();
                if (dataChunks.get(i).hasValue()) {
                    float[] values = new float[valueSequences[i].size()];
                    for (int j = 0; j < valueSequences[i].size(); j++)
                        values[j] = valueSequences[i].get(j);
                    valueLists[i].add(values);
                }
            }
        }

        for (int i = 0; i < numLanguage; i++) {
            logger.info("Task id:{}\t language {}\t origin tokens:{}\t origin instance:{}\t average instance length:{}", taskId, i, numOriginTokens[i], commonInstanceIds.size(), (float) numOriginTokens[i] / commonInstanceIds.size());
            logger.info("Task id:{}\t language {}\t reform tokens:{}\t reform instance:{}\t average instance length:{}", taskId, i, numReformTokens[i], featureSequenceMap.size(), (float) numReformTokens[i] / featureSequenceMap.size());
        }

        logger.info("Task id:{} reform finished!", taskId);

    }

    /**
     * 加载原始文档数据，并按照词语重组生成训练文档+简单压缩。
     * @param dataChunks
     * @param rule
     */
    @SuppressWarnings("unchecked")
    public void loadDataChunksReformCompress(List<DataChunk> dataChunks, Rule rule) {
        //不同视图下实例求交集
        Set<Object> commonInstanceIds = new TreeSet<>(Arrays.asList(dataChunks.get(0).getInstanceAlphabet().toArray()));
        for (DataChunk dataChunk : dataChunks) {
            Set<Object> tempInstanceIds = new HashSet<>(Arrays.asList(dataChunk.getInstanceAlphabet().toArray()));
            commonInstanceIds.retainAll(tempInstanceIds);
        }

        int numLanguage = dataChunks.size();

        instanceLists = new InstanceList[numLanguage];
        valueLists = new ArrayList[numLanguage];
        Alphabet[] alphabets = new Alphabet[numLanguage];

        Map<String, List<Integer>[]> featureSequenceMap = new HashMap<>();
        Map<String, List<Float>[]> valueSequenceMap = new HashMap<>();

        List<Integer>[] allTypes = new ArrayList[numLanguage];

        for (int i = 0; i < numLanguage; i++) {
            alphabets[i] = new Alphabet(String.class);
            allTypes[i] = new ArrayList<>();
            instanceLists[i] = new InstanceList(alphabets[i], null);
        }

        int[] numOriginTokens = new int[numLanguage];

        for (Object commonInstanceId : commonInstanceIds) {


            for (int i = 0; i < numLanguage; i++) {
                DataChunk dataChunk = dataChunks.get(i);
                long dataChunkId = dataChunk.getMeta().getId();

                Alphabet alphabet = alphabets[i];
                List<Integer> types = allTypes[i];

                types.clear();

                Set<String> stopWordSet = new HashSet<>();
                for (String stopWord : rule.getStopWords(dataChunkId))
                    stopWordSet.add(stopWord.trim().toLowerCase());

                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                    entry = rule.getSynonym(dataChunkId, (String) entry);
                    if (stopWordSet.contains(((String) entry).toLowerCase())) continue;

                    types.add(alphabet.lookupIndex(entry));
                    numOriginTokens[i] += token.getCount();
                }
            }

            for (int i = 0; i < numLanguage; i++) {

                for (Integer type : allTypes[i]) {
                    String instanceId = i + "#" + type;

                    List<Integer>[] featureSequences = featureSequenceMap.get(instanceId);
                    if (featureSequences == null) {
                        featureSequences = new List[numLanguage];
                        for (int j = 0; j < numLanguage; j++)
                            featureSequences[j] = new ArrayList<>();
                        featureSequenceMap.put(instanceId, featureSequences);
                    }

                    for (int j = 0; j < numLanguage; j++) {
                        List<Integer> featureSequence = featureSequences[j];
                        List<Integer> types = allTypes[j];

                        for (int k = 0; k < types.size(); k++)
                            featureSequence.add(types.get(k));
                    }
                }
            }

        }

        int[] numReformTokens = new int[numLanguage];

        for (Map.Entry<String, List<Integer>[]> entry : featureSequenceMap.entrySet()) {
            List<Integer>[] featureSequences = entry.getValue();
            for (int i = 0; i < numLanguage; i++) {
                FeatureSequence featureSequence = new FeatureSequence(alphabets[i], featureSequences[i].size());
                for (Integer feature : featureSequences[i])
                    featureSequence.add(feature.intValue());
                instanceLists[i].add(new Instance(featureSequence, null, entry.getKey(), null));
                numReformTokens[i] += featureSequences[i].size();
            }
        }

        for (int i = 0; i < numLanguage; i++) {
            logger.info("Task id:{}\t language {}\t origin tokens:{}\t origin instance:{}\t average instance length:{}", taskId, i, numOriginTokens[i], commonInstanceIds.size(), (float) numOriginTokens[i] / commonInstanceIds.size());
            logger.info("Task id:{}\t language {}\t reform tokens:{}\t reform instance:{}\t average instance length:{}", taskId, i, numReformTokens[i], featureSequenceMap.size(), (float) numReformTokens[i] / featureSequenceMap.size());
        }

        logger.info("Task id:{} reform & compress finished!", taskId);

    }

    /**
     * 加载原始文档数据，并按照词语重组生成训练文档+归约化简。
     * @param dataChunks
     * @param rule
     */
    @SuppressWarnings("unchecked")
    public void loadDataChunksReformReduction(List<DataChunk> dataChunks, Rule rule) {
        //不同视图下实例求交集
        Set<Object> commonInstanceIds = new TreeSet<>(Arrays.asList(dataChunks.get(0).getInstanceAlphabet().toArray()));
        for (DataChunk dataChunk : dataChunks) {
            Set<Object> tempInstanceIds = new HashSet<>(Arrays.asList(dataChunk.getInstanceAlphabet().toArray()));
            commonInstanceIds.retainAll(tempInstanceIds);
        }

        int numLanguage = dataChunks.size();

        instanceLists = new InstanceList[numLanguage];
        valueLists = new ArrayList[numLanguage];
        Alphabet[] alphabets = new Alphabet[numLanguage];

        Map<String, List<Integer>[]> featureSequenceMap = new HashMap<>();
        Map<String, List<Float>[]> valueSequenceMap = new HashMap<>();

        List<Integer>[] allTypes = new ArrayList[numLanguage];

        for (int i = 0; i < numLanguage; i++) {
            alphabets[i] = new Alphabet(String.class);
            allTypes[i] = new ArrayList<>();
            instanceLists[i] = new InstanceList(alphabets[i], null);
        }

        int[] numOriginTokens = new int[numLanguage];

        for (Object commonInstanceId : commonInstanceIds) {


            for (int i = 0; i < numLanguage; i++) {
                DataChunk dataChunk = dataChunks.get(i);
                long dataChunkId = dataChunk.getMeta().getId();

                Alphabet alphabet = alphabets[i];
                List<Integer> types = allTypes[i];

                types.clear();

                Set<String> stopWordSet = new HashSet<>();
                for (String stopWord : rule.getStopWords(dataChunkId))
                    stopWordSet.add(stopWord.trim().toLowerCase());

                int idx = dataChunk.getInstanceAlphabet().lookupIndex(commonInstanceId);
                DataFormat.Instance dataInstance = dataChunk.getInstances().get(idx);

                for (DataFormat.Token token : dataInstance.getTokensList()) {
                    Object entry = dataChunk.getTokenAlphabet().lookupObject(token.getType());
                    entry = rule.getSynonym(dataChunkId, (String) entry);
                    if (stopWordSet.contains(((String) entry).toLowerCase())) continue;

                    types.add(alphabet.lookupIndex(entry));
                    numOriginTokens[i] += token.getCount();
                }
            }

            for (int i = 0; i < numLanguage; i++) {

                for (Integer type : allTypes[i]) {
                    String instanceId = i + "#" + type;

                    List<Integer>[] featureSequences = featureSequenceMap.get(instanceId);
                    if (featureSequences == null) {
                        featureSequences = new List[numLanguage];
                        for (int j = 0; j < numLanguage; j++)
                            featureSequences[j] = new ArrayList<>();
                        featureSequenceMap.put(instanceId, featureSequences);
                    }

                    for (int j = 0; j < numLanguage; j++) {
                        List<Integer> featureSequence = featureSequences[j];
                        List<Integer> types = allTypes[j];

                        for (int k = 0; k < types.size(); k++)
                            featureSequence.add(types.get(k));
                    }
                }
            }

        }

        int[] numReformTokens = new int[numLanguage];
        Map<Integer, Integer> featureCountMap = new HashMap<>();

        int totalOriginTokens = 0;
        for (int i = 0; i < numLanguage; i++)
            totalOriginTokens += numOriginTokens[i];

        int reductionFactor = totalOriginTokens / commonInstanceIds.size() + 5;
        logger.info("Task id:{}\t reduction factor:{}", taskId, reductionFactor);

        for (Map.Entry<String, List<Integer>[]> entry : featureSequenceMap.entrySet()) {
            List<Integer>[] featureSequences = entry.getValue();
            for (int i = 0; i < numLanguage; i++) {
                FeatureSequence featureSequence = new FeatureSequence(alphabets[i], featureSequences[i].size());
                featureCountMap.clear();
                for (Integer feature : featureSequences[i]) {
                    Integer cnt = featureCountMap.get(feature.intValue());
                    if (cnt == null)
                        featureCountMap.put(feature.intValue(), 1);
                    else
                        featureCountMap.put(feature.intValue(), cnt + 1);
                }

                for (Map.Entry<Integer, Integer> cntEntry : featureCountMap.entrySet())
                    for (int j = 0; j < cntEntry.getValue(); j += reductionFactor)
                        featureSequence.add(cntEntry.getKey().intValue());

                instanceLists[i].add(new Instance(featureSequence, null, entry.getKey(), null));
                numReformTokens[i] += featureSequence.size();
            }
        }

        for (int i = 0; i < numLanguage; i++) {
            logger.info("Task id:{}\t language {}\t origin tokens:{}\t origin instance:{}\t average instance length:{}", taskId, i, numOriginTokens[i], commonInstanceIds.size(), (float) numOriginTokens[i] / commonInstanceIds.size());
            logger.info("Task id:{}\t language {}\t reform tokens:{}\t reform instance:{}\t average instance length:{}", taskId, i, numReformTokens[i], featureSequenceMap.size(), (float) numReformTokens[i] / featureSequenceMap.size());
        }

        logger.info("Task id:{} reform & reduction finished!", taskId);

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
                if (!alphabet.contains(entry)) continue;

                int type = alphabet.lookupIndex(entry);
                for (int j = 0; j < token.getCount(); j++) {
                    featureSequence.add(type);
                    if (hasValue)
                        values[tokenCount++] = token.getValues(j);
                }
            }

            if (featureSequence.size() != 0 ) {
                instanceList.add(new Instance(featureSequence, null, dataChunk.getInstanceAlphabet().lookupObject(i), null));
                if (hasValue)
                    valueList.add(values);
            }
        }
    }

    public void training() {
        mvmaTopicModel = new MVMATopicModel(totalTopics, (float) alphaSum, (float) betaSum, randomSeed, taskId);

        mvmaTopicModel.addTrainingInstances(instanceLists, valueLists);
        mvmaTopicModel.setNumIterations(numIteration);
        mvmaTopicModel.setTopicDisplay(showTopicsInterval, showTopicsNum);
        mvmaTopicModel.training();
    }

    public void inference(int numIteration, int burnIn, int thinning) {
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

}
