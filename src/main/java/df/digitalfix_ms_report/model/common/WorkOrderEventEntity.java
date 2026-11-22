package df.digitalfix_ms_report.model.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "WORK_ORDER_EVENTS")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EVENT_ID", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "EVENT_TYPE", length = 80)
    private String eventType;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "PREVIOUS_STATUS", length = 50)
    private String previousStatus;

    @Column(name = "NEW_STATUS", nullable = false, length = 50)
    private String newStatus;

    @Column(name = "ASSIGNED_TECHNICIAN_ID")
    private Long assignedTechnicianId;

    @Column(name = "CLIENT_ID")
    private Long clientId;

    @Column(name = "OCCURRED_AT", nullable = false)
    private Instant occurredAt;

    @Column(name = "TRACE_ID", length = 100)
    private String traceId;

    @Column(name = "RECEIVED_AT", nullable = false)
    private Instant receivedAt;
}
