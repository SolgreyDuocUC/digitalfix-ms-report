package df.digitalfix_ms_report.controller;

import df.digitalfix_ms_report.dto.kpi.KpiReportResponseDTO;
import df.digitalfix_ms_report.service.KpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class KpiController {

    private final KpiService kpiService;

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<KpiReportResponseDTO> getKpis(
            @RequestParam(name = "range", defaultValue = "last24h") String range
    ) {
        return ResponseEntity.ok(kpiService.getKpis(range));
    }
}
