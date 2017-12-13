package cn.edu.pku.sei.sc.allen.service;

import cc.mallet.topics.MVMATopicModel;
import cc.mallet.types.Alphabet;
import cn.edu.pku.sei.sc.allen.lang.BadRequestException;
import cn.edu.pku.sei.sc.allen.model.*;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import cn.edu.pku.sei.sc.allen.storage.SqlDataSourceStorage;
import cn.edu.pku.sei.sc.allen.util.JdbcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.*;
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

    private static Logger logger = LoggerFactory.getLogger(InputDataService.class);

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    @Autowired
    private SqlDataSourceStorage sqlDataSourceStorage;

    private Map<Long, Long> progressMap = new ConcurrentHashMap<>();

    private String dataPath = "data";

    private int partSize = 5000;

    //region getter setter
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
    //endregion

    @PostConstruct
    private void init() {
        if (dataPath.endsWith("/"))
            dataPath = dataPath.substring(0, dataPath.length() - 1);
        File dataDir = new File(dataPath);
        if (!dataDir.isDirectory() && !dataDir.mkdir())
            throw new IllegalStateException("无法创建data目录，请手动创建目录后重试");
    }

    public DataChunkMeta createDataChunk(long dataSourceId, List<String> sqls, String idName, String tokenName, String valueName) {
        if (!dataSourceService.testSqlDataSource(dataSourceId))
            throw new IllegalStateException("数据源连接失败");
        DataChunkMeta dataChunkMeta = new DataChunkMeta()
                .setDataSourceId(dataSourceId)
                .setSqls(sqls)
                .setIdName(idName)
                .setTokenName(tokenName)
                .setValueName(valueName)
                .setStatus(TaskStatus.Stopped);
        return dataChunkMetaStorage.save(dataChunkMeta);
    }

    private String getManifestDataPath(String manifestId) {
        return dataPath + "/" + manifestId + "/manifest.dat";
    }

    private String getDataChunkPartPath(String manifestId, int partNo) {
        return dataPath + "/" + manifestId + "/part" + partNo + ".dat";
    }

    @Async
    public void inputDataChunk(long dataChunkId, boolean forced) throws SQLException, IOException {
        DataChunkMeta dataChunkMeta = dataChunkMetaStorage.findOne(dataChunkId);
        if (dataChunkMeta == null)
            throw new IllegalArgumentException("不存在的数据块");

        if (!dataSourceService.testSqlDataSource(dataChunkMeta.getDataSourceId()))
            throw new IllegalStateException("无法连接到数据源");

        SqlDataSource sqlDataSource = sqlDataSourceStorage.findOne(dataChunkMeta.getDataSourceId());

        if (dataChunkMeta.getStatus() != TaskStatus.Stopped && !forced)
            throw new IllegalStateException("无法开始一个非停止状态的任务，尝试强制开始");

        long tokenCnt = 0;
        if (progressMap.putIfAbsent(dataChunkId, tokenCnt) != null)
            throw new IllegalStateException("无法重复执行数据导入任务");
        try {
            logger.info("Start import data id:{}.", dataChunkId);

            dataChunkMeta.setManifestId(UUID.randomUUID().toString());
            File manifestFile = new File(getManifestDataPath(dataChunkMeta.getManifestId()));

            while (manifestFile.isFile()) {
                dataChunkMeta.setManifestId(UUID.randomUUID().toString());
                manifestFile = new File(getManifestDataPath(dataChunkMeta.getManifestId()));
            }

            if (!manifestFile.getParentFile().isDirectory() && !manifestFile.getParentFile().mkdir()) {
                throw new IllegalStateException("无法创建文件子目录");
            }

            dataChunkMeta.setStatus(TaskStatus.Processing);
            dataChunkMetaStorage.save(dataChunkMeta);

            //开始导入数据，并写入文件

            DataFormat.Manifest.Builder manifestBuilder = DataFormat.Manifest.newBuilder();

            manifestBuilder.getDataChunkMetaBuilder().getDataSourceBuilder()
                    .setDriverClassName(sqlDataSource.getDriverClassName())
                    .setUrl(sqlDataSource.getUrl())
                    .setUsername(sqlDataSource.getUsername())
                    .setPassword(sqlDataSource.getPassword());

            manifestBuilder.getDataChunkMetaBuilder()
                    .addAllSqls(dataChunkMeta.getSqls())
                    .setIdName(dataChunkMeta.getIdName())
                    .setWordName(dataChunkMeta.getTokenName());

            if (dataChunkMeta.getValueName() != null) {
                manifestBuilder.getDataChunkMetaBuilder().setValueName(dataChunkMeta.getValueName());
            }

            manifestBuilder.setPartSize(partSize);

            //执行数据库语句并保存数据
            Map<String, DataFormat.Instance.Builder> instanceMap = new HashMap<>();
            Map<String, Map<String, DataFormat.Token.Builder>> instanceTokenMap = new HashMap<>();
            Set<String> tokenSet = new HashSet <>();

            try (Connection connection = JdbcUtil.getConnection(sqlDataSource)) {
                long lastTime = System.currentTimeMillis();
                long lastRow = 0;
                long rowCnt = 0;
                long lastToken = 0;

                for (String sql : dataChunkMeta.getSqls()) {
                    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                        if (sqlDataSource.getUrl().toLowerCase().contains("mysql")) //mysql特殊处理
                            preparedStatement.setFetchSize(Integer.MIN_VALUE);
                        try (ResultSet resultSet = preparedStatement.executeQuery()) {
                            int idColumn = resultSet.findColumn(dataChunkMeta.getIdName());
                            int tokenColumn = resultSet.findColumn(dataChunkMeta.getTokenName());
                            int valueColumn = dataChunkMeta.getValueName() == null ?
                                    -1 : resultSet.findColumn(dataChunkMeta.getValueName());

                            while (resultSet.next()) {
                                rowCnt++;
                                String instanceId = resultSet.getString(idColumn);
                                String token = resultSet.getString(tokenColumn);
                                if (instanceId == null || token == null) continue;

                                instanceId = instanceId.trim();
                                token = token.trim();
                                if (instanceId.length() == 0 || token.length() == 0)
                                    continue;

                                Float value = null;

                                if (dataChunkMeta.getValueName() != null) {
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
                                long elapsedMillis = System.currentTimeMillis() - lastTime;
                                if (tokenCnt % 200_000 == 0 || elapsedMillis >= 2_000) {
                                    if (rowCnt != 0 && elapsedMillis != 0)
                                        logger.info("Import data id:{}\t read:{}/{}\t quality:{}%\t speed: {} token/s, {} row/s",
                                                dataChunkId, tokenCnt, rowCnt, String.format("%.2f", ((double) tokenCnt) * 100 / rowCnt),
                                                (tokenCnt - lastToken) * 1000 / elapsedMillis, (rowCnt - lastRow) * 1000 / elapsedMillis);
                                    lastRow = rowCnt;
                                    lastToken = tokenCnt;
                                    lastTime += elapsedMillis;
                                }
                            }
                        }
                    }
                }
                long elapsedMillis = System.currentTimeMillis() - lastTime;
                if (rowCnt - lastRow > 0 && tokenCnt - lastToken > 0 && elapsedMillis > 0) {
                    logger.info("Import data id:{}\t read:{}/{}\t quality:{}%\t speed: {} token/s, {} row/s",
                            dataChunkId, tokenCnt, rowCnt, String.format("%.2f", ((double) tokenCnt) * 100 / rowCnt),
                            (tokenCnt - lastToken) * 1000 / elapsedMillis, (rowCnt - lastRow) * 1000 / elapsedMillis);
                }
            }

            logger.info("Import data id:{} read finished! Save to file...", dataChunkId);

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
                File partFile = new File(getDataChunkPartPath(dataChunkMeta.getManifestId(), i));
                try (FileOutputStream fileOutputStream = new FileOutputStream(partFile)) {
                    part.writeTo(fileOutputStream);
                }
            }

            dataChunkMeta.setStatus(TaskStatus.Finished)
                    .setTotalInstances(instanceIds.size())
                    .setTotalTypes(tokens.size())
                    .setTotalTokens(tokenCnt);

            dataChunkMetaStorage.save(dataChunkMeta);
            logger.info("Import data id:{} finished!", dataChunkId);
        } finally {
            progressMap.remove(dataChunkId);
        }
    }

    public long getProgress(long dataChunkId) {
        DataChunkMeta dataChunkMeta = dataChunkMetaStorage.findOne(dataChunkId);
        if (dataChunkMeta == null)
            throw new BadRequestException("不存在的数据块");
        switch (dataChunkMeta.getStatus()) {
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

    public DataChunk loadDataChunk(DataChunkMeta dataChunkMeta) throws IOException {
        //开始读取数据文件

        File manifestFile = new File(getManifestDataPath(dataChunkMeta.getManifestId()));
        if (!manifestFile.isFile())
            throw new BadRequestException("找不到数据块目录文件");

        DataFormat.Manifest manifest;
        try (FileInputStream fileInputStream = new FileInputStream(manifestFile)) {
            manifest = DataFormat.Manifest.parseFrom(fileInputStream);
        }

        int totalInstances = manifest.getInstanceIdsCount();

        List<DataFormat.Instance> instances = new ArrayList<>();

        for (int i = 0; i * manifest.getPartSize() < totalInstances; i++) {
            File partFile = new File(getDataChunkPartPath(dataChunkMeta.getManifestId(), i));
            if (!partFile.isFile())
                throw new BadRequestException("丢失数据块文件：" + partFile.getPath());

            try (FileInputStream fileInputStream = new FileInputStream(partFile)) {
                DataFormat.DataChunkPart dataChunkPart = DataFormat.DataChunkPart.parseFrom(fileInputStream);
                instances.addAll(dataChunkPart.getInstancesList());
            }
        }

        //开始构建数据块

        DataChunk dataChunk = new DataChunk()
                .setMeta(dataChunkMeta)
                .setInstanceAlphabet(new Alphabet(String.class))
                .setTokenAlphabet(new Alphabet(String.class))
                .setInstances(instances);

        dataChunk.getInstanceAlphabet().lookupIndices(manifest.getInstanceIdsList().toArray(), true);
        dataChunk.getTokenAlphabet().lookupIndices(manifest.getTokensList().toArray(), true);

        return dataChunk;
    }

    public DataChunk loadDataChunk(long dataChunkId) throws IOException {
        DataChunkMeta dataChunkMeta = dataChunkMetaStorage.findOne(dataChunkId);
        if (dataChunkMeta == null || dataChunkMeta.getStatus() != TaskStatus.Finished)
            throw new BadRequestException("数据块不存在或还没有完成导入");

        return loadDataChunk(dataChunkMeta);
    }
}
