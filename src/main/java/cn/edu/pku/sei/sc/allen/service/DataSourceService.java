package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import cn.edu.pku.sei.sc.allen.storage.SqlDataSourceStorage;
import cn.edu.pku.sei.sc.allen.util.JdbcUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by dell on 2017/11/27.
 */
@Service
public class DataSourceService {

    @Autowired
    private SqlDataSourceStorage sqlDataSourceStorage;

    public SqlDataSource createSqlDataSource(String driverClassName, String url, String username, String password) {
        SqlDataSource sqlDataSource = new SqlDataSource()
                .setDriverClassName(driverClassName)
                .setUrl(url)
                .setUsername(username)
                .setPassword(password);
        return sqlDataSourceStorage.save(sqlDataSource);
    }

    public boolean testSqlDataSource(long id) {
        SqlDataSource sqlDataSource = sqlDataSourceStorage.findOne(id);
        JdbcTemplate jdbcTemplate = JdbcUtil.getJdbcTemplate(sqlDataSource);
        try {
            jdbcTemplate.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
