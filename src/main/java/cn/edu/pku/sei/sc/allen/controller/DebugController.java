package cn.edu.pku.sei.sc.allen.controller;

import cn.edu.pku.sei.sc.allen.model.DataChunkMeta;
import cn.edu.pku.sei.sc.allen.model.TaskStatus;
import cn.edu.pku.sei.sc.allen.model.data.TestMsg;
import cn.edu.pku.sei.sc.allen.storage.DataChunkMetaStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by dell on 2017/11/26.
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    @Autowired
    private DataChunkMetaStorage dataChunkMetaStorage;

    @RequestMapping(value = "/test1", method = RequestMethod.POST)
    public DataChunkMeta test1() {
        DataChunkMeta dataChunkMeta = new DataChunkMeta();
        dataChunkMeta.setDataSourceId(1)
                .setSql("select 1")
                .setIdName("id")
                .setTokenName("word")
                .setStatus(TaskStatus.Stopped);
        return dataChunkMetaStorage.save(dataChunkMeta);
    }

    @RequestMapping(value = "/test2", method = RequestMethod.GET)
    public DataChunkMeta test2(@RequestParam long id) {
        return dataChunkMetaStorage.findOne(id);
    }

    @RequestMapping(value = "/test3", method = RequestMethod.GET)
    public List<DataChunkMeta> test3() {
        return dataChunkMetaStorage.findAll();
    }

    public static void test4() throws IOException {
        TestMsg.Test.Builder builder = TestMsg.Test.newBuilder();
        builder.setId(1);
        for (int i = 0; i < 10; i++) {
            builder.addValues(i);
            builder.addDoubles(i);
        }


        TestMsg.Test test = builder.build();
        System.out.println(test.getSerializedSize());
        File file = new File("data/1.dat");
        FileOutputStream fileOutputStream = new FileOutputStream(file);
//        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(fileOutputStream);
        test.writeTo(fileOutputStream);
//        codedOutputStream.flush();
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    public static void test5() throws IOException {
        File file = new File("data/1.dat");
        FileInputStream fileInputStream = new FileInputStream(file);
        TestMsg.Test test = TestMsg.Test.parseFrom(fileInputStream);
        System.out.println(test);
        System.out.println(test.getName());
    }

    private static int MAXN = 10_000_000;

    public static void test6() {

        float[] vals = new float[MAXN];
        for (int i = 0; i < MAXN; i++)
            vals[i] = i;

        //burnIn
        for (int i = 0; i < 100; i++) {
            float tmp = 0;
            for (int j = 0; j < MAXN; j++) {
                tmp += vals[j] * tmp;
            }
        }

        //speed test
        for (int k = 0; k < 10; k++) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                float tmp = 0;
                for (int j = 0; j < MAXN; j++) {
                    tmp += vals[j] / tmp;
                }
            }
            System.out.println("Cost:" + (System.currentTimeMillis() - start) + "ms.");
        }

        //memory test
        for (int i = 0; i < MAXN; i++) {
            vals = new float[MAXN];
            for (int j = 0; j < MAXN; j++) {
                vals[i] = j;
            }
        }

    }

    public static void test7() {

        double[] vals = new double[MAXN];
        for (int i = 0; i < MAXN; i++)
            vals[i] = i;

        //burnIn
        for (int i = 0; i < 100; i++) {
            double tmp = 0;
            for (int j = 0; j < MAXN; j++) {
                tmp += vals[j] * tmp;
            }
        }

        //speed test
        for (int k = 0; k < 10; k++) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                double tmp = 0;
                for (int j = 0; j < MAXN; j++) {
                    tmp += vals[j] / tmp;
                }
            }
            System.out.println("Cost:" + (System.currentTimeMillis() - start) + "ms.");
        }

        //memory test
        for (int i = 0; i < MAXN; i++) {
            vals = new double[MAXN];
            for (int j = 0; j < MAXN; j++) {
                vals[i] = j;
            }
        }

    }

    public static void main(String[] args) throws IOException {
//        test4();
//        test5();
        test6();
    }


}
