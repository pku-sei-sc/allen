package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.model.DataChunk;
import cn.edu.pku.sei.sc.allen.storage.DataChunkStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    @Autowired
    private DataChunkStorage dataChunkStorage;

    @RequestMapping(value = "/test1", method = RequestMethod.POST)
    public DataChunk test1() {
        DataChunk dataChunk = new DataChunk().setCreateTime(System.currentTimeMillis());
        return dataChunkStorage.save(dataChunk);
    }

    @RequestMapping(value = "/test2", method = RequestMethod.GET)
    public DataChunk test2(@RequestParam long id) {
        return dataChunkStorage.findOne(id);
    }

    @RequestMapping(value = "/test3", method = RequestMethod.GET)
    public List<DataChunk> test3() {
        return dataChunkStorage.findAll();
    }


}
