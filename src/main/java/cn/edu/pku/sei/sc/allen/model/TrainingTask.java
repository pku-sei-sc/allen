package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import java.util.List;

/**
 * Created by dell on 2017/11/28.
 */
@Entity
public class TrainingTask extends BaseModel {

    @ElementCollection
    private List<Long> dataChunkIds;

    private int totalTopics;

    private int alpha;

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

    public int getAlpha() {
        return alpha;
    }

    public TrainingTask setAlpha(int alpha) {
        this.alpha = alpha;
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
}
