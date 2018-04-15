package cn.edu.pku.sei.sc.allen.controller;

import cc.mallet.classify.RankMaxEntTrainer;
import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.DataChunkMeta;
import cn.edu.pku.sei.sc.allen.model.DataChunkMetaPac;
import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import cn.edu.pku.sei.sc.allen.service.DataSourceService;
import cn.edu.pku.sei.sc.allen.service.InputDataService;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.DataChunkPacStorage;
import cn.edu.pku.sei.sc.allen.storage.SqlDataSourceStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import sun.misc.Request;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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

    @Autowired
    private DataChunkPacStorage dataChunkPacStorage;

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
                                   @RequestParam(required = false) String sql,
                                   @RequestParam(required = false, defaultValue = "") List<String> sqls,
                                   @RequestParam String idName,
                                   @RequestParam String tokenName,
                                   @RequestParam(required = false) String valueName) {
        if (sqls.size() == 0 && sql == null)
            throw new BadRequestException("SQL语句缺失");
        if (sqls.size() == 0)
            sqls = Collections.singletonList(sql);
        return inputDataService.createDataChunk(dataSourceId, sqls, idName, tokenName, valueName);
    }

    // FIXME: 2018/4/14 获取id有误
    @RequestMapping(value = "/dataPac", method = RequestMethod.POST)
    public DataChunkMetaPac inputDataPac(@RequestParam long dataSourceId1,
                                         @RequestParam(required = false) String sql1,
                                         @RequestParam(required = false, defaultValue = "") List<String> sqls1,
                                         @RequestParam String idName1,
                                         @RequestParam String tokenName1,
                                         @RequestParam(required = false) String valueName1,
                                         @RequestParam long dataSourceId2,
                                         @RequestParam(required = false) String sql2,
                                         @RequestParam(required = false, defaultValue = "") List<String> sqls2,
                                         @RequestParam String idName2,
                                         @RequestParam String tokenName2,
                                         @RequestParam(required = false) String valueName2,
                                         @RequestParam(required = false) String descriptionDiagnose,
                                         @RequestParam(required = false) String descriptionMedicine,
                                         @RequestParam(required = false) String description){
        long count = dataChunkMetaStorage.count();
        DataChunkMeta diagnose = inputData(dataSourceId1, sql1, sqls1, idName1, tokenName1, valueName1);
        DataChunkMeta medicine = inputData(dataSourceId2, sql2, sqls2, idName2, tokenName2, valueName2);
        return inputDataService.createDataChunkPac(count+1,count+2,descriptionDiagnose,descriptionMedicine,description);
    }

    @RequestMapping(value = "/data/{dataChunkId}", method = RequestMethod.GET)
    public List<DataChunkMeta> getDataChunkById(@PathVariable long dataChunkId){
        List<DataChunkMeta> list = new ArrayList<>();
        list.add(dataChunkMetaStorage.findOne(dataChunkId));
        return list;
    }

    @RequestMapping(value = "/datapac/{dataChunkPacId}/start", method = RequestMethod.POST)
    public void startDataChunkPac(@PathVariable long dataChunkPacId,
                           @RequestParam(required = false,defaultValue = "false") boolean forced) throws IOException, SQLException {
        inputDataService.inputDatachunkPac(dataChunkPacId, forced);
    }

    @RequestMapping(value = "/datapac/{dataChunkPacId}/delete", method = RequestMethod.POST)
    public void deleteDataChunkPac(@PathVariable long dataChunkPacId) {
        dataChunkMetaStorage.delete(dataChunkPacStorage.findOne(dataChunkPacId).getDiagnoseId());
        dataChunkMetaStorage.delete(dataChunkPacStorage.findOne(dataChunkPacId).getMedicineId());
        dataChunkPacStorage.delete(dataChunkPacId);

    }

    @RequestMapping(value = "/datapac/{dataChunkId}/delete2", method = RequestMethod.POST)
    public void deleteDataChunk(@PathVariable long dataChunkId) {
        dataChunkMetaStorage.delete(dataChunkId);
    }

    @RequestMapping(value = "/datapac", method = RequestMethod.GET)
    public List<DataChunkMetaPac> getDatachunkPac(){return dataChunkPacStorage.findAll();}

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
