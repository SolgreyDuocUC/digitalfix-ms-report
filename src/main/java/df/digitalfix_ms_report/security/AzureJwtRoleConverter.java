package df.digitalfix_ms_report.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Extracts Azure Entra ID roles from the JWT "roles" claim and normalizes them as ROLE_* authorities.
 */
public class AzureJwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object claim = jwt.getClaim(ROLES_CLAIM);
        if (claim == null) {
            return Collections.emptyList();
        }

        if (claim instanceof Collection<?> roleCollection) {
            return roleCollection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(this::toRoleAuthority)
                    .collect(Collectors.toSet());
        }

        if (claim instanceof String rolesString && !rolesString.isBlank()) {
            return Arrays.stream(rolesString.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(this::toRoleAuthority)
                    .collect(Collectors.toSet());
        }

        return Collections.emptyList();
    }

    private GrantedAuthority toRoleAuthority(String rawRole) {
        String normalized = rawRole.toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }
        return new SimpleGrantedAuthority(normalized);
    }
}
