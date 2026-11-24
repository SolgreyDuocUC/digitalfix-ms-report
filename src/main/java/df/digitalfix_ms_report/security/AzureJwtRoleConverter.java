package df.digitalfix_ms_report.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts roles from the JWT "roles" claim and, when available, from ms-user (/api/user/me),
 * normalizing them as ROLE_* authorities.
 */
public class AzureJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "roles";

    private final UserRoleClient userRoleClient;

    public AzureJwtRoleConverter() {
        this(null);
    }

    public AzureJwtRoleConverter(UserRoleClient userRoleClient) {
        this.userRoleClient = userRoleClient;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Object claim = jwt.getClaim(ROLES_CLAIM);

        if (claim instanceof Collection<?> roleCollection) {
            roleCollection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(this::toRoleAuthority)
                    .forEach(authorities::add);
        } else if (claim instanceof String rolesString && !rolesString.isBlank()) {
            Arrays.stream(rolesString.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(this::toRoleAuthority)
                    .forEach(authorities::add);
        }

        if (userRoleClient != null) {
            String oid = jwt.getClaimAsString("oid");
            String userEntraId = oid != null && !oid.isBlank() ? oid : jwt.getSubject();
            userRoleClient.findRoleCode(userEntraId, jwt.getTokenValue())
                    .map(this::toRoleAuthority)
                    .ifPresent(authorities::add);
        }

        return authorities;
    }

    private GrantedAuthority toRoleAuthority(String rawRole) {
        String normalized = rawRole.toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return new SimpleGrantedAuthority(normalized);
    }
}
