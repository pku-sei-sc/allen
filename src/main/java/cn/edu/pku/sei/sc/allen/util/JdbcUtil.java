package cn.edu.pku.sei.sc.allen.util;

import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by dell on 2017/11/27.
 */
public class JdbcUtil {
    public static JdbcTemplate getJdbcTemplate(SqlDataSource sqlDataSource) {
        return new JdbcTemplate(getDataSource(sqlDataSource));
    }

    public static DataSource getDataSource(SqlDataSource sqlDataSource) {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(sqlDataSource.getUrl());
        properties.setDriverClassName(sqlDataSource.getDriverClassName());
        properties.setUsername(sqlDataSource.getUsername());
        properties.setPassword(sqlDataSource.getPassword());
        properties.setJmxEnabled(false);
        properties.setTestOnBorrow(true);
        properties.setValidationQuery("select 1");
        properties.setTestOnReturn(false);
        properties.setValidationInterval(30_000);
        properties.setMaxActive(10);
        properties.setInitialSize(2);
        properties.setMinIdle(2);
        properties.setMaxIdle(5);
        properties.setRemoveAbandoned(true);
        return new DataSource(properties);
    }

    public static String wrapNames(List<String> names) {
        List<String> safeNames = names.stream().map(name -> name.replaceAll("`", ""))
                .collect(Collectors.toList());
        return "`" + String.join("`,`", safeNames) + "`";
    }

    public static String wrapNames(String...names) {
        return wrapNames(Arrays.asList(names));
    }

    public static String wrapNames(List<String> nameList, String...names) {
        List<String> tempList =  Arrays.asList(names);
        tempList.addAll(nameList);
        return wrapNames(tempList);
    }

}
