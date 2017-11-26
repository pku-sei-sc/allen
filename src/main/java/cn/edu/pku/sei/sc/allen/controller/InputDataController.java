package cn.edu.pku.sei.sc.allen.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/input")
public class InputDataController {

    @RequestMapping(value = "/data", method = RequestMethod.POST)
    public void inputData(@RequestParam String sql,
                                  @RequestParam String instanceId,
                                  @RequestParam String wordName,
                                  @RequestParam(required = false) String valueName) {

    }

    public void getProgress() {

    }

}
