package com.codeauditor.agent.triage.dto;

public class TriageResult {

    private final TriageDecision decision;
    private final String reasoning;
    private final Long duplicateOfIssueId;
    private final String suggestedTitle;

    public TriageResult(TriageDecision decision, String reasoning) {
        this(decision, reasoning, null, null);
    }

    public TriageResult(TriageDecision decision, String reasoning, Long duplicateOfIssueId, String suggestedTitle) {
        this.decision = decision;
        this.reasoning = reasoning;
        this.duplicateOfIssueId = duplicateOfIssueId;
        this.suggestedTitle = suggestedTitle;
    }

    public TriageDecision getDecision() {
        return decision;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Long getDuplicateOfIssueId() {
        return duplicateOfIssueId;
    }

    public String getSuggestedTitle() {
        return suggestedTitle;
    }
}
