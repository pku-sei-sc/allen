package cn.edu.pku.sei.sc.allen.model;

/**
 * Created by dell on 2017/11/26.
 */
public class NameValuePair {

    public String name;
    public double value;

    public NameValuePair() {
    }

    public NameValuePair(String s, double d) {
        name = s;
        value = d;
    }

    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        value = v;
    }

    @Override
    public String toString() {
        return "(" + name + "," + value + ")";
    }
}
