package df.digitalfix_ms_report.listener;

import df.digitalfix_ms_report.dto.event.WorkOrderEventDTO;
import df.digitalfix_ms_report.service.KpiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderKafkaListener {

    private final KpiService kpiService;

    @KafkaListener(
            topics = "${kafka.topics.workorders-events:workorders.events}",
            groupId = "${spring.kafka.consumer.group-id:report-kpi-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onWorkOrderEvent(WorkOrderEventDTO event) {
        if (event == null) {
            log.warn("Received null or undeserializable WorkOrderEventDTO payload; skipping record");
            return;
        }
        log.debug("Processing workorder eventId={} orderId={} newStatus={} traceId={}",
                event.getEventId(), event.getOrderId(), event.getNewStatus(), event.getTraceId());
        kpiService.processEvent(event);
    }
}
