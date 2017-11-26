package cn.edu.pku.sei.sc.allen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Shawn on 2017/11/26.
 */
@SpringBootApplication
@RestController
public class Application {

    @RequestMapping(method = RequestMethod.GET, value = "/")
    public String hello(HttpServletResponse response) throws IOException, InterruptedException {
        return "Hello world!";
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
