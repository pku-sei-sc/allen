package cn.edu.pku.sei.sc.allen.model.data;

public class ResultData implements Comparable<ResultData> {
    public String index;
    public double similarity;
    public ResultData(String index, double similarity){
        this.index = index;
        this.similarity = similarity;
    }

    @Override
    public int compareTo(ResultData o) {

        return this.similarity>o.similarity? -1:1;
    }

    public double getSimilarity() {
        return similarity;
    }
}
