package cn.edu.pku.sei.sc.allen.model;

import com.fasterxml.jackson.databind.ser.Serializers;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
public class DataChunkMetaPac extends BaseModel{

    private long diagnoseId;

    private long medicineId;

    private String descriptionDiagnose;

    private String descriptionMedicine;

    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    public long getDiagnoseId() {
        return diagnoseId;
    }

    public DataChunkMetaPac setDiagnoseId(long diagnoseId) {
        this.diagnoseId = diagnoseId;
        return this;
    }

    public long getMedicineId() {
        return medicineId;
    }

    public DataChunkMetaPac setMedicineId(long medicineId) {
        this.medicineId = medicineId;
        return this;
    }

    public String getDescriptionDiagnose() {
        return descriptionDiagnose;
    }

    public DataChunkMetaPac setDescriptionDiagnose(String descriptionDiagnose) {
        this.descriptionDiagnose = descriptionDiagnose;
        return this;
    }

    public String getDescriptionMedicine() {
        return descriptionMedicine;
    }

    public DataChunkMetaPac setDescriptionMedicine(String descriptionMedicine) {
        this.descriptionMedicine = descriptionMedicine;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public DataChunkMetaPac setDescription(String description) {
        this.description = description;
        return this;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public DataChunkMetaPac setStatus(TaskStatus status) {
        this.status = status;
        return this;
    }
}
