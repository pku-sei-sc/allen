package cn.edu.pku.sei.sc.allen.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dell on 2017/11/30.
 */
public class Rule {

    public Map<Long, List<String>> stopWords = new HashMap<>();

    public Map<Long, Map<String, List<String>>> synonyms = new HashMap<>();

    private Map<Long, Map<String, String>> dictMap = new HashMap<>();

    public List<String> getStopWords(long dataChunkId) {
        if (stopWords.containsKey(dataChunkId))
            return stopWords.get(dataChunkId);
        else
            return new ArrayList<>();
    }

    public String getSynonym(long dataChunkId, String token) {
        Map<String, String> dict = dictMap.get(dataChunkId);
        if (!dictMap.containsKey(dataChunkId)) {
            dict = new HashMap<>();
            dictMap.put(dataChunkId, dict);
            Map<String, List<String>> invertMap = synonyms.get(dataChunkId);
            if (invertMap != null) {
                for (Map.Entry<String, List<String>> entry : invertMap.entrySet()) {
                    List<String> froms = entry.getValue();
                    for (String from : froms) {
                        if (dict.put(from, entry.getKey()) != null)
                            throw new IllegalArgumentException("数据id:" + dataChunkId + "规则中同义词[" + entry.getKey() +"] 部分存在歧义 [" + from +"] 多次出现");
                    }
                }
            }
        }
        return dict.getOrDefault(token, token);
    }
}
