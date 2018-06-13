package cc.mallet.topics;

import cc.mallet.types.*;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

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

    private int[][][] languageTypeTopicCounts;
    private int[][] languageTokensPerTopic; //一种语言中每个主题下的词语计数
    private float[][] languageMus;
    private float[][] languageSigma2s;
    private float[][][] languageTypeTopicSums;
    private boolean[] hasValue;
    private Alphabet[] alphabets;

    //for inference
    private float[][] instanceTopicDists;
    private InstanceList instanceList;

    private transient int[] vocabularySizes;

    private transient float[] alphas;

    private transient float[] betas;
    private transient float[] betaSums;


    //需要设置为ThreadLocal

    private transient int numThreads;
    private transient ExecutorService executorService;

    private SampleRunner[] sampleRunners;
    private Future<Void>[] futures;


    private transient int[][][][] languageTypeTopicCountsCache;
    private transient int[][][] languageTokensPerTopicCache;
    private transient int[] zeroIntegers;
    private transient float[] zeroFloats;

    private transient float[][] topicTermScores;
    private transient int[][] localTopicCounts;
    private transient float[][] localSigma2s;
    private transient float[][] localPowers;

    private transient int[] topicCounts;
    private transient float[] topicLogGammas;
//    private transient float[][] typeTopicSquareSum;

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

    public Alphabet getAlphabet(int language) {
        log.info("{}",language);
        return alphabets[language];
    }

    public boolean hasValue(int language) {
        return hasValue[language];
    }

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

        if (numThreads > 1)
            log.warn("Model can't get stable result when use parallel accelerating.");
        
        formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(5);

        this.executorService = Executors.newFixedThreadPool(numThreads);
        this.futures = new Future[numThreads];
        this.sampleRunners = new SampleRunner[numThreads];

        for (int thread = 0; thread < numThreads; thread++)
            this.sampleRunners[thread] = new SampleRunner(thread);

        this.topicTermScores = new float[numThreads][numTopics];
        this.localTopicCounts = new int[numThreads][numTopics];
        this.localSigma2s = new float[numThreads][numTopics];
        this.localPowers = new float[numThreads][numTopics];

        this.languageTypeTopicCountsCache = new int[numThreads][][][];
        this.languageTokensPerTopicCache = new int[numThreads][][];
        this.zeroIntegers = new int[numTopics];
        this.zeroFloats = new float[numTopics];

        this.topicCounts = new int[numTopics];
        this.topicLogGammas = new float[numTopics];
    }

    public MVMATopicModel(DataFormat.MVMATopicModel model, long randomSeed, long taskId) {
        this(model.getNumTopics(), model.getAlphaSum(), model.getBetaSum(), randomSeed, 8, taskId);
        loadModel(model);
    }

    public void addTrainingInstances(InstanceList[] training, ArrayList<float[]>[] valueLists) {
        realFeatures = valueLists;
        numLanguages = training.length;

        languageTokensPerTopic = new int[numLanguages][numTopics];
        for (int thread = 0; thread < numThreads; thread++)
            languageTokensPerTopicCache[thread] = new int[numLanguages][numTopics];

        alphabets = new Alphabet[numLanguages];
        vocabularySizes = new int[numLanguages];
        betas = new float[numLanguages];
        betaSums = new float[numLanguages];

        languageTypeTopicCounts = new int[numLanguages][][];
        for (int thread = 0; thread < numThreads; thread++)
            languageTypeTopicCountsCache[thread] = new int[numLanguages][][];


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

//            typeTopicSquareSum = new float[vocabularySizes[language]][numTopics];

            int[] typeTotals = new int[vocabularySizes[language]];

            languageTypeTopicCounts[language] = new int[vocabularySizes[language]][numTopics];
            for (int thread = 0; thread < numThreads; thread++)
                languageTypeTopicCountsCache[thread][language] = new int[vocabularySizes[language]][numTopics];


            if (valueLists[language] != null) {
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

                int[] tokensPerTopic = languageTokensPerTopic[language];

                instances[language] = training[language].get(doc);
                FeatureSequence tokens = (FeatureSequence) instances[language].getData();
                topicSequences[language] =
                        new LabelSequence(topicAlphabet, new int[ tokens.size() ]);

                int[] topics = topicSequences[language].getFeatures();

                for (int position = 0; position < tokens.size(); position++) {

                    int type = tokens.getIndexAtPosition(position);

                    int topic = ThreadLocalRandom.current().nextInt(numTopics);

                    topics[position] = topic;
                    tokensPerTopic[topic]++;

                    languageTypeTopicCounts[language][type][topic]++;
                    if (hasValue[language]) {
                        float value = valueLists[language].get(doc)[position];

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
                futures[threadId] = (Future<Void>) executorService.submit(sampleRunners[threadId]);

            for (int threadId = 0; threadId < numThreads; threadId++)
                futures[threadId].get();

            for (int threadId = 0; threadId < numThreads; threadId++) {
                for (int language = 0; language < numLanguages; language++) {
                    for (int topic = 0; topic < numTopics; topic++)
                        languageTokensPerTopic[language][topic] += languageTokensPerTopicCache[threadId][language][topic];
                    for (int type = 0; type < languageTypeTopicCountsCache[threadId][language].length; type++)
                        for (int topic = 0; topic < numTopics; topic++)
                            languageTypeTopicCounts[language][type][topic] += languageTypeTopicCountsCache[threadId][language][type][topic];
                }
            }

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
                printTopWords(printStream, wordsPerTopic);
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
            for (int language = 0; language < numLanguages; language++) {
                System.arraycopy(languageTokensPerTopic[language], 0, languageTokensPerTopicCache[threadId][language], 0, numTopics);
                for (int type = 0; type < languageTypeTopicCountsCache[threadId][language].length; type++)
                    System.arraycopy(languageTypeTopicCounts[language][type], 0, languageTypeTopicCountsCache[threadId][language][type], 0, numTopics);
            }

            for (int doc = threadId; doc < trainingData.size(); doc += numThreads)
                sampleTopicsForOneDoc (trainingData.get(doc), doc, threadId);

            for (int language = 0; language < numLanguages; language++) {
                for (int topic = 0; topic < numTopics; topic++)
                    languageTokensPerTopicCache[threadId][language][topic] -= languageTokensPerTopic[language][topic];
                for (int type = 0; type < languageTypeTopicCountsCache[threadId][language].length; type++)
                    for (int topic = 0; topic < numTopics; topic++)
                        languageTypeTopicCountsCache[threadId][language][type][topic] -= languageTypeTopicCounts[language][type][topic];
            }
        }
    }

    private void sampleTopicsForOneDoc (TopicAssignment topicAssignment, int docIndex, int threadId) {
        float[] topicTermScores = this.topicTermScores[threadId];
        int[] localTopicCounts = this.localTopicCounts[threadId];
        float[] localSigma2s = this.localSigma2s[threadId];
        float[] localPowers = this.localPowers[threadId];

        int[][][] languageTypeTopicCounts = languageTypeTopicCountsCache[threadId];
        int[][] languageTokensPerTopic = languageTokensPerTopicCache[threadId];

        int type, oldTopic, newTopic;
        float value = 0;

//        Arrays.fill(localTopicCounts, 0);
        System.arraycopy(zeroIntegers, 0, localTopicCounts, 0, numTopics);

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

            int[][] typeTopicCounts = languageTypeTopicCounts[language];
            int[] tokensPerTopic = languageTokensPerTopic[language];
            float beta = betas[language];
            float betaSum = betaSums[language];

            float score, sum;
            float[][] typeTopicSums = null;
            float[] mus = null;

            if (hasValue[language]) {
                typeTopicSums = languageTypeTopicSums[language];
                mus = languageMus[language];
            }

            int[] currentTypeTopicCounts;

            for (int position = 0; position < docLength; position++) {
                type = tokenSequence.getIndexAtPosition(position);
                oldTopic = oneDocTopics[position];
                currentTypeTopicCounts = typeTopicCounts[type];

                localTopicCounts[oldTopic] --;


                tokensPerTopic[oldTopic]--;
                currentTypeTopicCounts[oldTopic]--;


                float regu = 0;
                if (hasValue[language]) {
                    value = realFeatures[language].get(docIndex)[position];

                    //lock
                    typeTopicSums[type][oldTopic] -= value;

                    if (languageSigma2s[language][type] != 0) {
                        float logMax = Float.NEGATIVE_INFINITY;
                        for (int topic = 0; topic < numTopics; topic++) {
                            float tmpMu = (mus[type] + typeTopicSums[type][topic]) / (1 + currentTypeTopicCounts[topic]);
                            localSigma2s[topic] = languageSigma2s[language][type] / (1 + currentTypeTopicCounts[topic]);
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
                                    ((beta + currentTypeTopicCounts[topic]) /
                                            (betaSum + tokensPerTopic[topic]));

                    if (hasValue[language] && languageSigma2s[language][type] != 0) {
                        float normalFactor = 1 / (float) Math.sqrt(localSigma2s[topic]);
                        normalFactor *= Math.exp(localPowers[topic] + regu);
                        score *= normalFactor;
                    }
                    if (score < 0) {
                        log.info("score:{}, {}, {}, {} ", score, localTopicCounts[topic], currentTypeTopicCounts[topic], tokensPerTopic[topic]);
                    }
                    sum += score;
                    topicTermScores[topic] = score;
                }

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
                    log.error("sample:{}, sum:{}", sample, sum);
                    throw new IllegalStateException ("SimpleLDA: New topic not sampled.");
                }

                // Put that new topic into the counts
                oneDocTopics[position] = newTopic;
                localTopicCounts[newTopic]++;

                tokensPerTopic[newTopic]++;
                currentTypeTopicCounts[newTopic]++;

                if (hasValue[language]) {
                    typeTopicSums[type][newTopic] += value;
                }
            }
        }
    }

    public void printTopWords (PrintStream out, int numWords) {

        TreeSet[][] languageTopicSortedWords = new TreeSet[numLanguages][numTopics];
        for (int language = 0; language < numLanguages; language++) {
            TreeSet[] topicSortedWords = languageTopicSortedWords[language];
            for (int topic = 0; topic < numTopics; topic++) {
                topicSortedWords[topic] = new TreeSet<IDSorter>();
                for (int type = 0; type < vocabularySizes[language]; type++) {
                    float topicTotal = languageTokensPerTopic[language][topic];
                    int count = languageTypeTopicCounts[language][type][topic];
                    topicSortedWords[topic].add(new IDSorter(type, count / topicTotal));
                }
            }
        }


        for (int topic = 0; topic < numTopics; topic++) {
            out.println (topicAlphabet.lookupObject(topic) + "\talpha:" + formatter.format(alphas[topic]));
            for (int language = 0; language < numLanguages; language++) {
                out.print("\tlanguage:" + language + "\ttokens:" + languageTokensPerTopic[language][topic] + "\tbeta:" + String.format("%.6f", betas[language]) + "\t");
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

//        int[] topicCounts = new int[numTopics];
//        float[] topicLogGammas = new float[numTopics];
        System.arraycopy(zeroIntegers, 0, topicCounts, 0, numTopics);
        System.arraycopy(zeroFloats, 0, topicLogGammas, 0, numTopics);
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
            int[][] typeTopicCounts = languageTypeTopicCounts[language];
            int[] tokensPerTopic = languageTokensPerTopic[language];
            float beta = betas[language];
            float logGammaBeta = (float) Dirichlet.logGamma(beta);


            float[][] typeTopicSquareSum = new float[vocabularySizes[language]][numTopics];
//            for (int type = 0; type < vocabularySizes[language]; type++)
//                System.arraycopy(zeroFloats, 0, typeTopicSquareSum[type], 0, numTopics);


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
                    int count = typeTopicCounts[type][topic];
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
                                tokensPerTopic[topic] );
                if (Float.isNaN(logLikelihood)) {
                    System.out.println("after topic " + topic + " " + tokensPerTopic[topic]);
                    System.exit(1);
                }

            }

            logLikelihood +=
                    (Dirichlet.logGammaStirling(beta * numTopics));

            if (hasValue[language]) {
                for (int topic = 0; topic < numTopics; topic++) {
                    for (int type = 0; type < vocabularySizes[language]; type++) {
                        if (languageSigma2s[language][type] != 0) {
                            int tmpn = typeTopicCounts[type][topic];
                            float tmpSum = languageTypeTopicSums[language][type][topic];
                            float logll = tmpSum + languageMus[language][type];
                            logll *= logll / (tmpn + 1);
                            logll -= typeTopicSquareSum[type][topic] + languageMus[language][type] * languageMus[language][type];
                            logll /= 2 * languageSigma2s[language][type];
                            logll += Math.log(Math.sqrt(typeTopicCounts[type][topic] + 1)) - typeTopicCounts[type][topic] * Math.log(languageSigma2s[language][type]) / 2;

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

    private class InferenceRunner implements Runnable {

        private int threadId;

        private int instanceId;

        private int packSize;

        private InstanceList instances;

        private int language;

        private int burnIn;

        private int numIterations;

        private int thinning;

        public InferenceRunner(int threadId, int instanceId, int packSize, InstanceList instances, int language, int burnIn, int numIterations, int thinning) {
            this.threadId = threadId;
            this.instanceId = instanceId;
            this.packSize = packSize;
            this.instances = instances;
            this.language = language;
            this.burnIn = burnIn;
            this.numIterations = numIterations;
            this.thinning = thinning;
        }

        @Override
        public void run() {
            for (int i = instanceId; i - instanceId < packSize; i++) {
                instanceTopicDists[i] = inference(instances.get(i), language, numIterations, burnIn, thinning, threadId);
            }
        }
    }

    public void inference(InstanceList instanceList, int language, int numIterations, int burnIn, int thinning) throws ExecutionException, InterruptedException {
        this.instanceList = instanceList;
        instanceTopicDists = new float[instanceList.size()][];

        wordsPerTopic = 100;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        printStream.println("Training task id:" + taskId + "\titeration:"+ iterationsSoFar +"/"+ maxIteration +"\tTop words:");
        printTopWords(printStream, wordsPerTopic);
        log.info(byteArrayOutputStream.toString());

        int step = instanceList.size() / 1000;
        int numInstances = instanceList.size();
        step = step == 0 ? 1 : step;

        List<Future> futures = new ArrayList<>();

        int packSize = (numInstances / numThreads) + 1;

        for (int i = 0; i < numThreads; i++) {
            if (i < numThreads - 1) {
                futures.add(executorService.submit(new InferenceRunner(i, i * packSize, packSize, instanceList, language, burnIn, numIterations, thinning)));
            } else {
                futures.add(executorService.submit(new InferenceRunner(i, i * packSize, numInstances - i * packSize, instanceList, language, burnIn, numIterations, thinning)));
            }

//            if ((i + 1) % step == 0) {
//                float progress = (float) ((float) (i + 1) * 100.0 / numInstances);
//                log.info("Inference task id:{}\tinstance:{}/{}\tprogress:{}%", taskId, i + 1, numInstances,
//                        String.format("%.1f", progress));
//            }
        }

        for (Future future : futures)
            future.get();

        log.info("Training task id:{} inference finished!", taskId);
    }

    public float[] inference(Instance instances, int language, int numIterations, int burnIn, int thinning, int threadId){
        String instancesName = (String) instances.getName();

        int[] localTopicCounts = this.localTopicCounts[threadId];
        Arrays.fill(localTopicCounts, 0);

        int[] currentTypeTopicCounts;

        FeatureSequence tokens = (FeatureSequence) instances.getData();
        int[] topics = new int[tokens.size()];
        int[][] typeTopicCounts = languageTypeTopicCounts[language]; //词语在主题中的个数
        int[] tokensPerTopic = languageTokensPerTopic[language]; //主题下词语个数
        int numTokens = tokens.size();

        double beta =  betas[language];
        double alpha = alphas[language];

        //随机初始化
        for (int position = 0; position < tokens.size(); position++) {
            Object token= tokens.get(position);
            if(!alphabets[language].contains(token)){
                numTokens--;
                continue;
            }
            int topic = ThreadLocalRandom.current().nextInt(numTopics);
            topics[position] = topic;
            localTopicCounts[topic]++;
        }


        //迭代
        double[] pSum = new double[numTopics];
        int numSamples=0;
        for(int i=0;i<numIterations;i++) {
            double[] scores = new double[numTopics];
            double score = 0;
            double sum = 0;
            int newTopic;
            int type=0;

            for(int position = 0; position < tokens.size(); position++){
                sum = 0;
                localTopicCounts[topics[position]]--;

                Object token= tokens.get(position);
                if(!alphabets[language].contains(token))continue;
                else {
                    type = alphabets[language].lookupIndex(token);
                }
                currentTypeTopicCounts = typeTopicCounts[type];
//                if (interesting) {
//                    log.info("token:{}\ttypeTopicCounts:{}", token, currentTypeTopicCounts);
//                }
                for (int topic = 0; topic < numTopics; topic++) {
                    score =
                            (alpha + localTopicCounts[topic]) *
                                    ((beta + currentTypeTopicCounts[topic]) /
                                            (betaSum + tokensPerTopic[topic]));

                    sum += score;
                    scores[topic] = score;
                }
                double sample = ThreadLocalRandom.current().nextDouble() * sum;

                // Figure out which topic contains that point
                newTopic = -1;
                while (sample > 0.0) {
                    newTopic++;
                    if (newTopic == numTopics - 1)
                        break;
                    sample -= scores[newTopic];
                }
                if (newTopic == -1) {
                    throw new IllegalStateException ("SimpleLDA: New topic not sampled.");
                }

                // Put that new topic into the counts
                topics[position] = newTopic;
                localTopicCounts[newTopic]++;
            }

            //sample
            if(i >= burnIn && (i - burnIn) % thinning == 0) {
                for(int k = 0; k < numTopics; k++){
                    pSum[k] += (double) localTopicCounts[k] / (double) numTokens;
                }
                numSamples++;
            }

        }

        float[] result = new float[numTopics];

        for(int i = 0; i < numTopics; i++)
            result[i] = (float) pSum[i] / numSamples;

        return result;
    }



    public int match(Instance instance0, Instance instance1){
        String instancesName1 = (String) instance1.getName();   //instance 1 药 0病 model中 0是药 1是病
        String instancesName0 = (String) instance0.getName();

        FeatureSequence tokens0 = (FeatureSequence) instance0.getData();
        FeatureSequence tokens1 = (FeatureSequence) instance1.getData();
        //a词分配给主题k的数目 /主题k的总数目 取最大的

        int[][] typeTopicCounts = languageTypeTopicCounts[1];
        int[] tokensPerTopic = languageTokensPerTopic[1];
        int numTokens = tokens1.size();

        double beta =  betas[0];
        double alpha = alphas[0];
//        System.out.println(tokens1);

        int tag[] = new int[numTopics];
        Arrays.fill(tag,0);
        int type = 0;
        for(int position = 0; position < tokens0.size(); position++){
            Object token= tokens0.get(position);
            if(!alphabets[1].contains(token))continue;  //fixme
            else {
                type = alphabets[1].lookupIndex(token);
            }
            int[] currentTypeTopicCounts = typeTopicCounts[type];
            double max = 0.0;
            int index = 0;
            for (int topic = 0; topic < numTopics; topic++) {
                double temp = (beta + currentTypeTopicCounts[topic]) /
                        (betaSum + tokensPerTopic[topic]);
                if (temp > max) {
                    max = temp;
                    index = topic;
                }
            }
            tag[index] = 1;
        }

        int tag2[] = new int[numTopics];
        int falseCount = 0;
        int[][] typeTopicCounts0 = languageTypeTopicCounts[0];
        int[] tokensPerTopic0 = languageTokensPerTopic[0];
        for(int position = 0; position < tokens1.size(); position++){
            Object token= tokens1.get(position);
            if(!alphabets[0].contains(token))continue;  //fixme
            else {
                type = alphabets[0].lookupIndex(token);
            }
            int[] currentTypeTopicCounts0 = typeTopicCounts0[type];
            double max = 0.0;
            int index = 0;
            for (int topic = 0; topic < numTopics; topic++) {
                double temp = (beta + currentTypeTopicCounts0[topic]) /
                        (betaSum + tokensPerTopic0[topic]);
                if (temp > max) {
                    max = temp;
                    index = topic;
                }
            }
            if (tag[index] != 1)
                falseCount += 1;
            tag2[index] = 1;
        }

//        System.out.println(falseCount);
        return falseCount;
    }
    public DataFormat.InferenceResult storeInferenceResult() {
        DataFormat.InferenceResult.Builder resultBuilder = DataFormat.InferenceResult.newBuilder()
                .setNumTopics(numTopics);

        for (int i = 0; i < instanceList.size(); i++) {
            Instance instance = instanceList.get(i);
            DataFormat.InstanceTopicDist.Builder topicDistBuilder = DataFormat.InstanceTopicDist.newBuilder()
                    .setInstanceId((String) instance.getName());

            for (int j = 0; j < numTopics; j++)
                topicDistBuilder.addTopicShare(instanceTopicDists[i][j]);
            resultBuilder.addInstanceTopicDists(topicDistBuilder);
        }

        return resultBuilder.build();
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
                    modelBuilder.addLanguageTypeTopicCounts(languageTypeTopicCounts[i][j][k]);

            if (hasValue[i]) {
                for (int j = 0; j < vocabularySizes[i]; j++) {
                    for (int k = 0; k < numTopics; k++)
                        modelBuilder.addLanguageTypeTopicSums(languageTypeTopicSums[i][j][k]);
                    modelBuilder.addLanguageMus(languageMus[i][j]);
                    modelBuilder.addLanguageSigma2S(languageSigma2s[i][j]);
                }
            }

            for (int j = 0; j < numTopics; j++)
                modelBuilder.addLanguageTokensPerTopic(languageTokensPerTopic[i][j]);

            DataFormat.Alphabet.Builder alphabetBuilder = DataFormat.Alphabet.newBuilder();
            for (Object entry : alphabets[i].toArray())
                alphabetBuilder.addEntry((String) entry);
            modelBuilder.addAlphabets(alphabetBuilder);
        }

        return modelBuilder.build();
    }

    private void loadModel(DataFormat.MVMATopicModel model) {
        this.numLanguages = model.getNumLanguages();

        alphabets = new Alphabet[numLanguages];
        hasValue = new boolean[numLanguages];
        vocabularySizes = new int[numLanguages];

        betas = new float[numLanguages];
        betaSums = new float[numLanguages];
        languageTokensPerTopic = new int[numLanguages][numTopics];

        languageTypeTopicCounts = new int[numLanguages][][];
        languageTypeTopicSums = new float[numLanguages][][];
        languageMus = new float[numLanguages][];
        languageSigma2s = new float[numLanguages][];


        int ttcIdx = 0;
        int tptIdx = 0;
        int ttsIdx = 0;
        int mIdx = 0;
        int sIdx = 0;
        for (int i = 0; i < numLanguages; i++) {
            alphabets[i] = new Alphabet(String.class);
            DataFormat.Alphabet alphabet = model.getAlphabets(i);
            for (int j = 0; j < alphabet.getEntryCount(); j++)
                alphabets[i].lookupIndex(alphabet.getEntry(j));

            hasValue[i] = model.getHasValue(i);
            vocabularySizes[i] = alphabet.getEntryCount();

            betas[i] = betaSum / vocabularySizes[i];
            betaSums[i] = betaSum;

            languageTypeTopicCounts[i] = new int[vocabularySizes[i]][numTopics];
            for (int j = 0; j < vocabularySizes[i]; j++)
                for (int k = 0; k < numTopics; k++)
                    languageTypeTopicCounts[i][j][k] = model.getLanguageTypeTopicCounts(ttcIdx++);

            for (int j = 0; j < numTopics; j++)
                languageTokensPerTopic[i][j] = model.getLanguageTokensPerTopic(tptIdx++);

            if (hasValue[i]) {
                languageTypeTopicSums[i] = new float[vocabularySizes[i]][numTopics];
                languageMus[i] = new float[vocabularySizes[i]];
                languageSigma2s[i] = new float[vocabularySizes[i]];
                for (int j = 0; j < vocabularySizes[i]; j++) {
                    for (int k = 0; k < numTopics; k++)
                        languageTypeTopicSums[i][j][k] = model.getLanguageTypeTopicSums(ttsIdx++);
                    languageMus[i][j] = model.getLanguageMus(mIdx++);
                    languageSigma2s[i][j] = model.getLanguageSigma2S(sIdx++);
                }
            }
        }
    }

}
