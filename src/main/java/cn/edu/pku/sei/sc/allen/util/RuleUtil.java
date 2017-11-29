package cn.edu.pku.sei.sc.allen.util;

import cn.edu.pku.sei.sc.allen.model.Rule;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Created by dell on 2017/11/30.
 */
public class RuleUtil {

    public static Rule loadRule(String fileName) throws FileNotFoundException {
        if (fileName == null) return new Rule();
        Yaml yaml = new Yaml();
        return yaml.loadAs(new FileInputStream(fileName), Rule.class);
    }

    public static void main(String[] args) throws FileNotFoundException {
        Rule rule = loadRule("rule/rule.yml");
        System.out.println(rule.getStopWords(1));
        System.out.println(rule.getSynonym(1, "we"));
        System.out.println(rule.getSynonym(2, "hi"));
        System.out.println(rule);
    }

}
