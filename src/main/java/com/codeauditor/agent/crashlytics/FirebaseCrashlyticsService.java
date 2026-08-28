package com.codeauditor.agent.crashlytics;

import com.codeauditor.agent.crashlytics.dto.CrashEvent;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FirebaseCrashlyticsService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseCrashlyticsService.class);

    private final String serviceAccountKeyPath;
    private final List<CrashEvent> crashStore = Collections.synchronizedList(new ArrayList<>());
    private boolean initialized = false;

    public FirebaseCrashlyticsService(
            @Value("${firebase.service-account-path:${FIREBASE_SERVICE_ACCOUNT_KEY_PATH:}}") String serviceAccountKeyPath) {
        this.serviceAccountKeyPath = serviceAccountKeyPath;
    }

    @PostConstruct
    public void init() {
        if (serviceAccountKeyPath != null && !serviceAccountKeyPath.isBlank()) {
            File keyFile = new File(serviceAccountKeyPath);
            if (keyFile.exists()) {
                try (InputStream serviceAccount = new FileInputStream(keyFile)) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();

                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options);
                        log.info("Firebase Admin SDK initialized successfully with credentials from '{}'.", serviceAccountKeyPath);
                    }
                    this.initialized = true;
                } catch (Exception e) {
                    log.error("Failed to initialize Firebase Admin SDK from path '{}': {}", serviceAccountKeyPath, e.getMessage());
                }
            } else {
                log.warn("Firebase service account key file not found at '{}'. Firebase Crashlytics service operating in offline mode.", serviceAccountKeyPath);
            }
        } else {
            log.info("No Firebase service account key path configured. Operating in offline/simulated mode.");
        }
    }

    /**
     * Fetch unresolved crash events for the latest release version filtering by minimum error threshold.
     *
     * @param appId               the Firebase App ID (e.g. "1:1234567890:android:abcdef123456")
     * @param appVersion          the release version to filter by (e.g. "v1.2.4")
     * @param minErrorThreshold  minimum occurrence threshold for unresolved crashes
     * @return list of unresolved crash events meeting criteria
     */
    public List<CrashEvent> getUnresolvedCrashes(String appId, String appVersion, int minErrorThreshold) {
        log.info("Inspecting Firebase Crashlytics logs for appId='{}', appVersion='{}', minThreshold={}",
                appId, appVersion, minErrorThreshold);

        return crashStore.stream()
                .filter(crash -> appId == null || appId.isBlank() || appId.equals(crash.getAppId()))
                .filter(crash -> !crash.isResolved())
                .filter(crash -> appVersion == null || appVersion.isBlank() || appVersion.equalsIgnoreCase(crash.getAppVersion()))
                .filter(crash -> crash.getEventCount() >= minErrorThreshold)
                .toList();
    }

    /**
     * Registers a crash event into the inspection service (used for ingestion/testing).
     *
     * @param crash the CrashEvent to add
     */
    public void registerCrashEvent(CrashEvent crash) {
        if (crash != null) {
            crashStore.add(crash);
            log.info("Registered crash event '{}' for app '{}' version '{}'", crash.getCrashId(), crash.getAppId(), crash.getAppVersion());
        }
    }

    /**
     * Clears all registered crash events.
     */
    public void clearCrashEvents() {
        crashStore.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }
}
