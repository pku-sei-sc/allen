package cn.edu.pku.sei.sc.allen.model;

import cn.edu.pku.sei.sc.allen.util.CompareNVP2;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by dell on 2017/11/26.
 */
public class TopicWord2 {

    public static final int TOPIC_WORD_LIMIT = 10;

    String uuid;

    int task_id;

    public int topicNumber;
    public String wordName;
    public String tableName;
    public String valueName;

    List<Map<String, Double>> topics = new ArrayList<>();
    List<Map<String, Double>> values = new ArrayList<>();

    public TopicWord2() {
    }

    public void setTopicWord2(int taskID, int numTopic, String wordName, String tableName, String uid, String vName) {
        task_id = taskID;
        this.topicNumber = numTopic;
        this.wordName = wordName;
        this.tableName = tableName;
        this.uuid = uid;
        this.valueName = vName;
    }

    public void addTopicWordDis(Map<String, Double> wordMap) {
        topics.add(wordMap);
    }

    public void addTopicValues(Map<String, Double> valueMap) {
        values.add(valueMap);
    }


    public List<Map<String, Double>> getTopics() {
        return topics;
    }

    public void setTopics(List<Map<String, Double>> topics) {
        this.topics = topics;
    }
    public void setValues(List<Map<String, Double>> values) { this.values = values; }

    public List<List<NameValuePair>> getTopicList(){
        // Using topics to generate topiclists where value goes down
        List<List<NameValuePair>> topiclists = new ArrayList<>();
        CompareNVP2 comp = new CompareNVP2();
        for (int i = 0; i < topics.size(); i++) {
            PriorityQueue<NameValuePair> q = new PriorityQueue<>(comp);
            for (Map.Entry<String,Double> entry:topics.get(i).entrySet()){
                NameValuePair nvp = new NameValuePair(entry.getKey(),entry.getValue());
                q.add(nvp);
            }
            List<NameValuePair> lnvp = new ArrayList<>();
            while (!q.isEmpty()){
                lnvp.add(q.poll());
            }
            topiclists.add(lnvp);
        }
        return topiclists;
    }

    public List<List<Double>> getValueListByTopicList(List<List<NameValuePair>> topicList){
        if (this.valueName.equals("--None--")){
            return null;
        }
        List<List<Double>> valueList = new ArrayList<>();
        for (int i = 0; i < topicList.size(); i++) {
            List<Double> vList = new ArrayList<>();
            for (NameValuePair nvp : topicList.get(i)){
                vList.add(values.get(i).get(nvp.getName()));
            }
            valueList.add(vList);
        }
        return valueList;
    }

    public void selfPrint() {
        int len = topics.size();
        for (int i = 0; i < len; ++i) {
            selfPrint(i);
        }
    }

    public void selfPrint(int T) {
        Map<String, Double> map = topics.get(T);
        Formatter out = new Formatter(new StringBuilder(), Locale.CHINA);
        out.format("%s\n", wordName);
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            out.format("%s:%.3f ", entry.getKey(), entry.getValue());
        }
        System.out.println(out);
    }

    public void selfPrint(String filename) {
        Formatter out = null;
        try {
            out = new Formatter(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        out.format("%s in %s with %d topics %n",wordName,tableName,topicNumber);
        List<List<NameValuePair>> llnvp = getTopicList();
        for (int i = 0; i < llnvp.size(); i++) {
            out.format("Topic %d - ",i);
            List<NameValuePair> lnvp = llnvp.get(i);
            List<List<Double>> lldouble = null;
            if (!this.valueName.equals("--None--")) {
                lldouble = getValueListByTopicList(llnvp);
            }
            for (int j = 0; j < lnvp.size(); j++) {
                if (!this.valueName.equals("--None--")) {
                    out.format("%s:%.3f-%.3f ",lnvp.get(j).getName(),lnvp.get(j).getValue(),lldouble.get(i).get(j));
                } else {
                    out.format("%s:%.3f ",lnvp.get(j).getName(),lnvp.get(j).getValue());
                }
            }
            out.format("%n");
        }
        out.flush();
    }

//    public void selfPrintWithValue(String filename) {
//        if (values==null){
//            selfPrint(filename);
//            return;
//        }
//        Formatter out = null;
//        try {
//            out = new Formatter(filename);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        out.format("%s in %s with %d topics %n",wordName,tableName,topicNumber);
//        List<List<NameValuePair>> llnvp = getTopicList();
//        for (int i = 0; i < llnvp.size(); i++) {
//            out.format("Topic %d - ",i);
//            List<NameValuePair> lnvp = llnvp.get(i);
//            for (int j = 0; j < lnvp.size(); j++) {
//                out.format("%s:%.3f-%.3f ",lnvp.get(j).getName(),lnvp.get(j).getValue(),lldouble.get(i).get(j));
//            }
//            out.format("%n");
//        }
//        out.flush();
//    }
}