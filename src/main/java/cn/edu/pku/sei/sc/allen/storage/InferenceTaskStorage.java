package cn.edu.pku.sei.sc.allen.storage;

import cn.edu.pku.sei.sc.allen.model.InferenceTask;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by Shawn on 2017/12/12.
 */
@Repository
public interface InferenceTaskStorage extends CrudRepository<InferenceTask, Long> {

    List<InferenceTask> findAll();

}
