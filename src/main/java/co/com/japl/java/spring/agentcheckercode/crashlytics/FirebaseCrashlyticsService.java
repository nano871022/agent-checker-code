package co.com.japl.java.spring.agentcheckercode.crashlytics;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import co.com.japl.java.spring.agentcheckercode.crashlytics.dto.CrashEvent;
import co.com.japl.java.spring.agentcheckercode.crashlytics.dto.StackFrame;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@Service
public class FirebaseCrashlyticsService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseCrashlyticsService.class);

    private final String serviceAccountKeyPath;
    private final List<CrashEvent> crashStore = Collections.synchronizedList(new ArrayList<>());
    private boolean initialized = false;
    private String connectedProjectId;
    private GoogleCredentials credentials;
    private final RestClient crashlyticsClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public FirebaseCrashlyticsService(
            @Value("${firebase.service-account-path:${FIREBASE_SERVICE_ACCOUNT_KEY_PATH:}}") String serviceAccountKeyPath) {
        this(serviceAccountKeyPath, RestClient.builder().build(), new ObjectMapper());
    }

    FirebaseCrashlyticsService(String serviceAccountKeyPath, RestClient crashlyticsClient, ObjectMapper objectMapper) {
        this.serviceAccountKeyPath = serviceAccountKeyPath;
        this.crashlyticsClient = crashlyticsClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("Starting Firebase Crashlytics connection. Credentials path configured={}",
                serviceAccountKeyPath != null && !serviceAccountKeyPath.isBlank());
        if (serviceAccountKeyPath != null && !serviceAccountKeyPath.isBlank()) {
            File keyFile = new File(serviceAccountKeyPath);
            if (keyFile.exists()) {
                try (InputStream serviceAccount = new FileInputStream(keyFile)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount)
                            .createScoped(List.of(
                                    "https://www.googleapis.com/auth/cloud-platform",
                                    "https://www.googleapis.com/auth/firebase"
                            ));
                    if (credentials instanceof ServiceAccountCredentials serviceAccountCredentials) {
                        connectedProjectId = serviceAccountCredentials.getProjectId();
                    }

                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(credentials)
                            .build();

                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options);
                    } else {
                        log.info("Firebase Admin SDK was already initialized; reusing existing Firebase app.");
                    }
                    this.initialized = true;
                    log.info("Firebase connection successful: projectId='{}', credentials='{}', crashlyticsDataSource='in-memory registered events'.",
                            connectedProjectId == null ? "unknown" : connectedProjectId, serviceAccountKeyPath);
                } catch (Exception e) {
                    this.initialized = false;
                    log.error("Firebase connection failed: path='{}', projectId='{}', error='{}'.",
                            serviceAccountKeyPath, connectedProjectId == null ? "unknown" : connectedProjectId,
                            e.getMessage(), e);
                }
            } else {
                log.warn("Firebase connection unavailable: service account key file not found at '{}'; projectId='unknown'; operating in offline mode.",
                        serviceAccountKeyPath);
            }
        } else {
            log.warn("Firebase connection unavailable: no service account key path configured; projectId='unknown'; operating in offline/simulated mode.");
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
        int totalEvents = crashStore.size();
        log.info("Querying Firebase Crashlytics: initialized={}, projectId='{}', appId='{}', appVersion='{}', minThreshold={}, registeredEvents={}",
            initialized, connectedProjectId == null ? "unknown" : connectedProjectId,
            appId, appVersion, minErrorThreshold, totalEvents);

        try {
            List<CrashEvent> results = fetchRemoteCrashes(appId, appVersion, minErrorThreshold);
            if (results == null) {
                log.warn("Firebase Crashlytics API unavailable; using {} locally registered events as fallback.", totalEvents);
                results = filterLocalCrashes(appId, appVersion, minErrorThreshold);
            }

            int errorOccurrences = results.stream().collect(Collectors.summingInt(CrashEvent::getEventCount));
            log.info("Firebase Crashlytics query completed: projectId='{}', totalRegistered={}, matchingCrashGroups={}, errorsFound={}, errorOccurrences={}.",
                    connectedProjectId == null ? "unknown" : connectedProjectId, totalEvents,
                    results.size(), results.size(), errorOccurrences);
            return results;
        } catch (Exception e) {
            log.error("Firebase Crashlytics query failed: projectId='{}', appId='{}', error='{}'.",
                    connectedProjectId == null ? "unknown" : connectedProjectId, appId, e.getMessage(), e);
            return filterLocalCrashes(appId, appVersion, minErrorThreshold);
        }
    }

    private List<CrashEvent> filterLocalCrashes(String appId, String appVersion, int minErrorThreshold) {
        return crashStore.stream()
                .filter(crash -> appId == null || appId.isBlank() || appId.equals(crash.getAppId()))
                .filter(crash -> !crash.isResolved())
                .filter(crash -> appVersion == null || appVersion.isBlank() || appVersion.equalsIgnoreCase(crash.getAppVersion()))
                .filter(crash -> crash.getEventCount() >= minErrorThreshold)
                .toList();
    }

    private List<CrashEvent> fetchRemoteCrashes(String appId, String appVersion, int minErrorThreshold) {
        if (!initialized || credentials == null || connectedProjectId == null || connectedProjectId.isBlank()
                || appId == null || appId.isBlank()) {
            log.warn("Skipping Firebase Crashlytics API call: initialized={}, projectId='{}', appId='{}'.",
                    initialized, connectedProjectId == null ? "unknown" : connectedProjectId, appId);
            return null;
        }

        try {
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null) {
                credentials.refreshAccessToken();
            }

            URI uri = UriComponentsBuilder.fromUriString("https://firebasecrashlytics.googleapis.com")
                    .path("/v1alpha/projects/{project}/apps/{app}/reports/topIssues")
                    .queryParam("pageSize", 100)
                    .queryParamIfPresent("filter.version.displayNames",
                            appVersion == null || appVersion.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(appVersion))
                    .buildAndExpand(connectedProjectId, appId)
                    .encode()
                    .toUri();

            String response = crashlyticsClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .retrieve()
                    .body(String.class);
            return parseRemoteEvents(response, appId, minErrorThreshold);
        } catch (HttpClientErrorException.Forbidden e) {
            String activationUrl = "https://console.cloud.google.com/apis/library/firebasecrashlytics.googleapis.com?project="
                    + connectedProjectId;
            log.warn("Firebase Crashlytics API access denied for projectId='{}' (HTTP 403). "
                            + "Enable the Firebase Crashlytics API and grant the service account access. "
                            + "Activation URL: {}. API response: {}",
                    connectedProjectId, activationUrl, summarizeApiError(e.getResponseBodyAsString()));
            return null;
        } catch (HttpClientErrorException e) {
            log.warn("Firebase Crashlytics API request rejected: projectId='{}', appId='{}', status={}, response={}",
                    connectedProjectId, appId, e.getStatusCode(), summarizeApiError(e.getResponseBodyAsString()));
            return null;
        } catch (Exception e) {
            log.error("Firebase Crashlytics API request failed: projectId='{}', appId='{}', error='{}'.",
                    connectedProjectId, appId, e.getMessage());
            return null;
        }
    }

    private String summarizeApiError(String response) {
        if (response == null || response.isBlank()) {
            return "no response body";
        }
        String normalized = response.replaceAll("\\s+", " ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    List<CrashEvent> parseRemoteEvents(String response, String appId, int minErrorThreshold) throws Exception {
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        List<CrashEvent> events = new ArrayList<>();

        if (root.has("groups")) {
            for (JsonNode group : root.path("groups")) {
                JsonNode issue = group.path("issue");
                String issueId = textOrDefault(issue, "id", "unknown");
                String state = textOrDefault(issue, "state", "OPEN");
                if ("CLOSED".equalsIgnoreCase(state)) {
                    continue;
                }

                String title = textOrDefault(issue, "title", "");
                String subtitle = textOrDefault(issue, "subtitle", "");
                String lastSeenVersion = textOrDefault(issue, "lastSeenVersion", null);
                String issueUri = textOrDefault(issue, "uri", null);

                String exceptionType = textOrDefault(issue, "errorType", "CRASH");
                String message = title;
                if (!subtitle.isBlank()) {
                    if (subtitle.contains(" - ")) {
                        String[] parts = subtitle.split(" - ", 2);
                        exceptionType = parts[0].trim();
                        message = parts[1].trim();
                    } else if (subtitle.contains(": ")) {
                        String[] parts = subtitle.split(": ", 2);
                        exceptionType = parts[0].trim();
                        message = parts[1].trim();
                    } else {
                        exceptionType = subtitle.trim();
                        message = title;
                    }
                }

                StackFrame primaryFrame = parsePrimaryFrameFromTitle(title);

                StringBuilder stackTraceBuilder = new StringBuilder();
                if (!subtitle.isBlank()) {
                    stackTraceBuilder.append(subtitle).append("\n");
                }
                if (!title.isBlank()) {
                    stackTraceBuilder.append("\tat ").append(title);
                }
                if (issueUri != null && !issueUri.isBlank()) {
                    stackTraceBuilder.append("\nCrashlytics Console: ").append(issueUri);
                }

                int eventCount = 1;
                JsonNode metrics = group.path("metrics");
                if (metrics.isArray() && !metrics.isEmpty()) {
                    eventCount = metrics.get(0).path("eventsCount").asInt(1);
                }

                CrashEvent crashEvent = CrashEvent.builder()
                        .crashId(issueId)
                        .appId(appId)
                        .appVersion(lastSeenVersion)
                        .exceptionType(exceptionType)
                        .message(message)
                        .stackTrace(stackTraceBuilder.toString().trim())
                        .eventCount(eventCount)
                        .resolved(false)
                        .primaryFrame(primaryFrame)
                        .timestamp(Instant.now())
                        .build();

                if (crashEvent.getEventCount() >= minErrorThreshold) {
                    events.add(crashEvent);
                }
            }
            return events;
        }

        Map<String, CrashEventAccumulator> grouped = new HashMap<>();
        for (JsonNode event : root.path("events")) {
            JsonNode issue = event.path("issue");
            String issueId = textOrDefault(issue, "id", event.path("name").asText("unknown"));
            CrashEventAccumulator accumulator = grouped.computeIfAbsent(issueId,
                    ignored -> new CrashEventAccumulator(issueId, appId));
            accumulator.add(event, issue);
        }
        return grouped.values().stream()
                .map(CrashEventAccumulator::toCrashEvent)
                .filter(crash -> crash.getEventCount() >= minErrorThreshold)
                .toList();
    }

    private StackFrame parsePrimaryFrameFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        int lastDot = title.lastIndexOf('.');
        if (lastDot <= 0) {
            return StackFrame.builder().className(title).build();
        }
        String fullClassName = title.substring(0, lastDot);
        String methodName = title.substring(lastDot + 1);
        int lastClassDot = fullClassName.lastIndexOf('.');
        String packageName = lastClassDot > 0 ? fullClassName.substring(0, lastClassDot) : "";
        String simpleClassName = lastClassDot > 0 ? fullClassName.substring(lastClassDot + 1) : fullClassName;
        String fileName = simpleClassName.contains("$")
                ? simpleClassName.substring(0, simpleClassName.indexOf('$')) + ".kt"
                : simpleClassName + ".kt";

        return StackFrame.builder()
                .packageName(packageName)
                .className(fullClassName)
                .methodName(methodName)
                .fileName(fileName)
                .build();
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class CrashEventAccumulator {
        private final String issueId;
        private final String appId;
        private int eventCount;
        private String appVersion;
        private String exceptionType;
        private String message;
        private String stackTrace;
        private Instant timestamp;

        private CrashEventAccumulator(String issueId, String appId) {
            this.issueId = issueId;
            this.appId = appId;
        }

        private void add(JsonNode event, JsonNode issue) {
            eventCount++;
            appVersion = event.path("version").path("displayVersion").asText(appVersion);
            exceptionType = issue.path("errorType").asText(exceptionType);
            message = issue.path("subtitle").asText(issue.path("title").asText(message));
            stackTrace = issue.path("title").asText(stackTrace);
            String eventTime = event.path("eventTime").asText(null);
            if (timestamp == null && eventTime != null) {
                timestamp = Instant.parse(eventTime);
            }
        }

        private CrashEvent toCrashEvent() {
            return CrashEvent.builder()
                    .crashId(issueId)
                    .appId(appId)
                    .appVersion(appVersion)
                    .exceptionType(exceptionType)
                    .message(message)
                    .stackTrace(stackTrace)
                    .eventCount(eventCount)
                    .resolved(false)
                    .timestamp(timestamp)
                    .build();
        }
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

    public String getConnectedProjectId() {
        return connectedProjectId;
    }
}
