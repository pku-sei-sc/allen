package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.Entity;

/**
 * Created by dell on 2017/11/26.
 */
@Entity
public class DataChunk extends BaseModel {

    public long getId() {
        return id;
    }

    public DataChunk setId(long id) {
        this.id = id;
        return this;
    }

    public long getCreateTime() {
        return createTime;
    }

    public DataChunk setCreateTime(long createTime) {
        this.createTime = createTime;
        return this;
    }
}
