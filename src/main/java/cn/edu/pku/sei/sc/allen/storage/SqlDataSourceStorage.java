package cn.edu.pku.sei.sc.allen.storage;

import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by dell on 2017/11/27.
 */
@Repository
public interface SqlDataSourceStorage extends CrudRepository<SqlDataSource, Long> {

    List<SqlDataSource> findAll();

}
