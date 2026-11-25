package df.digitalfix_ms_report.dto.report;

import java.util.List;
import java.util.Map;

/**
 * Resumen de reportería para una ventana de tiempo (last1h, last24h o last7d), calculado
 * sobre la bitácora persistida WORK_ORDER_EVENTS / WORK_ORDER_STATES.
 */
public record ReportSummaryResponseDTO(
        String range,
        String from,
        String to,
        int bucketMinutes,
        List<Bucket> series,
        Totals totals,
        Map<String, Long> eventsByStatus,
        Resolution resolution,
        List<TechnicianClosures> topTechnicians
) {
    /** Órdenes creadas, cerradas y canceladas en un tramo [start, start + bucketMinutes). */
    public record Bucket(String start, long created, long closed, long cancelled) {
    }

    public record Totals(long created, long closed, long cancelled, long events, long distinctOrders) {
    }

    /** Tiempo de resolución (Creada → Cerrada) de las órdenes cerradas en la ventana. */
    public record Resolution(long samples, double avgMinutes, double p50Minutes, double p90Minutes, double maxMinutes) {
    }

    public record TechnicianClosures(Long technicianId, long closed) {
    }
}
