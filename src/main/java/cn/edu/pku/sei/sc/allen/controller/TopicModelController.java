package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.model.TrainingTask;
import cn.edu.pku.sei.sc.allen.service.TopicModelService;
import cn.edu.pku.sei.sc.allen.storage.TrainingTaskStorage;
import cn.edu.pku.sei.sc.allen.view.TrainingProgress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/topics-model")
public class TopicModelController {

    @Autowired
    private TopicModelService topicModelService;

    @Autowired
    private TrainingTaskStorage trainingTaskStorage;

    @RequestMapping(value = "/training", method = RequestMethod.POST)
    public TrainingTask training(@RequestParam List<Long> dataChunkIds,
                                 @RequestParam(required = false) String ruleFile,
                                 @RequestParam int totalTopics,
                                 @RequestParam(required = false, defaultValue = "50") double alphaSum,
                                 @RequestParam(required = false, defaultValue = "200") double betaSum,
                                 @RequestParam(required = false, defaultValue = "0") long randomSeed,
                                 @RequestParam(required = false, defaultValue = "1000") int numIteration,
                                 @RequestParam(required = false, defaultValue = "0") int showTopicsInterval,
                                 @RequestParam(required = false, defaultValue = "7") int showTopicsNum) {
        if (alphaSum <= 0)
            alphaSum = 50;
        if (betaSum <= 0)
            betaSum = 200;
        if (randomSeed == 0)
            randomSeed = System.nanoTime();
        return topicModelService.createTrainingTask(dataChunkIds, ruleFile, totalTopics, alphaSum, betaSum, randomSeed,
                numIteration, showTopicsInterval, showTopicsNum);
    }

    @RequestMapping(value = "/training", method = RequestMethod.GET)
    public List<TrainingTask> getAllTask() {
        return trainingTaskStorage.findAll();
    }

    @RequestMapping(value = "/training/{trainingTaskId}/start", method = RequestMethod.POST)
    public void startTraining(@PathVariable long trainingTaskId,
                              @RequestParam(required = false, defaultValue = "false") boolean forced) throws IOException {
        topicModelService.startTraining(trainingTaskId, forced);
    }

    @RequestMapping(value = "/{trainingTaskId}", method = RequestMethod.GET)
    public TrainingProgress getProgress(@PathVariable long trainingTaskId) {
        return topicModelService.getProgress(trainingTaskId);
    }

    @RequestMapping(value = "/inference", method = RequestMethod.POST)
    public void inference() {

    }

}
