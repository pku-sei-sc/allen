package cn.edu.pku.sei.sc.allen.model;
import cn.edu.pku.sei.sc.allen.algorithm.TopicModel;
import com.fasterxml.jackson.databind.ser.Serializers;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
public class InferenceTaskPac extends BaseModel{
    private String name;

    private long DatachunkPacId;

    private long inferenceTaskDiagnoseId;

    private long inferenceTaskMedicineId;

    private String dataChunkPacName;

    private String ModelManifestId;

    private String method;

    private int top;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    public String getModelManifestId() {
        return ModelManifestId;
    }

    public InferenceTaskPac setModelManifestId(String modelManifestId) {
        ModelManifestId = modelManifestId;
        return this;
    }



    public TaskStatus getStatus() {
        return status;
    }

    public InferenceTaskPac setStatus(TaskStatus status) {
        this.status = status;
        return this;
    }

    public long getInferenceTaskDiagnoseId() {
        return inferenceTaskDiagnoseId;
    }

    public InferenceTaskPac setInferenceTaskDiagnoseId(long inferenceTaskDiagnoseId) {
        this.inferenceTaskDiagnoseId = inferenceTaskDiagnoseId;
        return this;
    }

    public long getInferenceTaskMedicineId() {
        return inferenceTaskMedicineId;
    }

    public InferenceTaskPac setInferenceTaskMedicineId(long inferenceTaskMedicineId) {
        this.inferenceTaskMedicineId = inferenceTaskMedicineId;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public InferenceTaskPac setMethod(String method) {
        this.method = method;
        return this;
    }

    public int getTop() {
        return top;
    }

    public InferenceTaskPac setTop(int top) {
        this.top = top;
        return this;
    }

    public String getName() {
        return name;
    }

    public InferenceTaskPac setName(String name) {
        this.name = name;
        return this;
    }

    public long getDatachunkPacId() {
        return DatachunkPacId;
    }

    public InferenceTaskPac setDatachunkPacId(long datachunkPacId) {
        DatachunkPacId = datachunkPacId;
        return this;
    }

    public String getDataChunkPacName() {
        return dataChunkPacName;
    }

    public InferenceTaskPac setDataChunkPacName(String dataChunkPacName) {
        this.dataChunkPacName = dataChunkPacName;
        return this;
    }
}
