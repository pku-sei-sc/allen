package cc.mallet.topics;

import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * 最多训练一次，但可以多次Inference
 * Created by Lingxiao on 2016/10/22.
 */
public class MVMATopicModel {

    private static final Logger log = LoggerFactory.getLogger(MVMATopicModel.class);

    public class TopicAssignment implements Serializable {
        public Instance[] instances;
        public LabelSequence[] topicSequences;

        public TopicAssignment (Instance[] instances, LabelSequence[] topicSequences) {
            this.instances = instances;
            this.topicSequences = topicSequences;
        }
    }

    private int numTopics;
    private float alphaSum;
    private float betaSum;
    private int numLanguages;

    private AtomicIntegerArray[][] languageTypeTopicCounts;
    private AtomicIntegerArray[] languageTokensPerTopic;
    private float[][] languageMus;
    private float[][] languageSigma2s;
    private float[][][] languageTypeTopicSums;
    private boolean[] hasValue;
    private Alphabet[] alphabets;

    private transient int[] vocabularySizes;

    private transient float[] alphas;

    private transient float[] betas;
    private transient float[] betaSums;


    //需要设置为ThreadLocal

    private int numThreads;
    private ExecutorService executorService;

    private Future<Void>[] futures;

//    private BlockingQueue<float[]> topicTermScoresQueue;
//    private BlockingQueue<int[]> localTopicCountsQueue;
//    private BlockingQueue<float[]> localSigma2sQueue;
//    private BlockingQueue<float[]> localPowersQueue;

    private transient float[][] topicTermScores;
    private transient int[][] localTopicCounts;
    private transient float[][] localSigma2s;
    private transient float[][] localPowers;
//    private transient Randoms[] random;
//
//    private transient ThreadLocalRandom random;

    private transient LabelAlphabet topicAlphabet;

    private transient ArrayList<TopicAssignment> trainingData;
    private transient ArrayList<float[]>[] realFeatures;

    private transient int iterationsSoFar = 1;
    private transient int numIterations;
    private transient int maxIteration;

    private transient int showTopicsInterval;
    private transient int wordsPerTopic;

    private transient long randomSeed;

    private transient NumberFormat formatter;
    //用时查询
    private transient long totalTime;

    private transient long taskId;

    //region getter setter
    public int getNumIterations() {
        return numIterations;
    }

    public MVMATopicModel setNumIterations(int numIterations) {
        this.numIterations = numIterations;
        return this;
    }

