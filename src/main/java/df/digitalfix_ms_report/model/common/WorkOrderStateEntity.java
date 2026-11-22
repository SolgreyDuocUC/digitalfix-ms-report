package df.digitalfix_ms_report.model.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "WORK_ORDER_STATES")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderStateEntity {

    @Id
    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "CURRENT_STATUS", nullable = false, length = 50)
    private String currentStatus;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    @Column(name = "FIRST_SEEN_AT", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "CLOSED_AT")
    private Instant closedAt;

    @Column(name = "RESOLUTION_MILLIS")
    private Long resolutionMillis;

    @Column(name = "LAST_EVENT_ID", length = 100)
    private String lastEventId;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;
}
