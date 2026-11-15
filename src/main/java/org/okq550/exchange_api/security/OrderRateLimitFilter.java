package org.okq550.exchange_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/*
Spring Security runs first, so by the time this filter runs, the request is authenticated, and auth.getName() is the username. Unauthenticated requests were already rejected with 401.
computeIfAbsent creates a user’s bucket on their first request, atomically, so two simultaneous first requests can’t create two buckets.
Retry-After: 1 is the polite part of a 429: it tells a well-behaved client how long to wait before trying again.
*/

@Component
public class OrderRateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // Only limit order placement and cancels
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/orders");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = (auth != null) ? auth.getName() : "anonymous";

        TokenBucket bucket = buckets.computeIfAbsent(user, u -> new TokenBucket(20, 10));
        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "1");
            response.getWriter().write("Too many requests");
            return;   // stop here: the request never reaches the controller
        }
        chain.doFilter(request, response);   // within the limit: carry on
    }
}
