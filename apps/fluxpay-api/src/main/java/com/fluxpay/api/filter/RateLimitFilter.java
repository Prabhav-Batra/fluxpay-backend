package com.fluxpay.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.JwtUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    
    // In a fully distributed production setup with Redis, we would use a ProxyManager backed by Redis here.
    // For this bootstrapped MVP, we use an in-memory concurrent map to track buckets per tenant to avoid 
    // complex Lettuce proxy configuration issues, but this can easily be swapped to a RedisProxyManager 
    // when scaling to multiple instances.
    private final ConcurrentMap<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String merchantId;
        
        try {
            merchantId = jwtUtil.extractUsername(token); // Or extract merchant ID from token claims
        } catch (Exception e) {
            // Invalid token, let Security filter handle it
            filterChain.doFilter(request, response);
            return;
        }
        
        if (merchantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = cache.computeIfAbsent(merchantId, this::createNewBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            ApiResponse<Object> apiResponse = ApiResponse.error("RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        }
    }

    private Bucket createNewBucket(String key) {
        // Standard SaaS Limits: 100 requests per minute per merchant
        io.github.bucket4j.Refill refill = io.github.bucket4j.Refill.greedy(100, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(100, refill);
                
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
