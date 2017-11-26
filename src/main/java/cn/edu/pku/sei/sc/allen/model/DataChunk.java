package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Created by dell on 2017/11/26.
 */
@Entity
public class DataChunk {

    @Id
    @GeneratedValue
    private long id;

    @Column
    private long createTime;

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
