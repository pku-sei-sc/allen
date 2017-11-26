package cc.mallet.topics;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import cn.edu.pku.sei.sc.allen.model.TopicWord2;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Lingxiao on 2016/10/22.
 */
public class MVMATopicModel extends PolylingualTopicModel {

    protected int[][][] languageTypeTopicCountsNonsparse;
    //    protected ArrayList<double[][]> realFeatures = new ArrayList<>();
    protected ArrayList<double[]>[] realFeatures;
    double[][] languageMus;
    double[][] languageSigma2s;
    double[][][] languageTypeTopicSums;
    boolean[] hasValue;

    public MVMATopicModel(int numberOfTopics, double alphaSum) {
        super (numberOfTopics, alphaSum, new Randoms());
    }

    public void addInstancesWithValues (InstanceList[] training, ArrayList<double[]>[] valueList) {
        realFeatures = valueList;
        numLanguages = training.length;

        languageTokensPerTopic = new int[numLanguages][numTopics];

        alphabets = new Alphabet[ numLanguages ];
        vocabularySizes = new int[ numLanguages ];
        betas = new double[ numLanguages ];
        betaSums = new double[ numLanguages ];
        languageMaxTypeCounts = new int[ numLanguages ];

        languageTypeTopicCountsNonsparse = new int[ numLanguages ][][];
        languageTypeTopicSums = new double[numLanguages][][];
        languageMus = new double[numLanguages][];
        languageSigma2s = new double[numLanguages][];
        hasValue = new boolean[numLanguages];

        int numInstances = training[0].size();


        ArrayList<Double>[][] typeValues = new ArrayList[numLanguages][];


        for (int language = 0; language < numLanguages; language++) {

            if (training[language].size() != numInstances) {
                System.err.println("Warning: language " + language + " has " +
                        training[language].size() + " instances, lang 0 has " +
                        numInstances);
            }

            alphabets[ language ] = training[ language ].getDataAlphabet();
            vocabularySizes[ language ] = alphabets[ language ].size();

            betas[language] = DEFAULT_BETA;
            betaSums[language] = betas[language] * vocabularySizes[ language ];

            int[] typeTotals = new int[ vocabularySizes[language] ];
            languageTypeTopicCountsNonsparse[language] = new int[ vocabularySizes[language] ][numTopics];

            if (valueList[language] != null) {
                hasValue[language] = true;
                languageTypeTopicSums[language] = new double[vocabularySizes[language]][numTopics];
                languageMus[language] = new double[vocabularySizes[language]];
                languageSigma2s[language] = new double[vocabularySizes[language]];
                typeValues[language] = new ArrayList[vocabularySizes[language]];
                for (int i = 0; i < vocabularySizes[language]; i++) {
                    typeValues[language][i] = new ArrayList<>();
                }
            }

            for (Instance instance : training[language]) {
                if (testingIDs != null &&
                        testingIDs.contains(instance.getName())) {
                    continue;
                }

                FeatureSequence tokens = (FeatureSequence) instance.getData();
                for (int position = 0; position < tokens.getLength(); position++) {
                    int type = tokens.getIndexAtPosition(position);
                    typeTotals[ type ]++;
                }
            }

            for (int type = 0; type < vocabularySizes[language]; type++) {
                if (typeTotals[type] > languageMaxTypeCounts[language]) {
                    languageMaxTypeCounts[language] = typeTotals[type];
                }
            }
        }


        for (int doc = 0; doc < numInstances; doc++) {

            if (testingIDs != null &&
                    testingIDs.contains(training[0].get(doc).getName())) {
                continue;
            }

            Instance[] instances = new Instance[ numLanguages ];
            LabelSequence[] topicSequences = new LabelSequence[ numLanguages ];
//            double[][] values = new double[numInstances][];

            for (int language = 0; language < numLanguages; language++) {

//                int[][] typeTopicCounts = languageTypeTopicCounts[language];
                int[] tokensPerTopic = languageTokensPerTopic[language];

                instances[language] = training[language].get(doc);
                FeatureSequence tokens = (FeatureSequence) instances[language].getData();
                topicSequences[language] =
                        new LabelSequence(topicAlphabet, new int[ tokens.size() ]);

                int[] topics = topicSequences[language].getFeatures();
//                if (hasValue[language]) {
//                    values[language] = new double[tokens.size()];
//                }
                for (int position = 0; position < tokens.size(); position++) {

                    int type = tokens.getIndexAtPosition(position);

                    int topic = random.nextInt(numTopics);

                    topics[position] = topic;
                    tokensPerTopic[topic]++;

                    languageTypeTopicCountsNonsparse[language][type][topic] ++;
                    if (hasValue[language]) {
                        double value = valueList[language].get(doc)[position];
//                        values[language][position] = value;
                        languageTypeTopicSums[language][type][topic] += value;
                        languageMus[language][type] += value;
                        typeValues[language][type].add(value);
                    }
                }
            }

            TopicAssignment t = new TopicAssignment (instances, topicSequences);
            data.add (t);
//            realFeatures.add(values);
        }

        for (int language = 0; language < numLanguages; language++) {
            if (hasValue[language]) {
                for (int type = 0; type < vocabularySizes[language]; type++) {
                    languageMus[language][type] /= typeValues[language][type].size();
                    for (double v : typeValues[language][type]) {
                        languageSigma2s[language][type] += (v - languageMus[language][type]) * (v - languageMus[language][type]);
                    }
                    languageSigma2s[language][type] /= typeValues[language][type].size();
                }
            }
        }

//        initializeHistograms();

//        languageSmoothingOnlyMasses = new double[ numLanguages ];
//        languageCachedCoefficients = new double[ numLanguages ][ numTopics ];

//        cacheValues();
    }

