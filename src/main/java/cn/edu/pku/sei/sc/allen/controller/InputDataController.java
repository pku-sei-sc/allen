package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.model.DataChunkMeta;
import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import cn.edu.pku.sei.sc.allen.service.DataSourceService;
import cn.edu.pku.sei.sc.allen.service.InputDataService;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.SqlDataSourceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.sql.SQLException;
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

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    @Autowired
    private InputDataService inputDataService;

    @RequestMapping(value = "/data-source", method = RequestMethod.POST)
    public SqlDataSource createSqlDataSource(@RequestParam String driverClassName,
                                             @RequestParam String url,
                                             @RequestParam(required = false, defaultValue = "") String username,
                                             @RequestParam(required = false, defaultValue = "") String password) {
        return dataSourceService.createSqlDataSource(driverClassName, url, username, password);
    }

    @RequestMapping(value = "/data-source/{dataSourceId}/test", method = RequestMethod.POST)
    public boolean testSqlDataSource(@PathVariable long dataSourceId) {
        return dataSourceService.testSqlDataSource(dataSourceId);
    }

    @RequestMapping(value = "/data-source", method = RequestMethod.GET)
    public List<SqlDataSource> getSqlDataSources() {
        return sqlDataSourceStorage.findAll();
    }


    @RequestMapping(value = "/data", method = RequestMethod.POST)
    public DataChunkMeta inputData(@RequestParam long dataSourceId,
                                   @RequestParam String sql,
                                   @RequestParam String idName,
                                   @RequestParam String tokenName,
                                   @RequestParam(required = false) String valueName) {
        return inputDataService.createDataChunk(dataSourceId, sql, idName, tokenName, valueName);
    }

    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public List<DataChunkMeta> getDataChunks() {
        return dataChunkMetaStorage.findAll();
    }

    @RequestMapping(value = "/data/{dataChunkId}/start", method = RequestMethod.POST)
    public void startInput(@PathVariable long dataChunkId,
                           @RequestParam(required = false, defaultValue = "false") boolean forced) throws SQLException, IOException {
        inputDataService.inputDataChunk(dataChunkId, forced);
    }

    @RequestMapping(value = "/data/{dataChunkId}/progress", method = RequestMethod.GET)
    public long getProgress(@PathVariable long dataChunkId) {
        return inputDataService.getProgress(dataChunkId);
    }

}
