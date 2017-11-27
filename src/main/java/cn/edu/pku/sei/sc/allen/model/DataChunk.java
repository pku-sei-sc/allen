package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Created by dell on 2017/11/26.
 */
@Entity
public class DataChunk extends BaseModel {

    private long dataSourceId;

    private String sql;

    private String idName;

    private String tokenName;

    private String valueName;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private Integer totalInstances;

    private Integer totalTypes;

    private Long totalTokens;

    private String manifestId;

    public long getDataSourceId() {
        return dataSourceId;
    }

    public DataChunk setDataSourceId(long dataSourceId) {
        this.dataSourceId = dataSourceId;
        return this;
    }

    public String getSql() {
        return sql;
    }

    public DataChunk setSql(String sql) {
        this.sql = sql;
        return this;
    }

    public String getIdName() {
        return idName;
    }

    public DataChunk setIdName(String idName) {
        this.idName = idName;
        return this;
    }

    public String getTokenName() {
        return tokenName;
    }

    public DataChunk setTokenName(String tokenName) {
        this.tokenName = tokenName;
        return this;
    }

    public String getValueName() {
        return valueName;
    }

    public DataChunk setValueName(String valueName) {
        this.valueName = valueName;
        return this;
    }

    public String getManifestId() {
        return manifestId;
    }

    public DataChunk setManifestId(String manifestId) {
        this.manifestId = manifestId;
        return this;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public DataChunk setStatus(TaskStatus status) {
        this.status = status;
        return this;
    }

    public Integer getTotalInstances() {
        return totalInstances;
    }

    public DataChunk setTotalInstances(Integer totalInstances) {
        this.totalInstances = totalInstances;
        return this;
    }

    public Integer getTotalTypes() {
        return totalTypes;
    }

    public DataChunk setTotalTypes(Integer totalTypes) {
        this.totalTypes = totalTypes;
        return this;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public DataChunk setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
        return this;
    }
}