    @Override
    public void addInstances (InstanceList[] training) {

        numLanguages = training.length;

        languageTokensPerTopic = new int[numLanguages][numTopics];

        alphabets = new Alphabet[ numLanguages ];
        vocabularySizes = new int[ numLanguages ];
        betas = new double[ numLanguages ];
        betaSums = new double[ numLanguages ];
        languageMaxTypeCounts = new int[ numLanguages ];
//        languageTypeTopicCounts = new int[ numLanguages ][][];

        languageTypeTopicCountsNonsparse = new int[ numLanguages ][][];

        int numInstances = training[0].size();

        for (int language = 0; language < numLanguages; language++) {

            if (training[language].size() != numInstances) {
                System.err.println("Warning: language " + language + " has " +
                        training[language].size() + " instances, lang 0 has " +
                        numInstances);
            }

            alphabets[ language ] = training[ language ].getDataAlphabet();
            vocabularySizes[ language ] = alphabets[ language ].size();

            betas[language] = DEFAULT_BETA;
            betaSums[language] = betas[language] * vocabularySizes[ language ];

//            languageTypeTopicCounts[language] = new int[ vocabularySizes[language] ][];

//            int[][] typeTopicCounts = languageTypeTopicCounts[language];

            // Get the total number of occurrences of each word type
            int[] typeTotals = new int[ vocabularySizes[language] ];
            languageTypeTopicCountsNonsparse[language] = new int[ vocabularySizes[language] ][numTopics];
            for (Instance instance : training[language]) {
                if (testingIDs != null &&
                        testingIDs.contains(instance.getName())) {
                    continue;
                }

                FeatureSequence tokens = (FeatureSequence) instance.getData();
                for (int position = 0; position < tokens.getLength(); position++) {
                    int type = tokens.getIndexAtPosition(position);
                    typeTotals[ type ]++;
                }
            }

			/* Automatic stoplist creation, currently disabled
			TreeSet<IDSorter> sortedWords = new TreeSet<IDSorter>();
			for (int type = 0; type < vocabularySizes[language]; type++) {
				sortedWords.add(new IDSorter(type, typeTotals[type]));
			}

			stoplists[language] = new HashSet<Integer>();
			Iterator<IDSorter> typeIterator = sortedWords.iterator();
			int totalStopwords = 0;

			while (typeIterator.hasNext() && totalStopwords < numStopwords) {
				stoplists[language].add(typeIterator.next().getID());
			}
			*/

            // Allocate enough space so that we never have to worry about
            //  overflows: either the number of topics or the number of times
            //  the type occurs.
            for (int type = 0; type < vocabularySizes[language]; type++) {
                if (typeTotals[type] > languageMaxTypeCounts[language]) {
                    languageMaxTypeCounts[language] = typeTotals[type];
                }
//                typeTopicCounts[type] = new int[ Math.min(numTopics, typeTotals[type]) ];
            }
        }

        for (int doc = 0; doc < numInstances; doc++) {

            if (testingIDs != null &&
                    testingIDs.contains(training[0].get(doc).getName())) {
                continue;
            }

            Instance[] instances = new Instance[ numLanguages ];
            LabelSequence[] topicSequences = new LabelSequence[ numLanguages ];

            for (int language = 0; language < numLanguages; language++) {

//                int[][] typeTopicCounts = languageTypeTopicCounts[language];
                int[] tokensPerTopic = languageTokensPerTopic[language];

                instances[language] = training[language].get(doc);
                FeatureSequence tokens = (FeatureSequence) instances[language].getData();
                topicSequences[language] =
                        new LabelSequence(topicAlphabet, new int[ tokens.size() ]);

                int[] topics = topicSequences[language].getFeatures();
                for (int position = 0; position < tokens.size(); position++) {

                    int type = tokens.getIndexAtPosition(position);
//                    int[] currentTypeTopicCounts = typeTopicCounts[ type ];

                    int topic = random.nextInt(numTopics);

                    // If the word is one of the [numStopwords] most
                    //  frequent words, put it in a non-sampled topic.
                    //if (stoplists[language].contains(type)) {
                    //	topic = -1;
                    //}

                    topics[position] = topic;
                    tokensPerTopic[topic]++;

                    languageTypeTopicCountsNonsparse[language][type][topic] ++;

                    // The format for these arrays is
                    //  the topic in the rightmost bits
                    //  the count in the remaining (left) bits.
                    // Since the count is in the high bits, sorting (desc)
                    //  by the numeric value of the int guarantees that
                    //  higher counts will be before the lower counts.

                    // Start by assuming that the array is either empty
                    //  or is in sorted (descending) order.

                    // Here we are only adding counts, so if we find
                    //  an existing location with the topic, we only need
                    //  to ensure that it is not larger than its left neighbor.

                    int index = 0;
//                    int currentTopic = currentTypeTopicCounts[index] & topicMask;
                    int currentValue;

//                    while (currentTypeTopicCounts[index] > 0 && currentTopic != topic) {
//                        index++;
//
//						/*
//							// Debugging output...
//   					if (index >= currentTypeTopicCounts.length) {
//							for (int i=0; i < currentTypeTopicCounts.length; i++) {
//								System.out.println((currentTypeTopicCounts[i] & topicMask) + ":" +
//												   (currentTypeTopicCounts[i] >> topicBits) + " ");
//							}
//
//							System.out.println(type + " " + typeTotals[type]);
//						}
//						*/
//                        currentTopic = currentTypeTopicCounts[index] & topicMask;
//                    }
//                    currentValue = currentTypeTopicCounts[index] >> topicBits;

//                    if (currentValue == 0) {
//                        // new value is 1, so we don't have to worry about sorting
//                        //  (except by topic suffix, which doesn't matter)
//
//                        currentTypeTopicCounts[index] =
//                                (1 << topicBits) + topic;
//                    }
//                    else {
//                        currentTypeTopicCounts[index] =
//                                ((currentValue + 1) << topicBits) + topic;
//
//                        // Now ensure that the array is still sorted by
//                        //  bubbling this value up.
//                        while (index > 0 &&
//                                currentTypeTopicCounts[index] > currentTypeTopicCounts[index - 1]) {
//                            int temp = currentTypeTopicCounts[index];
//                            currentTypeTopicCounts[index] = currentTypeTopicCounts[index - 1];
//                            currentTypeTopicCounts[index - 1] = temp;
//
//                            index--;
//                        }
//                    }
                }
            }

            TopicAssignment t = new TopicAssignment (instances, topicSequences);
            data.add (t);
        }

//        initializeHistograms();

//        languageSmoothingOnlyMasses = new double[ numLanguages ];
//        languageCachedCoefficients = new double[ numLanguages ][ numTopics ];

//        cacheValues();
    }

