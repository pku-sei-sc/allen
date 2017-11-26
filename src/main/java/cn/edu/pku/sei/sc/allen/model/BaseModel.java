package cn.edu.pku.sei.sc.allen.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

/**
 * Created by dell on 2017/11/27.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
abstract public class BaseModel {

    @Id
    @GeneratedValue
    protected long id;

    @CreatedDate
    protected long createTime;

    public long getId() {
        return id;
    }

    public BaseModel setId(long id) {
        this.id = id;
        return this;
    }

    public long getCreateTime() {
        return createTime;
    }

    public BaseModel setCreateTime(long createTime) {
        this.createTime = createTime;
        return this;
    }
}
