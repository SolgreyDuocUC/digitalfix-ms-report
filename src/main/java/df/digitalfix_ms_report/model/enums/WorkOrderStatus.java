package df.digitalfix_ms_report.model.enums;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public enum WorkOrderStatus {
    CREADA(true),
    ASIGNADA(true),
    EN_DESPLAZAMIENTO(true),
    EN_EJECUCION(true),
    CERRADA(false),
    CANCELADA(false),
    DESCONOCIDO(false);

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Set<WorkOrderStatus> ACTIVE_STATUSES = EnumSet.of(
            CREADA, ASIGNADA, EN_DESPLAZAMIENTO, EN_EJECUCION
    );

    private final boolean active;

    WorkOrderStatus(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public static Set<WorkOrderStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }

    /**
     * Normalizes status strings from upstream events (e.g. "EN_EJECUCIÓN" or "en-ejecucion" -> EN_EJECUCION).
     */
    public static WorkOrderStatus fromString(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return DESCONOCIDO;
        }
        String decomposed = Normalizer.normalize(rawStatus.trim(), Normalizer.Form.NFD);
        String withoutAccents = DIACRITICS.matcher(decomposed).replaceAll("");
        String normalized = withoutAccents
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        for (WorkOrderStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return DESCONOCIDO;
    }
}
