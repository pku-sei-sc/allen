package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import java.util.List;

/**
 * Created by dell on 2017/11/28.
 */
@Entity
public class TrainingTask extends BaseModel {

    @ElementCollection(fetch = FetchType.EAGER)
    private List<Long> dataChunkIds;

    private String ruleFile;

    private int totalTopics;

    private double alphaSum;

    private double betaSum;

    private long randomSeed;

    private int numIteration;

    private int showTopicsInterval;

    private int showTopicsNum;

    private TaskStatus status;

    private String manifestId;

    public List<Long> getDataChunkIds() {
        return dataChunkIds;
    }

    public TrainingTask setDataChunkIds(List<Long> dataChunkIds) {
        this.dataChunkIds = dataChunkIds;
        return this;
    }

    public int getTotalTopics() {
        return totalTopics;
    }

    public TrainingTask setTotalTopics(int totalTopics) {
        this.totalTopics = totalTopics;
        return this;
    }

    public double getAlphaSum() {
        return alphaSum;
    }

    public TrainingTask setAlphaSum(double alphaSum) {
        this.alphaSum = alphaSum;
        return this;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TrainingTask setStatus(TaskStatus status) {
        this.status = status;
        return this;
    }

    public String getManifestId() {
        return manifestId;
    }

    public TrainingTask setManifestId(String manifestId) {
        this.manifestId = manifestId;
        return this;
    }

    public int getNumIteration() {
        return numIteration;
    }

    public TrainingTask setNumIteration(int numIteration) {
        this.numIteration = numIteration;
        return this;
    }

    public int getShowTopicsInterval() {
        return showTopicsInterval;
    }

    public TrainingTask setShowTopicsInterval(int showTopicsInterval) {
        this.showTopicsInterval = showTopicsInterval;
        return this;
    }

    public int getShowTopicsNum() {
        return showTopicsNum;
    }

    public TrainingTask setShowTopicsNum(int showTopicsNum) {
        this.showTopicsNum = showTopicsNum;
        return this;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public TrainingTask setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
        return this;
    }

    public double getBetaSum() {
        return betaSum;
    }

    public TrainingTask setBetaSum(double betaSum) {
        this.betaSum = betaSum;
        return this;
    }

    public String getRuleFile() {
        return ruleFile;
    }

    public TrainingTask setRuleFile(String ruleFile) {
        this.ruleFile = ruleFile;
        return this;
    }
}
