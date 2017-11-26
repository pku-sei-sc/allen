package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import cn.edu.pku.sei.sc.allen.service.DataSourceService;
import cn.edu.pku.sei.sc.allen.storage.SqlDataSourceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/input")
public class InputDataController {

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private SqlDataSourceStorage sqlDataSourceStorage;

    @RequestMapping(value = "/data-source", method = RequestMethod.POST)
    public SqlDataSource createSqlDataSource(@RequestParam String driverClassName,
                                             @RequestParam String url,
                                             @RequestParam(required = false) String username,
                                             @RequestParam(required = false) String password) {
        return dataSourceService.createSqlDataSource(driverClassName, url, username, password);
    }

    @RequestMapping(value = "/data-source/{dataSourceId}", method = RequestMethod.GET)
    public boolean testSqlDataSource(@PathVariable long dataSourceId) {
        return dataSourceService.testSqlDataSource(dataSourceId);
    }

    @RequestMapping(value = "/data-source", method = RequestMethod.GET)
    public List<SqlDataSource> getSqlDataSources() {
        return sqlDataSourceStorage.findAll();
    }


    @RequestMapping(value = "/data", method = RequestMethod.POST)
    public void inputData(@RequestParam long dataSourceId,
                          @RequestParam String sql,
                          @RequestParam String idName,
                          @RequestParam String wordName,
                          @RequestParam(required = false) String valueName) {

    }

    public void getProgress() {

    }

}
