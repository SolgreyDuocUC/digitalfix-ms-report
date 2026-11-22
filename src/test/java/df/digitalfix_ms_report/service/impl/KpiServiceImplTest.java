package df.digitalfix_ms_report.service.impl;

import df.digitalfix_ms_report.dto.event.WorkOrderEventDTO;
import df.digitalfix_ms_report.dto.kpi.KpiReportResponseDTO;
import df.digitalfix_ms_report.exception.InvalidRangeException;
import df.digitalfix_ms_report.model.common.WorkOrderStateEntity;
import df.digitalfix_ms_report.repository.WorkOrderEventRepository;
import df.digitalfix_ms_report.repository.WorkOrderStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");

    @Mock
    private WorkOrderEventRepository eventRepository;

    @Mock
    private WorkOrderStateRepository stateRepository;

    private KpiServiceImpl kpiService;

    @BeforeEach
    void setUp() {
        kpiService = new KpiServiceImpl(
                eventRepository,
                stateRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldTrackLifecycleNormalizeAccentsAndCalculateResolutionAverage() {
        when(eventRepository.existsByEventId(any())).thenReturn(false);

        // Order 101: CREADA -> ASIGNADA -> EN_EJECUCIÓN (with accent) -> CERRADA (30 mins)
        kpiService.processEvent(event("ev-1", 101L, null, "CREADA", "2026-11-22T14:00:00Z"));
        kpiService.processEvent(event("ev-2", 101L, "CREADA", "ASIGNADA", "2026-11-22T14:05:00Z"));
        kpiService.processEvent(event("ev-3", 101L, "ASIGNADA", "EN_EJECUCIÓN", "2026-11-22T14:15:00Z"));
        kpiService.processEvent(event("ev-4", 101L, "EN_EJECUCIÓN", "CERRADA", "2026-11-22T14:30:00Z"));

        // Order 102: Active in EN_DESPLAZAMIENTO
        kpiService.processEvent(event("ev-5", 102L, null, "CREADA", "2026-11-22T14:10:00Z"));
        kpiService.processEvent(event("ev-6", 102L, "CREADA", "EN_DESPLAZAMIENTO", "2026-11-22T14:20:00Z"));

        // Order 103: Active in EN_EJECUCION (without accent)
        kpiService.processEvent(event("ev-7", 103L, null, "CREADA", "2026-11-22T14:30:00Z"));
        kpiService.processEvent(event("ev-8", 103L, "CREADA", "EN_EJECUCION", "2026-11-22T14:40:00Z"));

        KpiReportResponseDTO kpis = kpiService.getKpis("last24h");

        assertEquals(3L, kpis.getTotalOrdersLast24h());
        assertEquals(0.13, kpis.getOrdersPerHour());
        assertEquals(30.0, kpis.getAvgResolutionTimeMinutes());
        assertEquals(0L, kpis.getActiveStatusCount().get("CREADA"));
        assertEquals(0L, kpis.getActiveStatusCount().get("ASIGNADA"));
        assertEquals(1L, kpis.getActiveStatusCount().get("EN_DESPLAZAMIENTO"));
        assertEquals(1L, kpis.getActiveStatusCount().get("EN_EJECUCION"));
    }

    @Test
    void shouldIgnoreResolutionTimeWhenClosedWithoutPriorCreatedEvent() {
        when(eventRepository.existsByEventId(any())).thenReturn(false);

        kpiService.processEvent(event("ev-orphan", 201L, "EN_EJECUCION", "CERRADA", "2026-11-22T14:45:00Z"));

        KpiReportResponseDTO kpis = kpiService.getKpis("last24h");

        assertEquals(1L, kpis.getTotalOrdersLast24h());
        assertEquals(0.0, kpis.getAvgResolutionTimeMinutes());
        assertEquals(0L, kpis.getActiveStatusCount().get("EN_EJECUCION"));
    }

    @Test
    void shouldDeduplicateEventsByEventId() {
        when(eventRepository.existsByEventId("dup-1")).thenReturn(false);

        WorkOrderEventDTO duplicateEvent = event("dup-1", 301L, null, "CREADA", "2026-11-22T14:50:00Z");
        kpiService.processEvent(duplicateEvent);
        kpiService.processEvent(duplicateEvent);

        KpiReportResponseDTO kpis = kpiService.getKpis("last1h");

        assertEquals(1L, kpis.getTotalOrdersLast24h());
        assertEquals(1.0, kpis.getOrdersPerHour());
        assertEquals(1L, kpis.getActiveStatusCount().get("CREADA"));
        verify(eventRepository, times(1)).save(any());
    }

    @Test
    void shouldFilterMetricsByRangeAndRejectInvalidRange() {
        when(eventRepository.existsByEventId(any())).thenReturn(false);

        // Order within last1h and closed in 10 mins
        kpiService.processEvent(event("r-1", 401L, null, "CREADA", "2026-11-22T14:30:00Z"));
        kpiService.processEvent(event("r-2", 401L, "CREADA", "CERRADA", "2026-11-22T14:40:00Z"));

        // Order 3 hours ago (in last24h, outside last1h) closed in 50 mins
        kpiService.processEvent(event("r-3", 402L, null, "CREADA", "2026-11-22T11:10:00Z"));
        kpiService.processEvent(event("r-4", 402L, "CREADA", "CERRADA", "2026-11-22T12:00:00Z"));

        KpiReportResponseDTO last1h = kpiService.getKpis("last1h");
        assertEquals(1L, last1h.getTotalOrdersLast24h());
        assertEquals(10.0, last1h.getAvgResolutionTimeMinutes());

        KpiReportResponseDTO last24h = kpiService.getKpis("last24h");
        assertEquals(2L, last24h.getTotalOrdersLast24h());
        assertEquals(30.0, last24h.getAvgResolutionTimeMinutes());

        KpiReportResponseDTO last7d = kpiService.getKpis("last7d");
        assertEquals(2L, last7d.getTotalOrdersLast24h());

        assertThrows(InvalidRangeException.class, () -> kpiService.getKpis("last30d"));
    }

    @Test
    void shouldWarmupStateFromOracleStorage() {
        WorkOrderStateEntity activeState = WorkOrderStateEntity.builder()
                .orderId(501L)
                .currentStatus("ASIGNADA")
                .createdAt(NOW.minusSeconds(1800))
                .firstSeenAt(NOW.minusSeconds(1800))
                .updatedAt(NOW.minusSeconds(1200))
                .build();

        WorkOrderStateEntity closedState = WorkOrderStateEntity.builder()
                .orderId(502L)
                .currentStatus("CERRADA")
                .createdAt(NOW.minusSeconds(3600))
                .firstSeenAt(NOW.minusSeconds(3600))
                .closedAt(NOW.minusSeconds(1200))
                .resolutionMillis(2_400_000L) // 40 min
                .updatedAt(NOW.minusSeconds(1200))
                .build();

        when(stateRepository.findByCurrentStatusIn(any())).thenReturn(List.of(activeState));
        when(stateRepository.findByFirstSeenAtAfterOrClosedAtAfter(any(), any()))
                .thenReturn(List.of(activeState, closedState));

        kpiService.warmupFromStorage();
        KpiReportResponseDTO kpis = kpiService.getKpis("last24h");

        assertEquals(2L, kpis.getTotalOrdersLast24h());
        assertEquals(40.0, kpis.getAvgResolutionTimeMinutes());
        assertEquals(1L, kpis.getActiveStatusCount().get("ASIGNADA"));
    }

    @Test
    void shouldHandleConcurrentEventsSafely() throws InterruptedException {
        when(eventRepository.existsByEventId(any())).thenReturn(false);

        int orders = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(orders);

        for (int i = 0; i < orders; i++) {
            final long orderId = 1000L + i;
            pool.submit(() -> {
                try {
                    kpiService.processEvent(event("c1-" + orderId, orderId, null, "CREADA", "2026-11-22T14:00:00Z"));
                    kpiService.processEvent(event("c2-" + orderId, orderId, "CREADA", "ASIGNADA", "2026-11-22T14:10:00Z"));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();

        KpiReportResponseDTO kpis = kpiService.getKpis("last24h");
        assertEquals(40L, kpis.getTotalOrdersLast24h());
        assertEquals(0L, kpis.getActiveStatusCount().get("CREADA"));
        assertEquals(40L, kpis.getActiveStatusCount().get("ASIGNADA"));
    }

    private WorkOrderEventDTO event(
            String eventId,
            Long orderId,
            String previousStatus,
            String newStatus,
            String timestamp
    ) {
        return WorkOrderEventDTO.builder()
                .eventId(eventId)
                .eventType("WORK_ORDER_STATUS_CHANGED")
                .orderId(orderId)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .assignedTechnicianId(77L)
                .clientId(88L)
                .timestamp(timestamp)
                .traceId("trace-" + eventId)
                .build();
    }
}
