package com.nepopay.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final long[] now = {0L};
    private final RateLimiter limiter = new RateLimiter(() -> now[0]);
    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    void allowsUpToCapacityThenRejects() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("k", 3, MINUTE).allowed()).isTrue();
        }
        RateLimiter.Decision denied = limiter.tryConsume("k", 3, MINUTE);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 20L); // 3 per minute = one token per 20 seconds
    }

    @Test
    void tokensRefillOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("k", 3, MINUTE);
        }
        assertThat(limiter.tryConsume("k", 3, MINUTE).allowed()).isFalse();

        now[0] += TimeUnit.SECONDS.toNanos(21);                    // one token has come back
        assertThat(limiter.tryConsume("k", 3, MINUTE).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", 3, MINUTE).allowed()).isFalse();
    }

    @Test
    void neverRefillsBeyondCapacity() {
        limiter.tryConsume("k", 2, MINUTE);
        now[0] += TimeUnit.HOURS.toNanos(5);
        assertThat(limiter.tryConsume("k", 2, MINUTE).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", 2, MINUTE).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", 2, MINUTE).allowed()).isFalse();
    }

    @Test
    void keysAreIndependent() {
        limiter.tryConsume("alice", 1, MINUTE);
        assertThat(limiter.tryConsume("alice", 1, MINUTE).allowed()).isFalse();
        assertThat(limiter.tryConsume("bob", 1, MINUTE).allowed()).isTrue();
    }
}
