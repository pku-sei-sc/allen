package cn.edu.pku.sei.sc.allen.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/output")
public class OutputDataController {

    @RequestMapping(value = "/ad", method = RequestMethod.GET)
    public void getADResult() {

    }

    @RequestMapping(value = "/tm", method = RequestMethod.GET)
    public void getTMResult() {

    }

}
