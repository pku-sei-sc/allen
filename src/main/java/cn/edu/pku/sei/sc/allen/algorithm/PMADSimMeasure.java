package cn.edu.pku.sei.sc.allen.algorithm;

import java.util.List;

/**
 * Created by xuany on 2017.3.25.
 */
public class PMADSimMeasure {

    public float innerProduct(float[] distA, float[] distB) {
        float r = 0;
        for (int i = 0; i < distA.length; i++) {
            r += distA[i] * distB[i];
        }
        return r;
    }


    public  float cosineSimilarity(float[] vectorA, float[] vectorB) {
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
    public float klDivergence(float[] p1, float[] p2) {
        float klDiv = 0.0f;
        for (int i = 0; i < p1.length; ++i) {
            if (p1[i] == 0) {
                continue;
            }
            if (p2[i] == 0.0) {
                continue;
            }
            klDiv += p1[i] * Math.log(p1[i] / p2[i]);
        }
        return klDiv; // moved this division out of the loop -DM
    }



    public static float sum(List<Float> ensScores) {
        float sum = 0;
        for (float float1 : ensScores) {

            sum += float1;
        }
        return sum;
    }

}

