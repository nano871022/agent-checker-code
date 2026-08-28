package com.codeauditor.agent.controller;

import com.codeauditor.agent.dto.PrioritizeRequest;
import com.codeauditor.agent.dto.PrioritizeResponse;
import com.codeauditor.agent.queue.RepositoryQueueManager;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final RepositoryQueueManager queueManager;

    public QueueController(RepositoryQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @PostMapping("/prioritize")
    public ResponseEntity<PrioritizeResponse> prioritizeRepository(@Valid @RequestBody PrioritizeRequest request) {
        log.info("Received request to prioritize repository '{}', force_immediate={}",
                request.repositoryName(), request.forceImmediate());

        boolean prioritized = queueManager.prioritizeRepository(request.repositoryName());

        if (prioritized) {
            PrioritizeResponse response = new PrioritizeResponse(
                    true,
                    "Repository successfully prioritized to head of queue.",
                    request.repositoryName()
            );
            return ResponseEntity.ok(response);
        } else {
            PrioritizeResponse response = new PrioritizeResponse(
                    false,
                    "Repository not found in active queue or is disabled.",
                    request.repositoryName()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
}
