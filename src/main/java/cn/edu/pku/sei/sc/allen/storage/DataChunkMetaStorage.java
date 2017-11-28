package cn.edu.pku.sei.sc.allen.storage;

import cn.edu.pku.sei.sc.allen.model.DataChunkMeta;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by dell on 2017/11/26.
 */
@Repository
public interface DataChunkMetaStorage extends CrudRepository<DataChunkMeta, Long> {
    List<DataChunkMeta> findAll();

    List<DataChunkMeta> findAll(Iterable<Long> ids);
}
