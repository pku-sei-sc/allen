package cn.edu.pku.sei.sc.allen.model;

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * Created by dell on 2017/11/27.
 */
@Entity
public class SqlDataSource extends BaseModel {

    @Column
    private String driverClassName;

    @Column
    private String url;

    @Column
    private String username;

    @Column
    private String password;

    public String getDriverClassName() {
        return driverClassName;
    }

    public SqlDataSource setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public SqlDataSource setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public SqlDataSource setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public SqlDataSource setPassword(String password) {
        this.password = password;
        return this;
    }
}
