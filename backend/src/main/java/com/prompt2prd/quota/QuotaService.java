package com.prompt2prd.quota;

import com.prompt2prd.common.api.ApiException;
import com.prompt2prd.common.config.ModelProperties;
import com.prompt2prd.model.domain.ModelConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Pattern;

/** Deterministic policy boundary shared by future model-backed controllers. */
@Service
public final class QuotaService {

    private static final long RATE_WINDOW_SECONDS = 60;
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final QuotaStore store;
    private final QuotaProperties properties;
    private final ModelProperties modelProperties;
    private final Clock clock;

    @Autowired
    public QuotaService(
            QuotaStore store,
            QuotaProperties properties,
            ModelProperties modelProperties) {
        this(store, properties, modelProperties, Clock.systemUTC());
    }

    QuotaService(
            QuotaStore store,
            QuotaProperties properties,
            ModelProperties modelProperties,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.modelProperties = Objects.requireNonNull(modelProperties, "modelProperties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Admits one user-visible operation. A chunked analysis must call this once,
     * then call {@link #acquireUpstreamCalls(ModelConfig.KeySource, int)} per model-call batch.
     */
    public void beginOperation(
            String ipDigest,
            ModelConfig.KeySource keySource,
            QuotaOperation operation) {
        requireDigest(ipDigest);
        Objects.requireNonNull(keySource, "keySource");
        Objects.requireNonNull(operation, "operation");

        if (keySource == ModelConfig.KeySource.SYSTEM && !modelProperties.systemKeyAvailable()) {
            throw new ApiException.BadRequest("System key mode is unavailable");
        }
        checkFrequency(ipDigest);
        if (keySource == ModelConfig.KeySource.USER) {
            return;
        }

        int limit = operation == QuotaOperation.ANALYSIS
                ? properties.analysisDailyLimit()
                : properties.fullPrdDailyLimit();
        if (!store.tryConsumeIpOperation(ipDigest, currentDate(), operation, limit)) {
            throw new ApiException.RateLimitExceeded("Daily system-key operation quota exhausted");
        }
    }

    /** Applies the basic per-IP frequency limit without consuming a free operation. */
    public void checkFrequency(String ipDigest) {
        requireDigest(ipDigest);
        long window = clock.instant().getEpochSecond() / RATE_WINDOW_SECONDS;
        if (!store.tryConsumeRateWindow(ipDigest, window, properties.requestsPerMinute())) {
            throw new ApiException.RateLimitExceeded("Basic request frequency limit exhausted");
        }
    }

    /** Atomically reserves real upstream calls against the system-key global daily budget. */
    public void acquireUpstreamCalls(ModelConfig.KeySource keySource, int calls) {
        Objects.requireNonNull(keySource, "keySource");
        if (calls <= 0) {
            throw new IllegalArgumentException("calls must be positive");
        }
        if (keySource == ModelConfig.KeySource.USER) {
            return;
        }
        if (!modelProperties.systemKeyAvailable()) {
            throw new ApiException.BadRequest("System key mode is unavailable");
        }
        if (!store.tryConsumeGlobalCalls(
                currentDate(), calls, properties.globalDailyCallLimit())) {
            throw new ApiException.RateLimitExceeded("Global system-key budget exhausted");
        }
    }

    public QuotaSnapshot current(String ipDigest) {
        requireDigest(ipDigest);
        if (!modelProperties.systemKeyAvailable()) {
            return new QuotaSnapshot(false, 0, 0, 0);
        }
        LocalDate date = currentDate();
        QuotaStore.UsageSnapshot usage = store.snapshot(ipDigest, date);
        return new QuotaSnapshot(
                true,
                remaining(properties.analysisDailyLimit(), usage.analysisUsed()),
                remaining(properties.fullPrdDailyLimit(), usage.fullPrdUsed()),
                remaining(properties.globalDailyCallLimit(), store.globalCallsUsed(date)));
    }

    private LocalDate currentDate() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private int remaining(int limit, int used) {
        return Math.max(0, limit - used);
    }

    private void requireDigest(String ipDigest) {
        if (ipDigest == null || !SHA_256_HEX.matcher(ipDigest).matches()) {
            throw new IllegalArgumentException("ipDigest must be a lowercase SHA-256 digest");
        }
    }
}
