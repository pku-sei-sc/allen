package cn.edu.pku.sei.sc.allen.service;

import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.model.SqlDataSource;
import cn.edu.pku.sei.sc.allen.model.TaskStatus;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.storage.DataChunkStorage;
import cn.edu.pku.sei.sc.allen.storage.SqlDataSourceStorage;
import cn.edu.pku.sei.sc.allen.util.JdbcUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by dell on 2017/11/27.
 */
@Service
@ConfigurationProperties("allen.input")
public class InputDataService {

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DataChunkStorage dataChunkStorage;

    @Autowired
    private SqlDataSourceStorage sqlDataSourceStorage;

    private Map<Long, Long> progressMap = new ConcurrentHashMap<>();

    private String dataPath = "data";

    private int partSize = 5000;

    public String getDataPath() {
        return dataPath;
    }

    public InputDataService setDataPath(String dataPath) {
        this.dataPath = dataPath;
        return this;
    }

    public int getPartSize() {
        return partSize;
    }

    public InputDataService setPartSize(int partSize) {
        this.partSize = partSize;
        return this;
    }

    @PostConstruct
    private void init() {
        if (dataPath.endsWith("/"))
            dataPath = dataPath.substring(0, dataPath.length() - 1);
        File dataDir = new File(dataPath);
        if (!dataDir.isDirectory() && !dataDir.mkdir())
            throw new IllegalStateException("无法创建data目录，请手动创建目录后重试");
    }

    public DataChunk createDataChunk(long dataSourceId, String sql, String idName, String tokenName, String valueName) {
        if (!dataSourceService.testSqlDataSource(dataSourceId))
            throw new IllegalStateException("数据源连接失败");
        DataChunk dataChunk = new DataChunk()
                .setDataSourceId(dataSourceId)
                .setSql(sql)
                .setIdName(idName)
                .setTokenName(tokenName)
                .setValueName(valueName)
                .setStatus(TaskStatus.Stopped);
        return dataChunkStorage.save(dataChunk);
    }

    private String getManifestDataPath(String manifestId) {
        return dataPath + "/" + manifestId + "/manifest.dat";
    }

    private String getDataChunkPartPath(String manifestId, int partNo) {
        return dataPath + "/" + manifestId + "/part" + partNo + ".dat";
    }

