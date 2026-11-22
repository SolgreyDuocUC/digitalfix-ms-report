package df.digitalfix_ms_report.model.enums;

import df.digitalfix_ms_report.exception.InvalidRangeException;
import lombok.Getter;

import java.time.Duration;
import java.util.Locale;

@Getter
public enum KpiRange {
    LAST_1H("last1h", Duration.ofHours(1), 1.0),
    LAST_24H("last24h", Duration.ofHours(24), 24.0),
    LAST_7D("last7d", Duration.ofDays(7), 168.0);

    private final String paramValue;
    private final Duration duration;
    private final double hours;

    KpiRange(String paramValue, Duration duration, double hours) {
        this.paramValue = paramValue;
        this.duration = duration;
        this.hours = hours;
    }

    public static KpiRange fromQueryParam(String value) {
        if (value == null || value.isBlank()) {
            return LAST_24H;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (KpiRange range : values()) {
            if (range.paramValue.equals(normalized)) {
                return range;
            }
        }
        throw new InvalidRangeException(
                "Invalid range '" + value + "'. Supported values: last1h, last24h, last7d"
        );
    }
}
