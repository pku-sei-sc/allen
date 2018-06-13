package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.model.InferenceTask;
import cn.edu.pku.sei.sc.allen.model.InferenceTaskPac;
import cn.edu.pku.sei.sc.allen.model.TrainingTask;
import cn.edu.pku.sei.sc.allen.service.ADService;
import cn.edu.pku.sei.sc.allen.service.TopicModelService;
import cn.edu.pku.sei.sc.allen.storage.InferenceTaskPacStorage;
import cn.edu.pku.sei.sc.allen.storage.InferenceTaskStorage;
import cn.edu.pku.sei.sc.allen.storage.TrainingTaskStorage;
import cn.edu.pku.sei.sc.allen.view.TrainingProgress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/topics-model")
public class TopicModelController {

    @Autowired
    private TopicModelService topicModelService;

    @Autowired
    private ADService adService;

    @Autowired
    private TrainingTaskStorage trainingTaskStorage;

    @Autowired
    private InferenceTaskStorage inferenceTaskStorage;

    @Autowired
    private InferenceTaskPacStorage inferenceTaskPacStorage;

    @RequestMapping(value = "/mapping", method = RequestMethod.POST)
    public boolean mappingDection(@RequestParam List<Long> dataChunkIds,
                                  @RequestParam String modelManifest,
                                  @RequestParam String ruleFile) throws IOException {
        adService.match(modelManifest, dataChunkIds, ruleFile);
        System.out.println("ad");
        return true;
    }
    @RequestMapping(value = "/training", method = RequestMethod.POST)
    public TrainingTask training(@RequestParam List<Long> dataChunkIds,
                                 @RequestParam(required = false) String ruleFile,
                                 @RequestParam int totalTopics,
                                 @RequestParam(required = false, defaultValue = "50") double alphaSum,
                                 @RequestParam(required = false, defaultValue = "200") double betaSum,
                                 @RequestParam(required = false, defaultValue = "0") long randomSeed,
                                 @RequestParam(required = false, defaultValue = "1000") int numIteration,
                                 @RequestParam(required = false, defaultValue = "100") int showTopicsInterval,
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
                              @RequestParam(required = false, defaultValue = "false") boolean forced) throws IOException, ExecutionException, InterruptedException {
        topicModelService.startTraining(trainingTaskId, forced);
    }

    @RequestMapping(value = "/training/{trainingTaskId}/delete", method = RequestMethod.POST)
    public void deleteTraining(@PathVariable long trainingTaskId) throws IOException, ExecutionException, InterruptedException {
        if(trainingTaskStorage.exists(trainingTaskId))
            trainingTaskStorage.delete(trainingTaskId);
    }

    @RequestMapping(value = "/{trainingTaskId}", method = RequestMethod.GET)
    public TrainingProgress getProgress(@PathVariable long trainingTaskId) {
        return topicModelService.getProgress(trainingTaskId);
    }

    @RequestMapping(value = "/inference", method = RequestMethod.POST)
    public InferenceTask inference(@RequestParam long dataChunkId,
                                   @RequestParam(required = false) String ruleFile,
                                   @RequestParam String modelManifest,
                                   @RequestParam(required = false, defaultValue = "0") int language,
                                   @RequestParam(required = false, defaultValue = "0") long randomSeed,
                                   @RequestParam(required = false, defaultValue = "100") int numIterations,
                                   @RequestParam(required = false, defaultValue = "40") int burnIn,
                                   @RequestParam(required = false, defaultValue = "5") int thinning) {
        if (randomSeed == 0)
            randomSeed = System.nanoTime();
        return topicModelService.createInferenceTask(dataChunkId, ruleFile, modelManifest, language, randomSeed, numIterations,
                burnIn, thinning);
    }

    @RequestMapping(value = "/inference/{inferenceTaskId}/start", method = RequestMethod.POST)
    public void startInference(@PathVariable long inferenceTaskId,
                               @RequestParam(required = false, defaultValue = "false") boolean forced) throws IOException, ExecutionException, InterruptedException {
        topicModelService.startInference(inferenceTaskId, forced);
    }

    @RequestMapping(value = "/inference", method = RequestMethod.GET)
    public List<InferenceTask> getAllInfer() {
        return inferenceTaskStorage.findAll();
    }

