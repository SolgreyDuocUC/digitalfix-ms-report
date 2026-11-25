package df.digitalfix_ms_report.dto.report;

import df.digitalfix_ms_report.model.common.WorkOrderEventEntity;

/**
 * Un cambio de estado de una orden de trabajo, tal como llegó por Kafka desde ms-workorders.
 * Las fechas van como texto ISO-8601 UTC para no depender de la configuración de Jackson.
 */
public record OrderEventResponseDTO(
        Long id,
        String eventId,
        String eventType,
        Long orderId,
        String previousStatus,
        String newStatus,
        Long assignedTechnicianId,
        Long clientId,
        String occurredAt,
        String receivedAt,
        String traceId
) {
    public static OrderEventResponseDTO from(WorkOrderEventEntity e) {
        return new OrderEventResponseDTO(
                e.getId(),
                e.getEventId(),
                e.getEventType(),
                e.getOrderId(),
                e.getPreviousStatus(),
                e.getNewStatus(),
                e.getAssignedTechnicianId(),
                e.getClientId(),
                e.getOccurredAt() != null ? e.getOccurredAt().toString() : null,
                e.getReceivedAt() != null ? e.getReceivedAt().toString() : null,
                e.getTraceId()
        );
    }
}
