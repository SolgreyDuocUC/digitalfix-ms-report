package df.digitalfix_ms_report.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queries ms-user (/api/user/me) to resolve the business role (codigoRol) for an Entra ID user.
 */
@Slf4j
@Service
public class UserRoleClient {

    private static final Duration ROLE_TTL = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private record CachedRole(String role, Instant expiresAt) {
    }

    private final RestClient restClient;
    private final Map<String, CachedRole> cache = new ConcurrentHashMap<>();

    public UserRoleClient(@Value("${digitalfix.ms-user.url:http://localhost:8082}") String msUserUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(msUserUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<String> findRoleCode(String userEntraId, String bearerToken) {
        if (userEntraId == null || userEntraId.isBlank() || bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }

        CachedRole cached = cache.get(userEntraId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return Optional.of(cached.role());
        }

        try {
            Optional<String> role = fetchRoleCode(bearerToken);
            if (role.isPresent()) {
                cache.put(userEntraId, new CachedRole(role.get(), Instant.now().plus(ROLE_TTL)));
            } else {
                log.warn("ms-user (/api/user/me) did not return 'codigoRol' for userEntraId={}", userEntraId);
            }
            return role;
        } catch (RestClientException e) {
            log.warn("Could not fetch role from ms-user for {}: {}", userEntraId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchRoleCode(String bearerToken) {
        Map<?, ?> profile = restClient.get()
                .uri("/api/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .body(Map.class);
        Object code = profile != null ? profile.get("codigoRol") : null;
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.toString().trim().toUpperCase();
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }
}
