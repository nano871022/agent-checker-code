package com.codeauditor.agent.triage.dto;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageAnalysis {

    @Description("One of RESOLVABLE_BY_AGENT, REQUIRES_HUMAN_INTERVENTION, or IGNORE_DUPLICATE")
    private TriageDecision decision;

    @Description("Short but specific explanation of what failed and why")
    private String summary;

    @Description("Most likely technical root cause, including relevant error details")
    private String rootCause;

    @Builder.Default
    @Description("Concrete evidence from the supplied logs, stack trace, or issue")
    private List<String> evidence = new ArrayList<>();

    @Builder.Default
    @Description("Ordered implementation steps an automated coding agent should perform")
    private List<String> recommendedActions = new ArrayList<>();

    @Builder.Default
    @Description("Specific guidance for a human developer when automation is not safe")
    private List<String> humanGuidance = new ArrayList<>();

    @Builder.Default
    @Description("Files, classes, workflows, or configuration locations to inspect")
    private List<String> affectedFiles = new ArrayList<>();

    @Builder.Default
    @Description("Tests or verification commands that must be added or executed")
    private List<String> testsToAdd = new ArrayList<>();
}