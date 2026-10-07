package com.nepopay.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.ErrorCode;
import com.nepopay.ratelimit.RateLimitService.Rule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs inside the security chain AFTER the bearer token is read, so "per user" rules know who is calling.
 * NOT a @Component on purpose: Boot would register it a second time outside the security chain.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Route(String method, String path, Rule rule, boolean perUser) {
    }

    private static final List<Route> ROUTES = List.of(
            new Route("POST", "/api/v1/auth/login", Rule.LOGIN_IP, false),
            new Route("POST", "/api/v1/auth/register", Rule.REGISTER_IP, false),
            new Route("POST", "/api/v1/auth/refresh", Rule.REFRESH_IP, false),
            new Route("GET", "/api/v1/users/search", Rule.SEARCH_USER, true),
            new Route("POST", "/api/v1/transfers", Rule.TRANSFER_USER, true),
            new Route("PATCH", "/api/v1/users/me/pin", Rule.PIN_CHANGE_USER, true),
            new Route("PATCH", "/api/v1/users/me/password", Rule.PIN_CHANGE_USER, true),
            new Route("POST", "/api/v1/demo/reset", Rule.RESET_USER, true));

    private final RateLimitService limits;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimitService limits, ObjectMapper mapper) {
        this.limits = limits;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Route route = ROUTES.stream()
                .filter(r -> r.method().equalsIgnoreCase(request.getMethod()) && r.path().equals(request.getRequestURI()))
                .findFirst().orElse(null);
        if (route == null) {
            chain.doFilter(request, response);
            return;
        }

        String subject = route.perUser() ? authenticatedSubject() : request.getRemoteAddr();
        if (subject == null) { // not logged in: the security rules will answer 401, nothing to count
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision = limits.consume(route.rule(), subject);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        ErrorCode code = ErrorCode.RATE_LIMITED;
        response.setStatus(code.getStatus().value());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(code,
                "Too many requests. Try again in about " + decision.retryAfterSeconds() + " second(s)."));
    }

    private static String authenticatedSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken ? auth.getName() : null;
    }
}
