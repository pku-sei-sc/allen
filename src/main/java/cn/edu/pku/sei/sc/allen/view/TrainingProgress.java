package cn.edu.pku.sei.sc.allen.view;

/**
 * Created by dell on 2017/11/28.
 */
public class TrainingProgress {

    private int currentIteration;

    private int maxIteration;

    private long totalCost;

    public int getCurrentIteration() {
        return currentIteration;
    }

    public TrainingProgress setCurrentIteration(int currentIteration) {
        this.currentIteration = currentIteration;
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
