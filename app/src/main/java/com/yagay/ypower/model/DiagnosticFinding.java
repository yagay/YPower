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

    // Precise runtime attribution metadata.
    public String ruleId = "";
    public DetectionHitState representativeState = DetectionHitState.UNKNOWN;
    public int totalCount;
    public int checkedCount;
    public int hitCount;
    public int notHitCount;
    public int unknownCount;
    // Legacy compatibility: mirrors hitCount.
    public int matchedCount;
    public long closestEventTimestamp;
    public long closestDeltaMs = Long.MAX_VALUE;
    public int pid = -1;
    public int tid = -1;
    public String thread = "";
    public String source = "";
    public String input = "";
    public String result = "";
    public String exception = "";
    public String exceptionClass = "";
    public String exceptionMessage = "";
    public String throwableId = "";
    public String cause = "";
    public int suppressedCount;
    public long durationNs;
    public String stack = "";
    public int sharedExitFrames;
    public boolean sameThreadAsExit;
    public int sharedFatalFrames;
    public boolean sameThreadAsFatal;
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
            o.put("ruleId", ruleId);
            o.put("representativeState", representativeState.name());
            o.put("totalCount", totalCount);
            o.put("checkedCount", checkedCount);
            o.put("hitCount", hitCount);
            o.put("notHitCount", notHitCount);
            o.put("unknownCount", unknownCount);
            o.put("matchedCount", matchedCount);
            o.put("closestEventTimestamp", closestEventTimestamp);
            o.put("closestDeltaMs", closestDeltaMs == Long.MAX_VALUE ? -1 : closestDeltaMs);
            o.put("pid", pid);
            o.put("tid", tid);
            o.put("thread", thread);
            o.put("source", source);
            o.put("input", input);
            o.put("result", result);
            o.put("exception", exception);
            o.put("exceptionClass", exceptionClass);
            o.put("exceptionMessage", exceptionMessage);
            o.put("throwableId", throwableId);
            o.put("cause", cause);
            o.put("suppressedCount", suppressedCount);
            o.put("durationNs", durationNs);
            o.put("stack", stack);
            o.put("sharedExitFrames", sharedExitFrames);
            o.put("sameThreadAsExit", sameThreadAsExit);
            o.put("sharedFatalFrames", sharedFatalFrames);
            o.put("sameThreadAsFatal", sameThreadAsFatal);
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
