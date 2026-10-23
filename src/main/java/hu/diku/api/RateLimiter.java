package hu.diku.api;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Single-node protection. Deployments must also apply limits at their trusted edge. */
@Component
class RateLimiter {
    private record Bucket(Instant expiresAt, int count) {}
    private final Map<String,Bucket> buckets = new HashMap<>();
    synchronized void reset(String key) { buckets.remove(key); }
    synchronized void check(String key, int limit, Duration period) {
        Instant now = Instant.now();
        buckets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        Bucket old = buckets.get(key);
        if ((old != null && old.count() >= limit) || (old == null && buckets.size() >= 10000))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Túl sok kérés. Próbáld meg később.");
        buckets.put(key, old == null ? new Bucket(now.plus(period),1) : new Bucket(old.expiresAt(),old.count()+1));
    }
}
