package cn.edu.pku.sei.sc.allen.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/topics-model")
public class TopicsModelController {

    @RequestMapping(value = "/training", method = RequestMethod.POST)
    public void training() {

    }

    @RequestMapping(value = "/inference", method = RequestMethod.POST)
    public void inference() {

    }

    @RequestMapping(value = "/{taskId}", method = RequestMethod.GET)
    public void getProgress(@PathVariable long taskId) {

    }

}
