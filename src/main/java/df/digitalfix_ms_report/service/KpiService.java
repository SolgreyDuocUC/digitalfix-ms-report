package df.digitalfix_ms_report.service;

import df.digitalfix_ms_report.dto.event.WorkOrderEventDTO;
import df.digitalfix_ms_report.dto.kpi.KpiReportResponseDTO;

public interface KpiService {
    void processEvent(WorkOrderEventDTO event);

    KpiReportResponseDTO getKpis(String range);

    void warmupFromStorage();
}
