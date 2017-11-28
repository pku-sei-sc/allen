package cn.edu.pku.sei.sc.allen.model;

import cc.mallet.types.Alphabet;
import cn.edu.pku.sei.sc.allen.model.data.DataFormat;

import java.util.List;

/**
 * Created by dell on 2017/11/28.
 */
public class DataChunk {

    private DataChunkMeta meta;

    private Alphabet tokenAlphabet; //词表

    private Alphabet instanceAlphabet; //实例表

    private List<DataFormat.Instance> instances; //实例数据

    public boolean hasValue() {
        return meta.getValueName() != null;
    }

    public DataChunkMeta getMeta() {
        return meta;
    }

    public DataChunk setMeta(DataChunkMeta meta) {
        this.meta = meta;
        return this;
    }

    public Alphabet getTokenAlphabet() {
        return tokenAlphabet;
    }

    public DataChunk setTokenAlphabet(Alphabet tokenAlphabet) {
        this.tokenAlphabet = tokenAlphabet;
        return this;
    }

    public Alphabet getInstanceAlphabet() {
        return instanceAlphabet;
    }

    public DataChunk setInstanceAlphabet(Alphabet instanceAlphabet) {
        this.instanceAlphabet = instanceAlphabet;
        return this;
    }

    public List<DataFormat.Instance> getInstances() {
        return instances;
    }

    public DataChunk setInstances(List<DataFormat.Instance> instances) {
        this.instances = instances;
        return this;
    }
}
