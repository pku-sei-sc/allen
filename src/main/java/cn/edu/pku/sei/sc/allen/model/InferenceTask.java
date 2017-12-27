package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.Entity;

/**
 * Created by Shawn on 2017/12/12.
 */
@Entity
public class InferenceTask extends BaseModel {

    private long dataChunkId;

    private String ruleFile;

    private String modelManifest;

    private int language;

    private long randomSeed;

    private int numIterations;

    private int burnIn;

    private int thinning;

    private TaskStatus status;

    private String manifestId;

    public long getDataChunkId() {
        return dataChunkId;
    }

    public InferenceTask setDataChunkId(long dataChunkId) {
        this.dataChunkId = dataChunkId;
        return this;
    }

    public String getModelManifest() {
        return modelManifest;
    }

    public InferenceTask setModelManifest(String modelManifest) {
        this.modelManifest = modelManifest;
        return this;
    }

    public int getLanguage() {
        return language;
    }

    public InferenceTask setLanguage(int language) {
        this.language = language;
        return this;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public InferenceTask setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
        return this;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public InferenceTask setNumIterations(int numIterations) {
        this.numIterations = numIterations;
        return this;
    }

    public int getBurnIn() {
        return burnIn;
    }

    public InferenceTask setBurnIn(int burnIn) {
        this.burnIn = burnIn;
        return this;
    }

    public int getThinning() {
        return thinning;
    }

    public InferenceTask setThinning(int thinning) {
        this.thinning = thinning;
        return this;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public InferenceTask setStatus(TaskStatus status) {
        this.status = status;
        return this;
    }

    public String getManifestId() {
        return manifestId;
    }

    public InferenceTask setManifestId(String manifestId) {
        this.manifestId = manifestId;
        return this;
    }

    public String getRuleFile() {
        return ruleFile;
    }

    public InferenceTask setRuleFile(String ruleFile) {
        this.ruleFile = ruleFile;
        return this;
    }
}
