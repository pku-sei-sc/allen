package cn.edu.pku.sei.sc.allen.util;

import cn.edu.pku.sei.sc.allen.model.NameValuePair;

import java.util.Comparator;

/**
 * Created by dell on 2017/11/26.
 */
public class CompareNVP2 implements Comparator<NameValuePair> {

    //从大到小
    @Override
    public int compare(NameValuePair o1, NameValuePair o2) {
        if(o1.getValue() - o2.getValue() > 0)	return -1;
        else return 1;
    }
}
