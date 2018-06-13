package cn.edu.pku.sei.sc.allen.storage;

import cn.edu.pku.sei.sc.allen.model.DataChunkMetaPac;
import cn.edu.pku.sei.sc.allen.model.InferenceTaskPac;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by 刘少钦 on 2018/4/23.
 */
@Repository
public interface InferenceTaskPacStorage extends CrudRepository<InferenceTaskPac, Long> {
    List<InferenceTaskPac> findAll();

    List<InferenceTaskPac> findAll(Iterable<Long> ids);
}