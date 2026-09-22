package com.yagay.ypower.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class FixRecommendation {
    public String title;
    public String detail;
    public String source;

    public FixRecommendation(String title, String detail, String source) {
        this.title = title;
        this.detail = detail;
        this.source = source;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("title", title);
            o.put("detail", detail);
            o.put("source", source);
        } catch (JSONException ignored) {
        }
        return o;
    }
}
