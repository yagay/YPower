package com.yagay.ypower.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticFinding {
    public String id;
    public String category;
    public String title;
    public DiagnosticStatus status;
    public String summary;
    public String detail;
    public int correlationScore;
    // 0 = not attributed, 1 = primary cause candidate, 2 = secondary cause candidate.
    public int attributionRank;
    public final List<String> evidence = new ArrayList<>();
    public final List<FixRecommendation> recommendations = new ArrayList<>();

    public DiagnosticFinding(String id, String category, String title, DiagnosticStatus status, String summary) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.status = status;
        this.summary = summary;
        this.detail = summary;
    }

    public DiagnosticFinding detail(String value) {
        this.detail = value;
        return this;
    }

    public DiagnosticFinding evidence(String value) {
        if (value != null && !value.isBlank()) evidence.add(value);
        return this;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("category", category);
            o.put("title", title);
            o.put("status", status.code);
            o.put("summary", summary);
            o.put("detail", detail);
            o.put("correlationScore", correlationScore);
            o.put("attributionRank", attributionRank);
            JSONArray arr = new JSONArray();
            for (String e : evidence) arr.put(e);
            o.put("evidence", arr);

            JSONArray recs = new JSONArray();
            for (FixRecommendation recommendation : recommendations) {
                recs.put(recommendation.toJson());
            }
            o.put("recommendations", recs);
        } catch (JSONException ignored) {
        }
        return o;
    }
}
