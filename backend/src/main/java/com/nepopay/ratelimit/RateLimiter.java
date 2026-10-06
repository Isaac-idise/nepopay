package com.nepopay.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter. Each key owns a bucket holding up to `capacity` tokens that refills
 * steadily over `period`. A request spends one token; no token = rejected, with a retry-after hint.
 * In-memory: correct for ONE server instance. Several instances would need a shared store (Redis).
 */
public class RateLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    private static final long IDLE_NANOS = Duration.ofHours(1).toNanos();
    private static final int CLEANUP_THRESHOLD = 20_000;

    private static final class Bucket {
        private final int capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastNanos;

        Bucket(int capacity, long periodNanos, long now) {
            this.capacity = capacity;
            this.refillPerNano = capacity / (double) periodNanos;
            this.tokens = capacity;
            this.lastNanos = now;
        }

        synchronized Decision take(long now) {
            tokens = Math.min(capacity, tokens + (now - lastNanos) * refillPerNano);
            lastNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return Decision.ALLOWED;
            }
            long waitNanos = (long) Math.ceil((1.0 - tokens) / refillPerNano);
            long seconds = Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L);
            return new Decision(false, seconds);
        }

        synchronized boolean idle(long now) {
            return now - lastNanos > IDLE_NANOS;
        }
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoClock;

    public RateLimiter() {
        this(System::nanoTime);
    }

    public RateLimiter(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    public Decision tryConsume(String key, int capacity, Duration period) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, period.toNanos(), now));
        if (buckets.size() > CLEANUP_THRESHOLD) {
            buckets.values().removeIf(b -> b.idle(now));
        }
        return bucket.take(now);
    }
}