    public MVMATopicModel setTopicDisplay(int showTopicsInterval, int wordsPerTopic) {
        this.showTopicsInterval = showTopicsInterval;
        this.wordsPerTopic = wordsPerTopic;
        return this;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public int getIterationSoFar() {
        return iterationsSoFar;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    //endregion

    public MVMATopicModel(int numTopics, float alphaSum, float betaSum, long randomSeed, int numThreads, long taskId) {
        this.numTopics = numTopics;
        this.alphaSum = alphaSum;
        this.betaSum = betaSum;
        this.randomSeed = randomSeed;
        this.numThreads = numThreads;
        this.taskId = taskId;
        
        this.trainingData = new ArrayList<>();
        this.topicAlphabet = new LabelAlphabet();
        for (int i = 0; i < numTopics; i++)
            this.topicAlphabet.lookupIndex("topic"+i);
        
        this.alphas = new float[numTopics];
        Arrays.fill(alphas, alphaSum / numTopics);

//        this.random = new Randoms[numThreads];
//        for (int i = 0; i < numThreads; i++)
//            this.random[i] = new Randoms();

//        this.random =

        if (numThreads > 1)
            log.warn("Model can't get stable result when use parallel accelerating.");
//        else
//            random[0] = new Randoms((int) randomSeed);
        
        formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(5);

        this.executorService = Executors.newFixedThreadPool(numThreads);
        this.futures = new Future[numThreads];
//        int ratio = 4;
//        topicTermScoresQueue = new ArrayBlockingQueue<>(ratio * numThreads);
//        localTopicCountsQueue = new ArrayBlockingQueue<>(ratio * numThreads);
//        localSigma2sQueue = new Arr
//
//        for (int i = 0; i < ratio * numThreads; i++) {
//            topicTermScoresQueue.add(new float[numTopics]);
//        }

        this.topicTermScores = new float[numThreads][numTopics];
        this.localTopicCounts = new int[numThreads][numTopics];
        this.localSigma2s = new float[numThreads][numTopics];
        this.localPowers = new float[numThreads][numTopics];
    }

    public void addTrainingInstances(InstanceList[] training, ArrayList<float[]>[] valueList) {
        realFeatures = valueList;
        numLanguages = training.length;

        languageTokensPerTopic = new AtomicIntegerArray[numLanguages];
        for (int i = 0; i < languageTokensPerTopic.length; i++)
            languageTokensPerTopic[i] = new AtomicIntegerArray(numTopics);


        alphabets = new Alphabet[numLanguages];
        vocabularySizes = new int[numLanguages];
        betas = new float[numLanguages];
        betaSums = new float[numLanguages];

        languageTypeTopicCounts = new AtomicIntegerArray[numLanguages][];
        languageTypeTopicSums = new float[numLanguages][][];
        languageMus = new float[numLanguages][];
        languageSigma2s = new float[numLanguages][];
        hasValue = new boolean[numLanguages];

        int numInstances = training[0].size();


        ArrayList<Float>[][] typeValues = new ArrayList[numLanguages][];


        for (int language = 0; language < numLanguages; language++) {

            if (training[language].size() != numInstances) {
                throw new IllegalStateException("language " + language + " has " +
                        training[language].size() + " instances, lang 0 has " +
                        numInstances);
            }

            alphabets[language] = training[language].getDataAlphabet();
            vocabularySizes[language] = alphabets[language].size();

            betas[language] = betaSum / vocabularySizes[language];
            betaSums[language] = betaSum;

            int[] typeTotals = new int[vocabularySizes[language]];
            languageTypeTopicCounts[language] = new AtomicIntegerArray[vocabularySizes[language]];

            for (int i = 0; i < languageTypeTopicCounts[language].length; i++)
                languageTypeTopicCounts[language][i] = new AtomicIntegerArray(numTopics);

            if (valueList[language] != null) {
                hasValue[language] = true;
                languageTypeTopicSums[language] = new float[vocabularySizes[language]][numTopics];
                languageMus[language] = new float[vocabularySizes[language]];
                languageSigma2s[language] = new float[vocabularySizes[language]];
                typeValues[language] = new ArrayList[vocabularySizes[language]];
                for (int i = 0; i < vocabularySizes[language]; i++) {
                    typeValues[language][i] = new ArrayList<>();
                }
            }

            for (Instance instance : training[language]) {

                FeatureSequence tokens = (FeatureSequence) instance.getData();
                for (int position = 0; position < tokens.getLength(); position++) {
                    int type = tokens.getIndexAtPosition(position);
                    typeTotals[ type ]++;
                }
            }
        }


        for (int doc = 0; doc < numInstances; doc++) {

            Instance[] instances = new Instance[ numLanguages ];
            LabelSequence[] topicSequences = new LabelSequence[ numLanguages ];

            for (int language = 0; language < numLanguages; language++) {

                AtomicIntegerArray tokensPerTopic = languageTokensPerTopic[language];

                instances[language] = training[language].get(doc);
                FeatureSequence tokens = (FeatureSequence) instances[language].getData();
                topicSequences[language] =
                        new LabelSequence(topicAlphabet, new int[ tokens.size() ]);

                int[] topics = topicSequences[language].getFeatures();

                for (int position = 0; position < tokens.size(); position++) {

                    int type = tokens.getIndexAtPosition(position);

                    int topic = ThreadLocalRandom.current().nextInt(numTopics);

                    topics[position] = topic;
                    tokensPerTopic.incrementAndGet(topic);

                    languageTypeTopicCounts[language][type].incrementAndGet(topic);
                    if (hasValue[language]) {
                        float value = valueList[language].get(doc)[position];

                        languageTypeTopicSums[language][type][topic] += value;
                        languageMus[language][type] += value;
                        typeValues[language][type].add(value);
                    }
                }
            }

            TopicAssignment t = new TopicAssignment (instances, topicSequences);
            trainingData.add (t);
        }

        for (int language = 0; language < numLanguages; language++) {
            if (hasValue[language]) {
                for (int type = 0; type < vocabularySizes[language]; type++) {
                    languageMus[language][type] /= typeValues[language][type].size();
                    for (float v : typeValues[language][type]) {
                        languageSigma2s[language][type] += (v - languageMus[language][type]) * (v - languageMus[language][type]);
                    }
                    languageSigma2s[language][type] /= typeValues[language][type].size();
                }
            }
        }
    }

    public void training() throws ExecutionException, InterruptedException {
        training(numIterations);
    }

    @SuppressWarnings("unchecked")
    public void training(int iterationsThisRound) throws ExecutionException, InterruptedException {
        if (trainingData.size() == 0)
            throw new IllegalStateException("未添加训练实例，无法开始训练!");

        maxIteration = iterationsSoFar + iterationsThisRound - 1;
        totalTime = 0;

        //ToDo: 添加训练参数基本信息显示
        log.info("Training task id:{} iteration starts!", taskId);

        for ( ; iterationsSoFar <= maxIteration; iterationsSoFar++) {
            long iterationStart = System.currentTimeMillis();

            for (int threadId = 0; threadId < numThreads; threadId++)
                futures[threadId] = (Future<Void>) executorService.submit(new SampleRunner(threadId));

            for (int threadId = 0; threadId < numThreads; threadId++)
                futures[threadId].get();

            long elapsedMillis = System.currentTimeMillis() - iterationStart;
            totalTime += elapsedMillis;

            float progress = (float) iterationsSoFar * 100 / maxIteration;

            if (iterationsSoFar % 50 == 0) {
                float ll = modelLogLikelihood();
                log.info("Training task id:{}\titeration:{}/{}\tprogress:{}%\tlast:{}ms\taverage:{}ms\ttotal:{}s\tremaining:{}s",
                        taskId, iterationsSoFar, maxIteration, String.format("%.1f", progress), elapsedMillis,
                        totalTime / iterationsSoFar, totalTime / 1000,
                        totalTime * (maxIteration - iterationsSoFar) / iterationsSoFar / 1000);
                log.info("Training task id:{}\tmodel log likelihood:{}", taskId, String.format("%.4f", ll));
            } else if (iterationsSoFar % 10 == 0 || elapsedMillis > 1000) {
                log.info("Training task id:{}\titeration:{}/{}\tprogress:{}%\tlast:{}ms\taverage:{}ms\ttotal:{}s\tremaining:{}s",
                        taskId, iterationsSoFar, maxIteration, String.format("%.1f", progress), elapsedMillis,
                        totalTime / iterationsSoFar, totalTime / 1000,
                        totalTime * (maxIteration - iterationsSoFar) / iterationsSoFar / 1000);
            } else {
                log.debug("Training task id:{}\titeration:{}/{}\tprogress:{}%\tlast:{}ms\taverage:{}ms\ttotal:{}s\tremaining:{}s",
                        taskId, iterationsSoFar, maxIteration, String.format("%.1f", progress), elapsedMillis,
                        totalTime / iterationsSoFar, totalTime / 1000,
                        totalTime * (maxIteration - iterationsSoFar) / iterationsSoFar / 1000);
            }

            if (showTopicsInterval != 0 && iterationsSoFar != 0 && iterationsSoFar % showTopicsInterval == 0) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(byteArrayOutputStream);
                printStream.println("Training task id:" + taskId + "\titeration:"+ iterationsSoFar +"/"+ maxIteration +"\tTop words:");
                printTopWords(printStream, wordsPerTopic, false);
                log.info(byteArrayOutputStream.toString());
            }
        }

        log.info("Training task id:{} iteration finished! Average cost:{}ms, total cost:{}s.", taskId, totalTime / maxIteration, totalTime / 1000);
    }

    private class SampleRunner implements Runnable {

        private int threadId;

        public SampleRunner(int threadId) {
            this.threadId = threadId;
        }

        @Override
        public void run() {
            for (int doc = threadId; doc < trainingData.size(); doc+= numThreads)
                sampleTopicsForOneDoc (trainingData.get(doc), doc, threadId);
        }
    }

    private void sampleTopicsForOneDoc (TopicAssignment topicAssignment, int docIndex, int threadId) {
        float[] topicTermScores = this.topicTermScores[threadId];
        int[] localTopicCounts = this.localTopicCounts[threadId];
        float[] localSigma2s = this.localSigma2s[threadId];
        float[] localPowers = this.localPowers[threadId];

        int type, oldTopic, newTopic;
        float value = 0;

        Arrays.fill(localTopicCounts, 0);

        for (int language = 0; language < numLanguages; language++) {
            int[] oneDocTopics = topicAssignment.topicSequences[language].getFeatures();
            int docLength = topicAssignment.topicSequences[language].getLength();

            //		populate topic counts
            for (int position = 0; position < docLength; position++) {
                localTopicCounts[oneDocTopics[position]]++;
            }
        }

        for (int language = 0; language < numLanguages; language++) {
            int[] oneDocTopics =
                    topicAssignment.topicSequences[language].getFeatures();
            int docLength =
                    topicAssignment.topicSequences[language].getLength();
            FeatureSequence tokenSequence =
                    (FeatureSequence) topicAssignment.instances[language].getData();

            AtomicIntegerArray[] typeTopicCounts = languageTypeTopicCounts[language];
            AtomicIntegerArray tokensPerTopic = languageTokensPerTopic[language];
            float beta = betas[language];
            float betaSum = betaSums[language];

            float score, sum;
            float[][] typeTopicSums = null;
            float[] mus = null;

            if (hasValue[language]) {
                typeTopicSums = languageTypeTopicSums[language];
                mus = languageMus[language];
            }

            AtomicIntegerArray currentTypeTopicCounts;

            for (int position = 0; position < docLength; position++) {
                type = tokenSequence.getIndexAtPosition(position);
                oldTopic = oneDocTopics[position];
                currentTypeTopicCounts = typeTopicCounts[type];

                localTopicCounts[oldTopic] --;
                tokensPerTopic.decrementAndGet(oldTopic);
                currentTypeTopicCounts.decrementAndGet(oldTopic);

                float regu = 0;
//                float[] localSigma2s = null;
//                float[] localPowers = null;
                if (hasValue[language]) {
                    value = realFeatures[language].get(docIndex)[position];

                    //lock
                    typeTopicSums[type][oldTopic] -= value;

//                    localSigma2s = new float[numTopics];
//                    localPowers = new float[numTopics];
                    if (languageSigma2s[language][type] != 0) {
                        float logMax = Float.NEGATIVE_INFINITY;
                        for (int topic = 0; topic < numTopics; topic++) {
                            float tmpMu = (mus[type] + typeTopicSums[type][topic]) / (1 + currentTypeTopicCounts.get(topic));
                            localSigma2s[topic] = languageSigma2s[language][type] / (1 + currentTypeTopicCounts.get(topic));
                            localPowers[topic] = -(value - tmpMu) * (value - tmpMu) / (2 * localSigma2s[topic]);
                            if (localPowers[topic] > logMax) {
                                logMax = localPowers[topic];
                            }
                        }
                        regu = -logMax;
                    }
                }

                sum = 0;

                for (int topic = 0; topic < numTopics; topic++) {
                    score =
                            (alphas[topic] + localTopicCounts[topic]) *
                                    ((beta + currentTypeTopicCounts.get(topic)) /
                                            (betaSum + tokensPerTopic.get(topic)));

                    if (hasValue[language] && languageSigma2s[language][type] != 0) {
                        float normalFactor = 1 / (float) Math.sqrt(localSigma2s[topic]);
                        normalFactor *= Math.exp(localPowers[topic] + regu);
                        score *= normalFactor;
                    }

                    sum += score;
                    topicTermScores[topic] = score;
                }

//                float sample =  (float) random[threadId].nextUniform() * sum;
                float sample = (1.0f - ThreadLocalRandom.current().nextFloat()) * sum;

                // Figure out which topic contains that point
                newTopic = -1;
                while (sample > 0.0f) {
                    newTopic++;
                    if (newTopic == numTopics - 1)
                        break;
                    sample -= topicTermScores[newTopic];
                }

                // Make sure we actually sampled a topic
                if (newTopic == -1) {
                    throw new IllegalStateException ("SimpleLDA: New topic not sampled.");
                }

                // Put that new topic into the counts
                oneDocTopics[position] = newTopic;
                localTopicCounts[newTopic]++;
                tokensPerTopic.incrementAndGet(newTopic);
                currentTypeTopicCounts.incrementAndGet(newTopic);
                if (hasValue[language]) {
                    typeTopicSums[type][newTopic] += value;
                }
            }
        }
    }

//    public List<TopicWord2> getTopWordsDist() {
//        List<TopicWord2> tws = new ArrayList<>();
////        float[][][] result = new float[numLanguages][numTopics][];
////        int[][] countTopicTotal = new int[numLanguages][numTopics];
////        for (int lan = 0; lan < numLanguages; lan++) {
////                for (int t = 0; t < numTopics; t++) {
////                    result[lan][t] = new float[vocabularySizes[lan]];
////                }
////        }
//        for (int lan = 0; lan < numLanguages; lan++) {
////			System.out.println(lan+"---------------------");
//            TopicWord2 tw = new TopicWord2();
//            int limit = tw.TOPIC_WORD_LIMIT;
//            for (int topic = 0; topic < numTopics; topic++) {
////				System.out.println(topic);
//                Map<String, Float> mp = new HashMap<>();
//                Map<String, Float> mvalues = null;
//                if (hasValue[lan]) {
//                    mvalues = new HashMap<>();
//                }
//                for (int w = 0; w < vocabularySizes[lan]; w++) {
//                    float count = languageTypeTopicCounts[lan][w][topic];
//                    if (count == 0) continue;
//                    float dist = (count + betas[lan])/
//                            (languageTokensPerTopic[lan][topic] + betas[lan]*vocabularySizes[lan]);
//                    String word = alphabets[lan].lookupObject(w).toString();
//                    mp.put(word, dist);
//                    if (hasValue[lan]) {
//                        float value = languageTypeTopicSums[lan][w][topic] / count;
//                        mvalues.put(word, value);
//                    }
//                    if (mp.size()>limit){
//                        float min = 10.1;
//                        String mins = null;
//                        for (Map.Entry<String,Float> entry:mp.entrySet() ){
//                            if (entry.getValue()<=min){
//                                min=entry.getValue();
//                                mins=entry.getKey();
//                            }
//                        }
//                        mp.remove(mins);
//                        if (hasValue[lan]) {
//                            mvalues.remove(mins);
//                        }
//                    }
//                }
//                tw.addTopicWordDis(mp);
//                tw.addTopicValues(mvalues);
////				System.out.println();
//            }
//            tws.add(tw);
//        }
//        return tws;
//    }

    public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {

        TreeSet[][] languageTopicSortedWords = new TreeSet[numLanguages][numTopics];
        for (int language = 0; language < numLanguages; language++) {
            TreeSet[] topicSortedWords = languageTopicSortedWords[language];
            for (int topic = 0; topic < numTopics; topic++) {
                topicSortedWords[topic] = new TreeSet<IDSorter>();
                for (int type = 0; type < vocabularySizes[language]; type++) {
                    float topicTotal = languageTokensPerTopic[language].get(topic);
                    int count = languageTypeTopicCounts[language][type].get(topic);
                    topicSortedWords[topic].add(new IDSorter(type, count / topicTotal));
//                    out.println("----" + type + "----");
                }
            }
        }

//        out.println("----" + languageTopicSortedWords[0][2].size() + "----");
        for (int topic = 0; topic < numTopics; topic++) {
            out.println (topicAlphabet.lookupObject(topic) + "\talpha:" + formatter.format(alphas[topic]));
            for (int language = 0; language < numLanguages; language++) {
                out.print("\tlanguage:" + language + "\ttokens:" + languageTokensPerTopic[language].get(topic) + "\tbeta:" + String.format("%.6f", betas[language]) + "\t");
                TreeSet<IDSorter> sortedWords = languageTopicSortedWords[language][topic];
                Alphabet alphabet = alphabets[language];
                int word = 0;
                Iterator<IDSorter> iterator = sortedWords.iterator();
                while (iterator.hasNext() && word < numWords) {
                    IDSorter info = iterator.next();
                    out.print("[" + alphabet.lookupObject(info.getID()) + ":" + String.format("%.4f", info.getWeight()) + "] ");
                    word++;
                }
                out.println();
            }
        }
    }

    public float modelLogLikelihood() {
        float logLikelihood = 0.0f;
        int nonZeroTopics;

        // The likelihood of the model is a combination of a
        // Dirichlet-multinomial for the words in each topic
        // and a Dirichlet-multinomial for the topics in each
        // document.

        // The likelihood function of a dirichlet multinomial is
        //	 Gamma( sum_i alpha_i )	 prod_i Gamma( alpha_i + N_i )
        //	prod_i Gamma( alpha_i )	  Gamma( sum_i (alpha_i + N_i) )

        // So the log likelihood is
        //	logGamma ( sum_i alpha_i ) - logGamma ( sum_i (alpha_i + N_i) ) +
        //	 sum_i [ logGamma( alpha_i + N_i) - logGamma( alpha_i ) ]

        // Do the documents first

        int[] topicCounts = new int[numTopics];
        float[] topicLogGammas = new float[numTopics];
        int[] docTopics;
        int[] docTypes;
        float[] docValues;

        for (int topic=0; topic < numTopics; topic++) {
            topicLogGammas[ topic ] = (float) Dirichlet.logGammaStirling( alphas[topic] );
        }

        for (int doc = 0; doc < trainingData.size(); doc++) {

            int totalLength = 0;

            for (int language = 0; language < numLanguages; language++) {

                LabelSequence topicSequence = (LabelSequence) trainingData.get(doc).topicSequences[language];
                int[] currentDocTopics = topicSequence.getFeatures();

                totalLength += topicSequence.getLength();

                // Count up the tokens
                for (int token=0; token < topicSequence.getLength(); token++) {
                    topicCounts[ currentDocTopics[token] ]++;
                }
            }

            for (int topic=0; topic < numTopics; topic++) {
                if (topicCounts[topic] > 0) {
                    logLikelihood += (Dirichlet.logGammaStirling(alphas[topic] + topicCounts[topic]) -
                            topicLogGammas[ topic ]);
                }
            }

            // subtract the (count + parameter) sum term
            logLikelihood -= Dirichlet.logGammaStirling(alphaSum + totalLength);

            Arrays.fill(topicCounts, 0);
        }

        // add the parameter sum term
        logLikelihood += trainingData.size() * Dirichlet.logGammaStirling(alphaSum);

        // And the topics

        for (int language = 0; language < numLanguages; language++) {
            AtomicIntegerArray[] typeTopicCounts = languageTypeTopicCounts[language];
            AtomicIntegerArray tokensPerTopic = languageTokensPerTopic[language];
            float beta = betas[language];
            float logGammaBeta = (float) Dirichlet.logGamma(beta);

            float[][] typeTopicSquareSum = new float[vocabularySizes[language]][numTopics];

            if (hasValue[language]) {
                for (int doc = 0; doc < trainingData.size(); doc++) {
                    docTopics = trainingData.get(doc).topicSequences[language].getFeatures();
                    docTypes = ((FeatureSequence) trainingData.get(doc).instances[language].getData()).getFeatures();
                    docValues = realFeatures[language].get(doc);
                    for (int token = 0; token < ((FeatureSequence) trainingData.get(doc).instances[language].getData()).size(); token++) {
                        int topic = docTopics[token];
                        int type = docTypes[token];
                        float value = docValues[token];
                        typeTopicSquareSum[type][topic] += value * value;
                    }
                }
            }

            for (int type=0; type < vocabularySizes[language]; type++) {
                // reuse this array as a pointer

//                topicCounts = typeTopicCounts[type];

                for (int topic = 0; topic < numTopics; topic ++) {
                    int count = typeTopicCounts[type].get(topic);
                    logLikelihood += Dirichlet.logGammaStirling(beta + count) - logGammaBeta;
                    if (Float.isNaN(logLikelihood)) {
                        System.out.println(count);
                        System.exit(1);
                    }
                }
            }

            for (int topic=0; topic < numTopics; topic++) {
                logLikelihood -=
                        Dirichlet.logGammaStirling( (beta * numTopics) +
                                tokensPerTopic.get(topic) );
                if (Float.isNaN(logLikelihood)) {
                    System.out.println("after topic " + topic + " " + tokensPerTopic.get(topic));
                    System.exit(1);
                }

            }

            logLikelihood +=
                    (Dirichlet.logGammaStirling(beta * numTopics));

            if (hasValue[language]) {
                for (int topic = 0; topic < numTopics; topic++) {
                    for (int type = 0; type < vocabularySizes[language]; type++) {
                        if (languageSigma2s[language][type] != 0) {
                            int tmpn = typeTopicCounts[type].get(topic);
                            float tmpSum = languageTypeTopicSums[language][type][topic];
                            float logll = tmpSum + languageMus[language][type];
                            logll *= logll / (tmpn + 1);
                            logll -= typeTopicSquareSum[type][topic] + languageMus[language][type] * languageMus[language][type];
                            logll /= 2 * languageSigma2s[language][type];
                            logll += Math.log(Math.sqrt(typeTopicCounts[type].get(topic) + 1)) - typeTopicCounts[type].get(topic) * Math.log(languageSigma2s[language][type]) / 2;

                            logLikelihood += logll;
                        }
                    }
                }
            }
        }

        if (Float.isNaN(logLikelihood)) {
            System.out.println("at the end");
            System.exit(1);
        }


        return logLikelihood;
    }

    public DataFormat.MVMATopicModel storeModel() {
        DataFormat.MVMATopicModel.Builder modelBuilder = DataFormat.MVMATopicModel.newBuilder()
                .setNumTopics(numTopics)
                .setAlphaSum(alphaSum)
                .setBetaSum(betaSum)
                .setNumLanguages(numLanguages);

        for (int i = 0; i < numLanguages; i++) {
            modelBuilder.addHasValue(hasValue[i]);
            for (int j = 0; j < vocabularySizes[i]; j++)
                for (int k = 0; k < numTopics; k++)
                    modelBuilder.addLanguageTypeTopicCounts(languageTypeTopicCounts[i][j].get(k));

            if (hasValue[i]) {
                for (int j = 0; j < vocabularySizes[i]; j++) {
                    for (int k = 0; k < numTopics; k++)
                        modelBuilder.addLanguageTypeTopicSums(languageTypeTopicSums[i][j][k]);
                    modelBuilder.addLanguageMus(languageMus[i][j]);
                    modelBuilder.addLanguageSigma2S(languageSigma2s[i][j]);
                }
            }

            for (int j = 0; j < numTopics; j++)
                modelBuilder.addLanguageTokensPerTopic(languageTokensPerTopic[i].get(j));

            DataFormat.Alphabet.Builder alphabetBuilder = DataFormat.Alphabet.newBuilder();
            for (Object entry : alphabets[i].toArray())
                alphabetBuilder.addEntry((String) entry);
            modelBuilder.addAlphabets(alphabetBuilder);
        }

        return modelBuilder.build();
    }

}
