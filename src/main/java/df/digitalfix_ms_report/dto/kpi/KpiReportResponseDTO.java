package df.digitalfix_ms_report.dto.kpi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiReportResponseDTO {
    private long totalOrdersLast24h;
    private double ordersPerHour;
    private double avgResolutionTimeMinutes;
    private Map<String, Long> activeStatusCount;
}
