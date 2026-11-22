package df.digitalfix_ms_report.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderEventDTO {
    private String eventId;
    private String eventType;
    private Long orderId;
    private String previousStatus;
    private String newStatus;
    private Long assignedTechnicianId;
    private Long clientId;
    private String timestamp; // ISO-8601 UTC
    private String traceId;
}
