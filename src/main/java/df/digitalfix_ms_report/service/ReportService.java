package df.digitalfix_ms_report.service;

import df.digitalfix_ms_report.dto.report.OrderEventResponseDTO;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO;

import java.util.List;

/** Consultas de reportería y auditoría sobre la bitácora persistida de eventos de órdenes. */
public interface ReportService {

    ReportSummaryResponseDTO getSummary(String range);

    List<OrderEventResponseDTO> getEvents(String range, String status, int limit);

    List<OrderEventResponseDTO> getOrderTimeline(Long orderId);
}
