package co.dev.japl.java.spring.agentcheckercode.triage;

import co.dev.japl.java.spring.agentcheckercode.triage.dto.TriageAnalysis;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CrashAnalysisAiService {

    @SystemMessage(fromResource = "prompts/crash-analysis-system.prompt")
    @UserMessage(fromResource = "prompts/crash-analysis-user.prompt")
    TriageAnalysis analyzeCrash(@V("logOrStackTrace") String logOrStackTrace);
}
