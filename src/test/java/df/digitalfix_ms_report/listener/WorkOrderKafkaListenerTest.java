package df.digitalfix_ms_report.listener;

import df.digitalfix_ms_report.dto.event.WorkOrderEventDTO;
import df.digitalfix_ms_report.service.KpiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WorkOrderKafkaListenerTest {

    @Mock
    private KpiService kpiService;

    @InjectMocks
    private WorkOrderKafkaListener listener;

    @Test
    void shouldForwardValidEventToKpiService() {
        WorkOrderEventDTO event = WorkOrderEventDTO.builder()
                .eventId("evt-1")
                .orderId(10L)
                .newStatus("CREADA")
                .timestamp("2026-11-22T14:00:00Z")
                .build();

        listener.onWorkOrderEvent(event);

        verify(kpiService).processEvent(event);
    }

    @Test
    void shouldIgnoreNullPoisonPillPayload() {
        listener.onWorkOrderEvent(null);

        verifyNoInteractions(kpiService);
    }
}
