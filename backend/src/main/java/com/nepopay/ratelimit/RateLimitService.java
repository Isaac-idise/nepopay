package com.nepopay.ratelimit;

import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** One place that knows every limit. "_IP" rules count per client address, "_USER" rules per logged-in user. */
@Service
public class RateLimitService {

    public enum Rule { LOGIN_IP, LOGIN_ACCOUNT, REGISTER_IP, REFRESH_IP, SEARCH_USER, TRANSFER_USER, PIN_CHANGE_USER, RESET_USER }

    private record Limit(int capacity, Duration period) {
    }

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final RateLimitProperties props;
    private final RateLimiter limiter = new RateLimiter();
    private final Map<Rule, Limit> limits = new EnumMap<>(Rule.class);

    public RateLimitService(RateLimitProperties p) {
        this.props = p;
        limits.put(Rule.LOGIN_IP, new Limit(p.loginIpPerMinute(), Duration.ofMinutes(1)));
        limits.put(Rule.LOGIN_ACCOUNT, new Limit(p.loginAccountPerWindow(), WINDOW));
        limits.put(Rule.REGISTER_IP, new Limit(p.registerPerHour(), Duration.ofHours(1)));
        limits.put(Rule.REFRESH_IP, new Limit(p.refreshPerMinute(), Duration.ofMinutes(1)));
        limits.put(Rule.SEARCH_USER, new Limit(p.searchPerMinute(), Duration.ofMinutes(1)));
        limits.put(Rule.TRANSFER_USER, new Limit(p.transferPerMinute(), Duration.ofMinutes(1)));
        limits.put(Rule.PIN_CHANGE_USER, new Limit(p.pinChangePerWindow(), WINDOW));
        limits.put(Rule.RESET_USER, new Limit(p.resetPerHour(), Duration.ofHours(1)));
        limits.values().forEach(l -> {
            if (l.capacity() <= 0) {
                throw new IllegalStateException("Rate limits must be positive numbers");
            }
        });
    }

    public RateLimiter.Decision consume(Rule rule, String subject) {
        if (!props.enabled()) {
            return RateLimiter.Decision.ALLOWED;
        }
        Limit l = limits.get(rule);
        return limiter.tryConsume(rule + ":" + subject, l.capacity(), l.period());
    }

    public void consumeOrThrow(Rule rule, String subject) {
        RateLimiter.Decision d = consume(rule, subject);
        if (!d.allowed()) {
            throw new NepOpayException(ErrorCode.RATE_LIMITED,
                    "Too many attempts. Try again in about " + d.retryAfterSeconds() + " second(s).");
        }
    }
}
