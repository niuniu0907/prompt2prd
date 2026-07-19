package com.prompt2prd.quota;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Environment-backed limits for the single-instance MVP quota policy. */
@Component
public final class QuotaProperties {

    public static final String ANALYSIS_DAILY_LIMIT_ENV = "PROMPT2PRD_QUOTA_ANALYSIS_DAILY_LIMIT";
    public static final String FULL_PRD_DAILY_LIMIT_ENV = "PROMPT2PRD_QUOTA_FULL_PRD_DAILY_LIMIT";
    public static final String GLOBAL_DAILY_CALL_LIMIT_ENV = "PROMPT2PRD_QUOTA_GLOBAL_DAILY_CALL_LIMIT";
    public static final String REQUESTS_PER_MINUTE_ENV = "PROMPT2PRD_QUOTA_REQUESTS_PER_MINUTE";
    public static final String MAX_TRACKED_ENTRIES_ENV = "PROMPT2PRD_QUOTA_MAX_TRACKED_ENTRIES";
    public static final String IP_DIGEST_SALT_ENV = "PROMPT2PRD_QUOTA_IP_DIGEST_SALT";

    private static final int DEFAULT_ANALYSIS_DAILY_LIMIT = 3;
    private static final int DEFAULT_FULL_PRD_DAILY_LIMIT = 1;
    private static final int DEFAULT_GLOBAL_DAILY_CALL_LIMIT = 100;
    private static final int DEFAULT_REQUESTS_PER_MINUTE = 30;
    private static final int DEFAULT_MAX_TRACKED_ENTRIES = 10_000;

    private final int analysisDailyLimit;
    private final int fullPrdDailyLimit;
    private final int globalDailyCallLimit;
    private final int requestsPerMinute;
    private final int maxTrackedEntries;
    private final String ipDigestSalt;

    public QuotaProperties() {
        this(System.getenv());
    }

    private QuotaProperties(Map<String, String> environment) {
        this(
                positive(environment, ANALYSIS_DAILY_LIMIT_ENV, DEFAULT_ANALYSIS_DAILY_LIMIT),
                positive(environment, FULL_PRD_DAILY_LIMIT_ENV, DEFAULT_FULL_PRD_DAILY_LIMIT),
                positive(environment, GLOBAL_DAILY_CALL_LIMIT_ENV, DEFAULT_GLOBAL_DAILY_CALL_LIMIT),
                positive(environment, REQUESTS_PER_MINUTE_ENV, DEFAULT_REQUESTS_PER_MINUTE),
                positive(environment, MAX_TRACKED_ENTRIES_ENV, DEFAULT_MAX_TRACKED_ENTRIES),
                salt(environment.get(IP_DIGEST_SALT_ENV)));
    }

    public QuotaProperties(
            int analysisDailyLimit,
            int fullPrdDailyLimit,
            int globalDailyCallLimit,
            int requestsPerMinute,
            int maxTrackedEntries,
            String ipDigestSalt) {
        this.analysisDailyLimit = requirePositive(analysisDailyLimit, "analysisDailyLimit");
        this.fullPrdDailyLimit = requirePositive(fullPrdDailyLimit, "fullPrdDailyLimit");
        this.globalDailyCallLimit = requirePositive(globalDailyCallLimit, "globalDailyCallLimit");
        this.requestsPerMinute = requirePositive(requestsPerMinute, "requestsPerMinute");
        this.maxTrackedEntries = requirePositive(maxTrackedEntries, "maxTrackedEntries");
        if (ipDigestSalt == null || ipDigestSalt.isBlank()) {
            throw new IllegalArgumentException("ipDigestSalt must not be blank");
        }
        this.ipDigestSalt = ipDigestSalt;
    }

    public static QuotaProperties fromEnvironment(Map<String, String> environment) {
        return new QuotaProperties(Map.copyOf(environment));
    }

    public int analysisDailyLimit() {
        return analysisDailyLimit;
    }

    public int fullPrdDailyLimit() {
        return fullPrdDailyLimit;
    }

    public int globalDailyCallLimit() {
        return globalDailyCallLimit;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    public int maxTrackedEntries() {
        return maxTrackedEntries;
    }

    String ipDigestSalt() {
        return ipDigestSalt;
    }

    @Override
    public String toString() {
        return "QuotaProperties[analysisDailyLimit=" + analysisDailyLimit
                + ", fullPrdDailyLimit=" + fullPrdDailyLimit
                + ", globalDailyCallLimit=" + globalDailyCallLimit
                + ", requestsPerMinute=" + requestsPerMinute
                + ", maxTrackedEntries=" + maxTrackedEntries
                + ", ipDigestSalt=[REDACTED]]";
    }

    private static int positive(Map<String, String> environment, String name, int defaultValue) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return requirePositive(Integer.parseInt(value.trim()), name);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer", exception);
        }
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String salt(String configured) {
        return configured == null || configured.isBlank()
                ? UUID.randomUUID().toString()
                : configured.trim();
    }
}
