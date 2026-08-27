package com.codeauditor.agent.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentState {
    private int lastProcessedIndex;
    private String lastProcessedRepositoryName;
    private String lastProcessedTimestamp;
}
