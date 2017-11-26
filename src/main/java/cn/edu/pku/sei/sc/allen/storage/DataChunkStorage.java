package cn.edu.pku.sei.sc.allen.storage;

import cn.edu.pku.sei.sc.allen.model.DataChunk;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by dell on 2017/11/26.
 */
@Repository
public interface DataChunkStorage extends CrudRepository<DataChunk, Long> {
    List<DataChunk> findAll();
}