    @Override
    public void estimate () throws IOException {
        estimate (numIterations);
    }

    @Override
    public void estimate (int iterationsThisRound) throws IOException {

        long startTime = System.currentTimeMillis();
        int maxIteration = iterationsSoFar + iterationsThisRound;

        long totalTime = 0;

        for ( ; iterationsSoFar <= maxIteration; iterationsSoFar++) {
            long iterationStart = System.currentTimeMillis();

            if (showTopicsInterval != 0 && iterationsSoFar != 0 && iterationsSoFar % showTopicsInterval == 0) {
                System.out.println();
                printTopWords (System.out, wordsPerTopic, false);

            }

//            if (saveStateInterval != 0 && iterationsSoFar % saveStateInterval == 0) {
//                this.printState(new File(stateFilename + '.' + iterationsSoFar));
//            }

			/*
			  if (saveModelInterval != 0 && iterations % saveModelInterval == 0) {
			  this.write (new File(modelFilename+'.'+iterations));
			  }
			*/

            // TODO this condition should also check that we have more than one sample to work with here
            // (The number of samples actually obtained is not yet tracked.)
//            if (iterationsSoFar > burninPeriod && optimizeInterval != 0 &&
//                    iterationsSoFar % optimizeInterval == 0) {
//
//                alphaSum = Dirichlet.learnParameters(alpha, topicDocCounts, docLengthCounts);
//                optimizeBetas();
//                clearHistograms();
//                cacheValues();
//            }

            // Loop over every document in the corpus
            topicTermCount = betaTopicCount = smoothingOnlyCount = 0;

            for (int doc = 0; doc < data.size(); doc++) {
                sampleTopicsForOneDoc (data.get(doc), doc,
                        (iterationsSoFar >= burninPeriod &&
                                iterationsSoFar % saveSampleInterval == 0));
            }

            long elapsedMillis = System.currentTimeMillis() - iterationStart;
            totalTime += elapsedMillis;

            if ((iterationsSoFar + 1) % 10 == 0) {

                double ll = modelLogLikelihood();
                System.out.println(elapsedMillis + "\t" + totalTime + "\t" +
                        ll);
            }
            else {
                System.out.print(elapsedMillis + " ");
            }
        }

		/*
		long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
		long minutes = seconds / 60;	seconds %= 60;
		long hours = minutes / 60;	minutes %= 60;
		long days = hours / 24;	hours %= 24;
		System.out.print ("\nTotal time: ");
		if (days != 0) { System.out.print(days); System.out.print(" days "); }
		if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
		if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
		System.out.print(seconds); System.out.println(" seconds");
		*/
    }


