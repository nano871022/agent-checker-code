package com.codeauditor.agent.crashlytics;

import com.codeauditor.agent.crashlytics.dto.CrashEvent;
import com.codeauditor.agent.crashlytics.dto.StackFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseCrashlyticsServiceTest {

    private FirebaseCrashlyticsService service;

    @BeforeEach
    void setUp() {
        service = new FirebaseCrashlyticsService("");
        service.init();
        service.clearCrashEvents();
    }

    @Test
    void testGetUnresolvedCrashesFiltering() {
        CrashEvent crash1 = CrashEvent.builder()
                .crashId("crash-1")
                .appId("1:1234567890:android:abcdef123456")
                .appVersion("v1.2.4")
                .exceptionType("java.lang.NullPointerException")
                .message("Null object reference")
                .stackTrace("java.lang.NullPointerException at com.company.app.UserProfileAdapter.onBindViewHolder(UserProfileAdapter.kt:84)")
                .eventCount(5)
                .resolved(false)
                .fingerprint("npe-userprofileadapter-84")
                .primaryFrame(StackFrame.builder()
                        .packageName("com.company.app")
                        .className("UserProfileAdapter")
                        .fileName("UserProfileAdapter.kt")
                        .methodName("onBindViewHolder")
                        .lineNumber(84)
                        .build())
                .timestamp(Instant.now())
                .build();

        // Below error threshold
        CrashEvent crash2 = CrashEvent.builder()
                .crashId("crash-2")
                .appId("1:1234567890:android:abcdef123456")
                .appVersion("v1.2.4")
                .exceptionType("java.lang.IndexOutOfBoundsException")
                .message("Index 2 out of bounds for length 2")
                .stackTrace("java.lang.IndexOutOfBoundsException at com.company.app.ItemAdapter.onBind(ItemAdapter.kt:42)")
                .eventCount(2)
                .resolved(false)
                .fingerprint("ioob-itemadapter-42")
                .primaryFrame(StackFrame.builder()
                        .packageName("com.company.app")
                        .className("ItemAdapter")
                        .fileName("ItemAdapter.kt")
                        .methodName("onBind")
                        .lineNumber(42)
                        .build())
                .timestamp(Instant.now())
                .build();

        // Already resolved
        CrashEvent crash3 = CrashEvent.builder()
                .crashId("crash-3")
                .appId("1:1234567890:android:abcdef123456")
                .appVersion("v1.2.4")
                .exceptionType("java.lang.IllegalArgumentException")
                .message("Invalid argument")
                .stackTrace("java.lang.IllegalArgumentException")
                .eventCount(10)
                .resolved(true)
                .fingerprint("iae-someclass-10")
                .timestamp(Instant.now())
                .build();

        // Different app version
        CrashEvent crash4 = CrashEvent.builder()
                .crashId("crash-4")
                .appId("1:1234567890:android:abcdef123456")
                .appVersion("v1.2.3")
                .exceptionType("java.lang.ClassCastException")
                .message("Cannot cast String to Integer")
                .eventCount(8)
                .resolved(false)
                .fingerprint("cce-someclass-20")
                .timestamp(Instant.now())
                .build();

        service.registerCrashEvent(crash1);
        service.registerCrashEvent(crash2);
        service.registerCrashEvent(crash3);
        service.registerCrashEvent(crash4);

        List<CrashEvent> results = service.getUnresolvedCrashes("1:1234567890:android:abcdef123456", "v1.2.4", 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCrashId()).isEqualTo("crash-1");
        assertThat(results.get(0).getExceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(results.get(0).getPrimaryFrame().getFileName()).isEqualTo("UserProfileAdapter.kt");
    }

    @Test
    void testGetUnresolvedCrashesWithNoVersionFilter() {
        CrashEvent crash1 = CrashEvent.builder()
                .crashId("crash-1")
                .appId("app-1")
                .appVersion("v1.0.0")
                .eventCount(5)
                .resolved(false)
                .build();

        service.registerCrashEvent(crash1);

        List<CrashEvent> results = service.getUnresolvedCrashes("app-1", null, 1);
        assertThat(results).hasSize(1);
    }
}
