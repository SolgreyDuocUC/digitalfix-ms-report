package df.digitalfix_ms_report.controller;

import df.digitalfix_ms_report.dto.report.OrderEventResponseDTO;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO;
import df.digitalfix_ms_report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reportería (series de tiempo, resolución, técnicos) y bitácora de auditoría de las órdenes.
 * La reportería es para Admin y Supervisor; la bitácora también la consulta el Auditor.
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<ReportSummaryResponseDTO> getSummary(
            @RequestParam(name = "range", defaultValue = "last24h") String range
    ) {
        return ResponseEntity.ok(reportService.getSummary(range));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<OrderEventResponseDTO>> getEvents(
            @RequestParam(name = "range", defaultValue = "last24h") String range,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        return ResponseEntity.ok(reportService.getEvents(range, status, limit));
    }

    @GetMapping("/orders/{orderId}/timeline")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<OrderEventResponseDTO>> getOrderTimeline(@PathVariable Long orderId) {
        return ResponseEntity.ok(reportService.getOrderTimeline(orderId));
    }
}
