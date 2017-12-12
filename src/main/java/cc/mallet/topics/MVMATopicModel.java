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
    private int[][] languageTokensPerTopic;
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
    private transient float[] topicTermScores;
    private transient int[] localTopicCounts;
    private transient float[] localSigma2s;
    private transient float[] localPowers;

    private transient LabelAlphabet topicAlphabet;

    private transient ArrayList<TopicAssignment> trainingData;
    private transient ArrayList<float[]>[] realFeatures;

    private transient int iterationsSoFar = 1;
    private transient int numIterations;
    private transient int maxIteration;

    private transient int showTopicsInterval;
    private transient int wordsPerTopic;

    private transient long randomSeed;
    private transient Randoms random;

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

    public MVMATopicModel(int numTopics, float alphaSum, float betaSum, long randomSeed, long taskId) {
        this.numTopics = numTopics;
        this.alphaSum = alphaSum;
        this.betaSum = betaSum;
        this.randomSeed = randomSeed;
        this.taskId = taskId;
        
        this.trainingData = new ArrayList<>();
        this.topicAlphabet = new LabelAlphabet();
        for (int i = 0; i < numTopics; i++)
            this.topicAlphabet.lookupIndex("topic"+i);
        
        this.alphas = new float[numTopics];
        Arrays.fill(alphas, alphaSum / numTopics);
        
        this.random = new Randoms((int) randomSeed);
        
        formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(5);

        this.topicTermScores = new float[numTopics];
        this.localTopicCounts = new int[numTopics];
        this.localSigma2s = new float[numTopics];
        this.localPowers = new float[numTopics];
    }

    public void addTrainingInstances(InstanceList[] training, ArrayList<float[]>[] valueList) {
        realFeatures = valueList;
        numLanguages = training.length;

        languageTokensPerTopic = new int[numLanguages][numTopics];

        alphabets = new Alphabet[numLanguages];
        vocabularySizes = new int[numLanguages];
        betas = new float[numLanguages];
        betaSums = new float[numLanguages];

        languageTypeTopicCounts = new int[numLanguages][][];
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
            languageTypeTopicCounts[language] = new int[vocabularySizes[language]][numTopics];

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

                int[] tokensPerTopic = languageTokensPerTopic[language];

                instances[language] = training[language].get(doc);
                FeatureSequence tokens = (FeatureSequence) instances[language].getData();
                topicSequences[language] =
                        new LabelSequence(topicAlphabet, new int[ tokens.size() ]);

                int[] topics = topicSequences[language].getFeatures();

                for (int position = 0; position < tokens.size(); position++) {

                    int type = tokens.getIndexAtPosition(position);

                    int topic = random.nextInt(numTopics);

                    topics[position] = topic;
                    tokensPerTopic[topic]++;

                    languageTypeTopicCounts[language][type][topic] ++;
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

    public void training() {
        training(numIterations);
    }

    public void training(int iterationsThisRound) {
        if (trainingData.size() == 0)
            throw new IllegalStateException("未添加训练实例，无法开始训练!");

        maxIteration = iterationsSoFar + iterationsThisRound - 1;
        totalTime = 0;

        //ToDo: 添加训练参数基本信息显示
        log.info("Training task id:{} iteration starts!", taskId);

        for ( ; iterationsSoFar <= maxIteration; iterationsSoFar++) {
            long iterationStart = System.currentTimeMillis();

            for (int doc = 0; doc < trainingData.size(); doc++)
                sampleTopicsForOneDoc (trainingData.get(doc), doc);

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

    private void sampleTopicsForOneDoc (TopicAssignment topicAssignment, int docIndex) {
        int[] currentTypeTopicCounts;
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

            for (int position = 0; position < docLength; position++) {
                type = tokenSequence.getIndexAtPosition(position);
                oldTopic = oneDocTopics[position];
                currentTypeTopicCounts = typeTopicCounts[type];

                localTopicCounts[oldTopic] --;
                tokensPerTopic[oldTopic] --;
                currentTypeTopicCounts[oldTopic] --;

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

                    sum += score;
                    topicTermScores[topic] = score;
                }

                float sample =  (float) random.nextUniform() * sum;

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
                tokensPerTopic[newTopic]++;
                currentTypeTopicCounts[newTopic]++;
                if (hasValue[language]) {
                    typeTopicSums[type][newTopic] += value;
                }
            }
        }
    }

    public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {

        TreeSet[][] languageTopicSortedWords = new TreeSet[numLanguages][numTopics];
        for (int language = 0; language < numLanguages; language++) {
            TreeSet[] topicSortedWords = languageTopicSortedWords[language];
            for (int topic = 0; topic < numTopics; topic++) {
                topicSortedWords[topic] = new TreeSet<IDSorter>();
                for (int type = 0; type < vocabularySizes[language]; type++) {
                    float topicTotal = languageTokensPerTopic[language][topic];
                    int count = languageTypeTopicCounts[language][type][topic];
                    topicSortedWords[topic].add(new IDSorter(type, count / topicTotal));
//                    out.println("----" + type + "----");
                }
            }
        }

//        out.println("----" + languageTopicSortedWords[0][2].size() + "----");
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
            int[][] typeTopicCounts = languageTypeTopicCounts[language];
            int[] tokensPerTopic = languageTokensPerTopic[language];
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

                topicCounts = typeTopicCounts[type];

                for (int topic = 0; topic < numTopics; topic ++) {
                    int count = topicCounts[topic];
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
                                tokensPerTopic[ topic ] );
                if (Float.isNaN(logLikelihood)) {
                    System.out.println("after topic " + topic + " " + tokensPerTopic[ topic ]);
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

}
