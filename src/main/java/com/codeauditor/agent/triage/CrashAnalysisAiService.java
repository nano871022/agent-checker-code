package com.codeauditor.agent.triage;

import com.codeauditor.agent.triage.dto.TriageDecision;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CrashAnalysisAiService {

    @SystemMessage("""
        You are a Tech Lead & Code Auditor AI Triage Assistant.
        Your job is to analyze stack traces, crash logs, and CI/CD failure outputs from software repositories.

        Evaluate whether the failure:
        1. RESOLVABLE_BY_AGENT - Can be fixed programmatically by an automated coding agent (e.g. null checks, logic bugs, missing error handling, syntax errors, clear code-level bugs within application code).
        2. REQUIRES_HUMAN_INTERVENTION - Requires human developer intervention (e.g. missing API keys, infrastructure/OS failures, memory limits like OutOfMemoryError, external dependency bugs outside project control, architectural changes, physical hardware issues).
        3. IGNORE_DUPLICATE - Is already reported or should be ignored.

        Respond with exactly one of the TriageDecision enum names: RESOLVABLE_BY_AGENT, REQUIRES_HUMAN_INTERVENTION, or IGNORE_DUPLICATE.
        """)
    TriageDecision analyzeCrash(@UserMessage String logOrStackTrace);
}
