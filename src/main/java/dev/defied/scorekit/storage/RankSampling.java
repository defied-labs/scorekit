package dev.defied.scorekit.storage;

import java.time.Duration;

public record RankSampling(int interval, Duration refresh) {

    public static final RankSampling DEFAULT = new RankSampling(10_000, Duration.ofMinutes(10));

    public RankSampling {
        if (interval < 1) {
            throw new IllegalArgumentException("Sample interval must be positive: " + interval);
        }
        if (refresh.toMillis() < 1) {
            throw new IllegalArgumentException("Refresh interval must be at least 1 ms: " + refresh);
        }
    }
}
