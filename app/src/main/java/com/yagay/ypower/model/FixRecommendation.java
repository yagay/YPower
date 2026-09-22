package com.yagay.ypower.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class FixRecommendation {
    public String title;
    public String detail;
    public String source;
    public boolean ypowerCanHelp;

    public FixRecommendation(String title, String detail, String source, boolean ypowerCanHelp) {
        this.title = title;
        this.detail = detail;
        this.source = source;
        this.ypowerCanHelp = ypowerCanHelp;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("title", title);
            o.put("detail", detail);
            o.put("source", source);
            o.put("ypowerCanHelp", ypowerCanHelp);
        } catch (JSONException ignored) {
        }
        return o;
    }
}
