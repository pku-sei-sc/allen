package cn.edu.pku.sei.sc.allen.view;

/**
 * Created by dell on 2017/11/28.
 */
public class TrainingProgress {

    private int finishedIteration;

    private int maxIteration;

    private long totalCost;

    public int getFinishedIteration() {
        return finishedIteration;
    }

    public TrainingProgress setFinishedIteration(int finishedIteration) {
        this.finishedIteration = finishedIteration;
        return this;
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public TrainingProgress setMaxIteration(int maxIteration) {
        this.maxIteration = maxIteration;
        return this;
    }

    public long getTotalCost() {
        return totalCost;
    }

    public TrainingProgress setTotalCost(long totalCost) {
        this.totalCost = totalCost;
        return this;
    }
}
