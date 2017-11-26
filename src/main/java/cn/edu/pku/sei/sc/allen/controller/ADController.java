package cn.edu.pku.sei.sc.allen.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abnormal Detection
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/ad")
public class ADController {

    @RequestMapping(value = "", method = RequestMethod.POST)
    public void doAbnormalDetection() {

    }

    @RequestMapping(value = "/{taskId}", method = RequestMethod.GET)
    public void getProgress(@PathVariable long taskId) {

    }

}
