package com.yagay.ypower.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class FixRecommendation {
    public String title;
    public String whyDetected;
    public String projectExplanation;
    public String whyAttributed;
    public String repair;
    public String source;

    public FixRecommendation(
            String title,
            String whyDetected,
            String projectExplanation,
            String whyAttributed,
            String repair,
            String source
    ) {
        this.title = title;
        this.whyDetected = whyDetected;
        this.projectExplanation = projectExplanation;
        this.whyAttributed = whyAttributed;
        this.repair = repair;
        this.source = source;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("title", title);
            o.put("whyDetected", whyDetected);
            o.put("projectExplanation", projectExplanation);
            o.put("whyAttributed", whyAttributed);
            o.put("repair", repair);
            o.put("source", source);
        } catch (JSONException ignored) {
        }
        return o;
    }
}
