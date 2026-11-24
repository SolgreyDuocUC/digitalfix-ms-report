package df.digitalfix_ms_report.controller;

import df.digitalfix_ms_report.dto.kpi.KpiReportResponseDTO;
import df.digitalfix_ms_report.exception.GlobalExceptionHandler;
import df.digitalfix_ms_report.exception.InvalidRangeException;
import df.digitalfix_ms_report.security.SecurityConfig;
import df.digitalfix_ms_report.service.KpiService;
import org.junit.jupiter.api.Test;
import df.digitalfix_ms_report.security.UserRoleClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KpiController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class KpiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KpiService kpiService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRoleClient userRoleClient;

    @Test
    void shouldReturnUnauthorizedWhenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/report/kpis").param("range", "last24h"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenRoleIsNotSupervisorOrAdmin() throws Exception {
        when(jwtDecoder.decode("tech-token")).thenReturn(jwtWithRoles(List.of("Technician")));

        mockMvc.perform(get("/api/report/kpis")
                        .param("range", "last24h")
                        .header("Authorization", "Bearer tech-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnKpisForSupervisorRole() throws Exception {
        when(jwtDecoder.decode("supervisor-token")).thenReturn(jwtWithRoles(List.of("Supervisor")));

        Map<String, Long> activeMap = new LinkedHashMap<>();
        activeMap.put("CREADA", 2L);
        activeMap.put("ASIGNADA", 3L);
        activeMap.put("EN_DESPLAZAMIENTO", 1L);
        activeMap.put("EN_EJECUCION", 4L);

        KpiReportResponseDTO response = KpiReportResponseDTO.builder()
                .totalOrdersLast24h(24L)
                .ordersPerHour(1.0)
                .avgResolutionTimeMinutes(42.5)
                .activeStatusCount(activeMap)
                .build();

        when(kpiService.getKpis("last24h")).thenReturn(response);

        mockMvc.perform(get("/api/report/kpis")
                        .param("range", "last24h")
                        .header("Authorization", "Bearer supervisor-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrdersLast24h").value(24))
                .andExpect(jsonPath("$.ordersPerHour").value(1.0))
                .andExpect(jsonPath("$.avgResolutionTimeMinutes").value(42.5))
                .andExpect(jsonPath("$.activeStatusCount.CREADA").value(2))
                .andExpect(jsonPath("$.activeStatusCount.ASIGNADA").value(3))
                .andExpect(jsonPath("$.activeStatusCount.EN_DESPLAZAMIENTO").value(1))
                .andExpect(jsonPath("$.activeStatusCount.EN_EJECUCION").value(4));
    }

    @Test
    void shouldReturnKpisForAdminRoleAndHandleInvalidRange() throws Exception {
        when(jwtDecoder.decode("admin-token")).thenReturn(jwtWithRoles(List.of("Admin")));
        when(kpiService.getKpis("invalid"))
                .thenThrow(new InvalidRangeException("Invalid range 'invalid'. Supported values: last1h, last24h, last7d"));

        mockMvc.perform(get("/api/report/kpis")
                        .param("range", "invalid")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid range 'invalid'. Supported values: last1h, last24h, last7d"));
    }

    @Test
    void shouldReturnKpisForSuperadminResolvedFromMsUser() throws Exception {
        when(jwtDecoder.decode("superadmin-token")).thenReturn(jwtWithRoles(List.of()));
        when(userRoleClient.findRoleCode("user-123", "token")).thenReturn(java.util.Optional.of("SUPERADMIN"));
        when(kpiService.getKpis("last24h")).thenReturn(KpiReportResponseDTO.builder()
                .totalOrdersLast24h(0L)
                .ordersPerHour(0.0)
                .avgResolutionTimeMinutes(0.0)
                .activeStatusCount(Map.of())
                .build());

        mockMvc.perform(get("/api/report/kpis")
                        .param("range", "last24h")
                        .header("Authorization", "Bearer superadmin-token"))
                .andExpect(status().isOk());
    }

    private Jwt jwtWithRoles(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