    protected void sampleTopicsForOneDoc (TopicAssignment topicAssignment, int docIndex,
                                          boolean shouldSaveState) {
        int[] currentTypeTopicCounts;
        int type, oldTopic, newTopic;
        double value = 0;
        double topicWeightsSum;

        int[] localTopicCounts = new int[numTopics];

        for (int language = 0; language < numLanguages; language++) {

            int[] oneDocTopics =
                    topicAssignment.topicSequences[language].getFeatures();
            int docLength =
                    topicAssignment.topicSequences[language].getLength();

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

            int[][] typeTopicCounts = languageTypeTopicCountsNonsparse[language];
            int[] tokensPerTopic = languageTokensPerTopic[language];
            double beta = betas[language];
            double betaSum = betaSums[language];

            double[] topicTermScores = new double[numTopics];
            double score, sum;
            double[][] typeTopicSums = null;
            double[] mus = null;
            double[] sigmas = null;
            if (hasValue[language]) {
                typeTopicSums = languageTypeTopicSums[language];
                mus = languageMus[language];
                sigmas = languageSigma2s[language];
            }

            for (int position = 0; position < docLength; position++) {
                type = tokenSequence.getIndexAtPosition(position);
                oldTopic = oneDocTopics[position];
//                if (oldTopic == -1) { continue; } ??
                currentTypeTopicCounts = typeTopicCounts[type];

                localTopicCounts[oldTopic] --;
                tokensPerTopic[oldTopic] --;
                assert(tokensPerTopic[oldTopic] >= 0) : "old Topic " + oldTopic + " below 0";
                currentTypeTopicCounts[oldTopic] --;

                double regu = 0;
                double[] tmpSigma2s = null;
                double[] powers = null;
                if (hasValue[language]) {
//                    value = values[language][position];
                    value = realFeatures[language].get(docIndex)[position];
                    typeTopicSums[type][oldTopic] -= value;
                    tmpSigma2s = new double[numTopics];
                    powers = new double[numTopics];
                    if (languageSigma2s[language][type] != 0) {
                        double logMax = Double.NEGATIVE_INFINITY;
                        for (int topic = 0; topic < numTopics; topic++) {
                            double tmpMu = (mus[type] + typeTopicSums[type][topic]) / (1 + currentTypeTopicCounts[topic]);
                            tmpSigma2s[topic] = languageSigma2s[language][type] / (1 + currentTypeTopicCounts[topic]);
                            powers[topic] = -(value - tmpMu) * (value - tmpMu) / (2 * tmpSigma2s[topic]);
                            if (powers[topic] > logMax) {
                                logMax = powers[topic];
                            }
                        }
                        regu = -logMax;
                    }
                }

                sum = 0;


                for (int topic = 0; topic < numTopics; topic++) {
                    score =
                            (alpha[topic] + localTopicCounts[topic]) *
                                    ((beta + currentTypeTopicCounts[topic]) /
                                            (betaSum + tokensPerTopic[topic]));

                    if (hasValue[language] && languageSigma2s[language][type] != 0) {
                        double normalFactor = 1 / Math.sqrt(tmpSigma2s[topic]);
                        normalFactor *= Math.exp(powers[topic] + regu);
                        score *= normalFactor;
//                    System.out.println(alphabet.lookupObject(type) + ", tmpsig:" + tmpSigma2s[topic] + ", power: " + powers[topic] + ", normalF: " + normalFactor);
                    }

                    sum += score;
                    topicTermScores[topic] = score;
                }

                double sample = random.nextUniform() * sum;

                // Figure out which topic contains that point
                newTopic = -1;
                while (sample > 0.0) {
                    newTopic++;
                    sample -= topicTermScores[newTopic];
                }
//
//                for (int i = 0; i < numTopics; i++) {
//                    System.out.print(localTopicCounts[i]
//                            + "*" + currentTypeTopicCounts[i]
//                            + "/" + tokensPerTopic[i]
//                            + ":" + topicTermScores[i] + ", ");
//                }
//                System.out.println();

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

    public List<TopicWord2> getTopWordsDist() {
        List<TopicWord2> tws = new ArrayList<>();
//        double[][][] result = new double[numLanguages][numTopics][];
//        int[][] countTopicTotal = new int[numLanguages][numTopics];
//        for (int lan = 0; lan < numLanguages; lan++) {
//                for (int t = 0; t < numTopics; t++) {
//                    result[lan][t] = new double[vocabularySizes[lan]];
//                }
//        }
        for (int lan = 0; lan < numLanguages; lan++) {
//			System.out.println(lan+"---------------------");
            TopicWord2 tw = new TopicWord2();
            int limit = tw.TOPIC_WORD_LIMIT;
            for (int topic = 0; topic < numTopics; topic++) {
//				System.out.println(topic);
                Map<String, Double> mp = new HashMap<>();
                Map<String, Double> mvalues = null;
                if (hasValue[lan]) {
                    mvalues = new HashMap<>();
                }
                for (int w = 0; w < vocabularySizes[lan]; w++) {
                    double count = languageTypeTopicCountsNonsparse[lan][w][topic];
                    if (count == 0) continue;
                    double dist = (count + betas[lan])/
                            (languageTokensPerTopic[lan][topic] + betas[lan]*vocabularySizes[lan]);
                    String word = alphabets[lan].lookupObject(w).toString();
                    mp.put(word, dist);
                    if (hasValue[lan]) {
                        double value = languageTypeTopicSums[lan][w][topic] / count;
                        mvalues.put(word, value);
                    }
                    if (mp.size()>limit){
                        double min = 10.1;
                        String mins = null;
                        for (Map.Entry<String,Double> entry:mp.entrySet() ){
                            if (entry.getValue()<=min){
                                min=entry.getValue();
                                mins=entry.getKey();
                            }
                        }
                        mp.remove(mins);
                        if (hasValue[lan]) {
                            mvalues.remove(mins);
                        }
                    }
                }
                tw.addTopicWordDis(mp);
                tw.addTopicValues(mvalues);
//				System.out.println();
            }
            tws.add(tw);
        }
        return tws;
    }

    @Override
    public void printTopWords (PrintStream out, int numWords, boolean usingNewLines) {

        TreeSet[][] languageTopicSortedWords = new TreeSet[numLanguages][numTopics];
        for (int language = 0; language < numLanguages; language++) {
            TreeSet[] topicSortedWords = languageTopicSortedWords[language];
            for (int topic = 0; topic < numTopics; topic++) {
                topicSortedWords[topic] = new TreeSet<IDSorter>();
                for (int type = 0; type < vocabularySizes[language]; type++) {
                    double topicTotal = languageTokensPerTopic[language][topic];
                    int count = languageTypeTopicCountsNonsparse[language][type][topic];
                    topicSortedWords[topic].add(new IDSorter(type, count / topicTotal));
//                    out.println("----" + type + "----");
                }
            }
        }

//        out.println("----" + languageTopicSortedWords[0][2].size() + "----");
        for (int topic = 0; topic < numTopics; topic++) {
            out.println (topic + "\t" + formatter.format(alpha[topic]));
            for (int language = 0; language < numLanguages; language++) {
                out.print(" " + language + "\t" + languageTokensPerTopic[language][topic] + "\t" + betas[language] + "\t");
                TreeSet<IDSorter> sortedWords = languageTopicSortedWords[language][topic];
                Alphabet alphabet = alphabets[language];
                int word = 0;
                Iterator<IDSorter> iterator = sortedWords.iterator();
                while (iterator.hasNext() && word < numWords) {
                    IDSorter info = iterator.next();
                    out.print(alphabet.lookupObject(info.getID()) + ":" + info.getWeight() + " ");
                    word++;
                }
                out.println();
            }
        }
    }

    @Override
    public double modelLogLikelihood() {
        double logLikelihood = 0.0;
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
        double[] topicLogGammas = new double[numTopics];
        int[] docTopics;
        int[] docTypes;
        double[] docValues;

        for (int topic=0; topic < numTopics; topic++) {
            topicLogGammas[ topic ] = Dirichlet.logGammaStirling( alpha[topic] );
        }

        for (int doc=0; doc < data.size(); doc++) {

            int totalLength = 0;

            for (int language = 0; language < numLanguages; language++) {

                LabelSequence topicSequence = (LabelSequence) data.get(doc).topicSequences[language];
                int[] currentDocTopics = topicSequence.getFeatures();

                totalLength += topicSequence.getLength();

                // Count up the tokens
                for (int token=0; token < topicSequence.getLength(); token++) {
                    topicCounts[ currentDocTopics[token] ]++;
                }
            }

            for (int topic=0; topic < numTopics; topic++) {
                if (topicCounts[topic] > 0) {
                    logLikelihood += (Dirichlet.logGammaStirling(alpha[topic] + topicCounts[topic]) -
                            topicLogGammas[ topic ]);
                }
            }

            // subtract the (count + parameter) sum term
            logLikelihood -= Dirichlet.logGammaStirling(alphaSum + totalLength);

            Arrays.fill(topicCounts, 0);
        }

        // add the parameter sum term
        logLikelihood += data.size() * Dirichlet.logGammaStirling(alphaSum);

        // And the topics

        for (int language = 0; language < numLanguages; language++) {
            int[][] typeTopicCounts = languageTypeTopicCountsNonsparse[language];
            int[] tokensPerTopic = languageTokensPerTopic[language];
            double beta = betas[language];
            double logGammaBeta = Dirichlet.logGamma(beta);

            double[][] typeTopicSquareSum = new double[vocabularySizes[language]][numTopics];

            if (hasValue[language]) {
                for (int doc = 0; doc < data.size(); doc++) {
                    docTopics = data.get(doc).topicSequences[language].getFeatures();
                    docTypes = ((FeatureSequence) data.get(doc).instances[language].getData()).getFeatures();
                    docValues = realFeatures[language].get(doc);
                    for (int token=0; token < ((FeatureSequence) data.get(doc).instances[language].getData()).size(); token++) {
                        int topic = docTopics[token];
                        int type = docTypes[token];
                        double value = docValues[token];
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
                    if (Double.isNaN(logLikelihood)) {
                        System.out.println(count);
                        System.exit(1);
                    }
                }
            }

            for (int topic=0; topic < numTopics; topic++) {
                logLikelihood -=
                        Dirichlet.logGammaStirling( (beta * numTopics) +
                                tokensPerTopic[ topic ] );
                if (Double.isNaN(logLikelihood)) {
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
                            double tmpSum = languageTypeTopicSums[language][type][topic];
                            double logll = tmpSum + languageMus[language][type];
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

        if (Double.isNaN(logLikelihood)) {
            System.out.println("at the end");
            System.exit(1);
        }


        return logLikelihood;
    }

//    @Override
//    public TopicInferencer getInferencer(int language) {
//        return new TopicInferencer(languageTypeTopicCountsNonsparse[language], languageTokensPerTopic[language],
//                alphabets[language],
//                alpha, betas[language], betaSums[language], false);
//    }

    public static void main(String[] args) throws IOException{
        InstanceList[] instls = new InstanceList[1];
//        SimpleLDAMultiFeature model = new SimpleLDAMultiFeature(20, 1, 0.1);

        ArrayList<Pipe> pipeList = new ArrayList<>();
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\S+")));
        pipeList.add(new TokenSequence2FeatureSequence());

        InstanceList instances = new InstanceList(new SerialPipes(pipeList));
        Reader fileReader = new InputStreamReader(new FileInputStream(new File("C:\\LDAexp\\realFeatureLDA\\simple")));
        instances.addThruPipe(new CsvIterator(fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1));

        instls[0] = instances;
        MVMATopicModel polymodelNoParse = new MVMATopicModel(3, 0.1);
        polymodelNoParse.addInstances(instls);
        polymodelNoParse.setNumIterations(800);
//        polymodelNoParse.optimizeBetas();
        polymodelNoParse.estimate();

        System.out.println("---------------------------");


        polymodelNoParse.printTopWords(System.out, 5, true);

    }
}
