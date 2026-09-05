package co.dev.japl.java.spring.agentcheckercode.triage.dto;

import java.util.List;

public class TriageResult {

    private final TriageDecision decision;
    private final String reasoning;
    private final Long duplicateOfIssueId;
    private final String suggestedTitle;
    private final TriageAnalysis analysis;

    public TriageResult(TriageDecision decision, String reasoning) {
        this(decision, reasoning, null, null);
    }

    public TriageResult(TriageDecision decision, String reasoning, Long duplicateOfIssueId, String suggestedTitle) {
        this(decision, reasoning, duplicateOfIssueId, suggestedTitle, null);
    }

    public TriageResult(TriageDecision decision, String reasoning, Long duplicateOfIssueId,
                        String suggestedTitle, TriageAnalysis analysis) {
        this.decision = decision;
        this.reasoning = reasoning;
        this.duplicateOfIssueId = duplicateOfIssueId;
        this.suggestedTitle = suggestedTitle;
        this.analysis = analysis;
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

    public TriageAnalysis getAnalysis() {
        return analysis;
    }

    public String getDetailedAnalysis() {
        if (analysis == null) {
            return reasoning;
        }
        StringBuilder details = new StringBuilder();
        append(details, "### Summary", analysis.getSummary());
        append(details, "### Root Cause", analysis.getRootCause());
        appendList(details, "### Evidence", analysis.getEvidence());
        appendList(details, "### Recommended Actions", analysis.getRecommendedActions());
        appendList(details, "### Human Guidance", analysis.getHumanGuidance());
        appendList(details, "### Affected Files / Locations", analysis.getAffectedFiles());
        appendList(details, "### Tests and Verification", analysis.getTestsToAdd());
        return details.length() == 0 ? reasoning : details.toString().trim();
    }

    private void append(StringBuilder details, String heading, String value) {
        if (value != null && !value.isBlank()) {
            details.append(heading).append("\n\n").append(value.trim()).append("\n\n");
        }
    }

    private void appendList(StringBuilder details, String heading, List<String> values) {
        if (values != null && !values.isEmpty()) {
            details.append(heading).append("\n\n");
            values.stream().filter(value -> value != null && !value.isBlank())
                    .forEach(value -> details.append("- ").append(value.trim()).append("\n"));
            details.append("\n");
        }
    }
}