    @Async
    public void inputDataChunk(long dataChunkId, boolean forced) throws SQLException, IOException {
        DataChunk dataChunk = dataChunkStorage.findOne(dataChunkId);
        if (!dataSourceService.testSqlDataSource(dataChunk.getDataSourceId()))
            throw new IllegalStateException("无法连接到数据源");

        SqlDataSource sqlDataSource = sqlDataSourceStorage.findOne(dataChunk.getDataSourceId());

        if (dataChunk.getStatus() != TaskStatus.Stopped && !forced)
            throw new IllegalStateException("无法开始一个非停止状态的任务，尝试强制开始");

        dataChunk.setManifestId(UUID.randomUUID().toString());
        File manifestFile = new File(getManifestDataPath(dataChunk.getManifestId()));

        while(manifestFile.isFile()) {
            dataChunk.setManifestId(UUID.randomUUID().toString());
            manifestFile = new File(getManifestDataPath(dataChunk.getManifestId()));
        }

        if (!manifestFile.getParentFile().isDirectory() && !manifestFile.getParentFile().mkdir())
            throw new IllegalStateException("无法创建文件子目录");

        long tokenCnt = 0;
        if (progressMap.put(dataChunkId, tokenCnt) != null)
            throw new IllegalStateException("无法重复执行数据导入任务");

        dataChunk.setStatus(TaskStatus.Processing);
        dataChunkStorage.save(dataChunk);

        //开始导入数据，并写入文件

        DataFormat.Manifest.Builder manifestBuilder = DataFormat.Manifest.newBuilder();

        manifestBuilder.getDataChunkMetaBuilder().getDataSourceBuilder()
                .setDriverClassName(sqlDataSource.getDriverClassName())
                .setUrl(sqlDataSource.getUrl())
                .setUsername(sqlDataSource.getUsername())
                .setPassword(sqlDataSource.getPassword());

        manifestBuilder.getDataChunkMetaBuilder()
                .setSql(dataChunk.getSql())
                .setIdName(dataChunk.getIdName())
                .setWordName(dataChunk.getTokenName());

        if (dataChunk.getValueName() != null)
            manifestBuilder.getDataChunkMetaBuilder().setValueName(dataChunk.getValueName());

        manifestBuilder.setPartSize(partSize);

        //执行数据库语句并保存数据
        Map<String, DataFormat.Instance.Builder> instanceMap = new HashMap<>();
        Map<String, Map<String, DataFormat.Token.Builder>> instanceTokenMap = new HashMap<>();
        Set<String> tokenSet = new HashSet<>();

        try (Connection connection = JdbcUtil.getConnection(sqlDataSource)) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(dataChunk.getSql())) {
                if (sqlDataSource.getUrl().toLowerCase().contains("mysql")) //mysql特殊处理
                    preparedStatement.setFetchSize(Integer.MIN_VALUE);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    int idColumn = resultSet.findColumn(dataChunk.getIdName());
                    int tokenColumn = resultSet.findColumn(dataChunk.getTokenName());
                    int valueColumn = dataChunk.getValueName() == null ?
                            -1 : resultSet.findColumn(dataChunk.getValueName());

                    while (resultSet.next()) {
                        String instanceId = resultSet.getString(idColumn);
                        String token = resultSet.getString(tokenColumn);
                        if (instanceId == null || token == null) continue;

                        Float value = null;

                        if (dataChunk.getValueName() != null) {
                            String valueStr = resultSet.getString(valueColumn);
                            try {
                                value = Float.parseFloat(valueStr);
                            } catch (Exception e) {
                                continue;
                            }
                        }


                        if (!instanceMap.containsKey(instanceId)) {
                            instanceMap.put(instanceId, DataFormat.Instance.newBuilder());
                            instanceTokenMap.put(instanceId, new HashMap<>());
                        }

                        if (!instanceTokenMap.get(instanceId).containsKey(token)) {
                            DataFormat.Token.Builder builder = DataFormat.Token.newBuilder();
                            instanceTokenMap.get(instanceId).put(token, builder);
                            tokenSet.add(token);
                        }

                        DataFormat.Token.Builder tokenBuilder = instanceTokenMap.get(instanceId).get(token);
                        int count = tokenBuilder.getCount();
                        tokenBuilder.setCount(count + 1);
                        if (value != null)
                            tokenBuilder.addValues(value);
                        progressMap.put(dataChunkId, ++tokenCnt);
                    }
                }
            }
        }

        //排序、划分数据
        List<String> instanceIds = new ArrayList<>();
        instanceIds.addAll(instanceMap.keySet());
        Collections.sort(instanceIds);

        List<String> tokens = new ArrayList<>();
        tokens.addAll(tokenSet);
        Collections.sort(tokens);

        manifestBuilder
                .addAllInstanceIds(instanceIds)
                .addAllTokens(tokens);

        DataFormat.Manifest manifest = manifestBuilder.build();

        Map<String, Integer> token2type = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++)
            token2type.put(tokens.get(i), i);

        List<DataFormat.DataChunkPart> parts = new ArrayList<>();
        DataFormat.DataChunkPart.Builder partBuilder = null;

        for (int i = 0; i < instanceIds.size(); i++) {
            if (i % partSize == 0) {
                if (partBuilder != null)
                    parts.add(partBuilder.build());
                partBuilder = DataFormat.DataChunkPart.newBuilder();
            }
            String instanceId = instanceIds.get(i);

            List<DataFormat.Token> tokenList = new ArrayList<>();
            for (Map.Entry<String, DataFormat.Token.Builder> entry : instanceTokenMap.get(instanceId).entrySet()) {
                entry.getValue().setType(token2type.get(entry.getKey()));
                tokenList.add(entry.getValue().build());
            }

            tokenList.sort(Comparator.comparingInt(DataFormat.Token::getCount).reversed());

            DataFormat.Instance.Builder instanceBuilder = instanceMap.get(instanceId);
            instanceBuilder.addAllTokens(tokenList);

            partBuilder.addInstances(instanceBuilder);
        }
        if (partBuilder != null)
            parts.add(partBuilder.build());

        //写入文件

        try (FileOutputStream fileOutputStream = new FileOutputStream(manifestFile)) {
            manifest.writeTo(fileOutputStream);
        }

        for (int i = 0; i < parts.size(); i++) {
            DataFormat.DataChunkPart part = parts.get(i);
            File partFile = new File(getDataChunkPartPath(dataChunk.getManifestId(), i));
            try (FileOutputStream fileOutputStream = new FileOutputStream(partFile)) {
                part.writeTo(fileOutputStream);
            }
        }

        dataChunk.setStatus(TaskStatus.Finished)
                .setTotalInstances(instanceIds.size())
                .setTotalTypes(tokens.size())
                .setTotalTokens(tokenCnt);

        dataChunkStorage.save(dataChunk);
        progressMap.remove(dataChunkId);
    }

    public long getProgress(long dataChunkId) {
        DataChunk dataChunk = dataChunkStorage.findOne(dataChunkId);
        if (dataChunk == null)
            throw new BadRequestException("不存在的数据块");
        switch (dataChunk.getStatus()) {
            case Stopped:
                throw new BadRequestException("任务未运行");
            case Finished:
                throw new BadRequestException("任务已完成");
            case Processing: {
                Long progress = progressMap.get(dataChunkId);
                if (progress == null)
                    throw new BadRequestException("任务未在运行中");
                return progress;
            }
            default:
                throw new IllegalStateException("未知状态");
        }
    }
}
