package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.service.ADService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Abnormal Detection
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/ad")
public class ADController {

    @Autowired
    private ADService adService;

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public void doAbnormalDetection(@RequestParam String manifestID1,
                                    @RequestParam String manifestID2,
                                    @RequestParam String method,
                                    @RequestParam(required = false, defaultValue = "100") int top) throws IOException {
        adService.adRun(manifestID1, manifestID2, method, top);
    }


    @RequestMapping(value = "/{taskId}", method = RequestMethod.GET)
    public void getProgress(@PathVariable long taskId) {

    }

}
