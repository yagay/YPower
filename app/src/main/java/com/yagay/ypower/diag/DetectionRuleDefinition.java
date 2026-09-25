package com.yagay.ypower.diag;

public final class DetectionRuleDefinition {
    public final String id;
    public final String category;
    public final String title;
    public final String whyDetected;
    public final String projectExplanation;
    public final String remediation;
    public final String reference;

    public DetectionRuleDefinition(
            String id,
            String category,
            String title,
            String whyDetected,
            String projectExplanation,
            String remediation,
            String reference
    ) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.whyDetected = whyDetected;
        this.projectExplanation = projectExplanation;
        this.remediation = remediation;
        this.reference = reference;
    }
}
