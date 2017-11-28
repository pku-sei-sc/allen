package cn.edu.pku.sei.sc.allen.storage;

import cn.edu.pku.sei.sc.allen.model.TrainingTask;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by dell on 2017/11/28.
 */
@Repository
public interface TrainingTaskStorage extends CrudRepository<TrainingTask, Long> {

    List<TrainingTask> findAll();

}