    @RequestMapping(value = "/inference/{inferTaskId}/delete", method = RequestMethod.POST)
    public void deleteInfer(@PathVariable long inferTaskId) throws IOException, ExecutionException, InterruptedException {
        if(inferenceTaskStorage.exists(inferTaskId))
            inferenceTaskStorage.delete(inferTaskId);
    }

    @RequestMapping(value = "/inferencePac", method = RequestMethod.POST)
    public InferenceTaskPac inferencePac(@RequestParam long dataChunkPacId,
                                         @RequestParam String modelManifest,
                                         @RequestParam String method,
                                         @RequestParam String name,
                                         @RequestParam(required = false) String ruleFile,
                                         @RequestParam(required = false, defaultValue = "0") long randomSeed,
                                         @RequestParam(required = false, defaultValue = "100") int numIterations,
                                         @RequestParam(required = false, defaultValue = "40") int burnIn,
                                         @RequestParam(required = false, defaultValue = "5") int thinning,
                                         @RequestParam(required = false, defaultValue = "100") int top) throws InterruptedException, ExecutionException, IOException {
//        InferenceTask inferenceTask1 = inference(dataChunkId1, ruleFile1, modelManifest, language1, randomSeed1, numIterations1, burnIn1, thinning1);
//        InferenceTask inferenceTask2 = inference(dataChunkId2, ruleFile2, modelManifest, language2, randomSeed2, numIterations2, burnIn2, thinning2);
//        startInference(inferenceTask1.getId(),true);
//        startInference(inferenceTask2.getId(),true);
//        adService.adRun(inferenceTask1.getManifestId(), inferenceTask2.getManifestId(), method, top);
        if (randomSeed == 0)
            randomSeed = System.nanoTime();
        return  topicModelService.creatInferenceTaskPac(dataChunkPacId, name, modelManifest, method, ruleFile,
                 randomSeed, numIterations, burnIn, thinning, top);
    }

    @RequestMapping(value = "/inferencePac/{inferTaskPacId}/delete", method = RequestMethod.POST)
    public void deleteInferPac(@PathVariable long inferTaskPacId) throws IOException, ExecutionException, InterruptedException {
        InferenceTaskPac inferenceTaskPac = inferenceTaskPacStorage.findOne(inferTaskPacId);

        if(inferenceTaskStorage.exists(inferenceTaskPac.getInferenceTaskDiagnoseId())){
            inferenceTaskStorage.delete(inferenceTaskPac.getInferenceTaskDiagnoseId());
        }

        if(inferenceTaskStorage.exists(inferenceTaskPac.getInferenceTaskMedicineId())){
            inferenceTaskStorage.delete(inferenceTaskPac.getInferenceTaskMedicineId());
        }
        if(inferenceTaskPacStorage.exists(inferTaskPacId)){
            inferenceTaskPacStorage.delete(inferTaskPacId);

        }
    }

    @RequestMapping(value = "/inferencePac", method = RequestMethod.GET)
    public List<InferenceTaskPac> getAllInferPac() {
        return inferenceTaskPacStorage.findAll();
    }

    @RequestMapping(value = "/inferencePac/{inferencePacId}/start", method = RequestMethod.POST)
    public void startInferencePac(@PathVariable long inferencePacId) throws InterruptedException, ExecutionException, IOException {
        InferenceTaskPac inferenceTaskPac = inferenceTaskPacStorage.findOne(inferencePacId);
        topicModelService.startInference(inferenceTaskPac.getInferenceTaskDiagnoseId(),true);

        topicModelService.startInference(inferenceTaskPac.getInferenceTaskMedicineId(),true);

        if(inferenceTaskStorage.findOne(inferenceTaskPac.getInferenceTaskDiagnoseId()).getManifestId() != null
                && inferenceTaskStorage.findOne(inferenceTaskPac.getInferenceTaskMedicineId()).getManifestId() !=null) {
            adService.adRun(inferenceTaskStorage.findOne(inferenceTaskPac.getInferenceTaskDiagnoseId()).getManifestId(),
                    inferenceTaskStorage.findOne(inferenceTaskPac.getInferenceTaskMedicineId()).getManifestId(),
                    inferenceTaskPac.getMethod(),
                    inferenceTaskPac.getTop());
        }

    }


}
