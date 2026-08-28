package com.codeauditor.agent.crashlytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrashEvent {
    private String crashId;
    private String appId;
    private String appVersion;
    private String exceptionType;
    private String message;
    private String stackTrace;
    private int eventCount;
    private boolean resolved;
    private String fingerprint;
    private StackFrame primaryFrame;
    private Instant timestamp;
}
