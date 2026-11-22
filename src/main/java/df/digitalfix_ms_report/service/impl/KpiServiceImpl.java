package df.digitalfix_ms_report.service.impl;

import df.digitalfix_ms_report.dto.event.WorkOrderEventDTO;
import df.digitalfix_ms_report.dto.kpi.KpiReportResponseDTO;
import df.digitalfix_ms_report.model.common.WorkOrderEventEntity;
import df.digitalfix_ms_report.model.common.WorkOrderStateEntity;
import df.digitalfix_ms_report.model.enums.KpiRange;
import df.digitalfix_ms_report.model.enums.WorkOrderStatus;
import df.digitalfix_ms_report.repository.WorkOrderEventRepository;
import df.digitalfix_ms_report.repository.WorkOrderStateRepository;
import df.digitalfix_ms_report.service.KpiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class KpiServiceImpl implements KpiService {

    private static final Duration MAX_RETENTION = Duration.ofDays(7);
    private static final int MAX_PROCESSED_EVENT_CACHE = 50_000;

    private final WorkOrderEventRepository eventRepository;
    private final WorkOrderStateRepository stateRepository;
    private final Clock clock;

    private final ConcurrentHashMap<Long, OrderRuntimeState> orderStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WorkOrderStatus, AtomicLong> activeStatusCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ResolutionSample> resolutionSamples = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalResolutionTimeMillis = new AtomicLong(0L);
    private final AtomicLong resolvedOrdersCount = new AtomicLong(0L);

    @Autowired
    public KpiServiceImpl(
            WorkOrderEventRepository eventRepository,
            WorkOrderStateRepository stateRepository
    ) {
        this(eventRepository, stateRepository, Clock.systemUTC());
    }

    KpiServiceImpl(
            WorkOrderEventRepository eventRepository,
            WorkOrderStateRepository stateRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.stateRepository = stateRepository;
        this.clock = clock;
        for (WorkOrderStatus status : WorkOrderStatus.activeStatuses()) {
            activeStatusCounts.put(status, new AtomicLong(0L));
        }
    }

    @Override
    @EventListener(ApplicationReadyEvent.class)
    public void warmupFromStorage() {
        try {
            Instant retentionCutoff = Instant.now(clock).minus(MAX_RETENTION);
            List<String> activeNames = WorkOrderStatus.activeStatuses().stream()
                    .map(Enum::name)
                    .toList();

            Map<Long, WorkOrderStateEntity> merged = new LinkedHashMap<>();
            List<WorkOrderStateEntity> activeEntities = stateRepository.findByCurrentStatusIn(activeNames);
            if (activeEntities != null) {
                activeEntities.forEach(e -> merged.put(e.getOrderId(), e));
            }

            List<WorkOrderStateEntity> recentEntities =
                    stateRepository.findByFirstSeenAtAfterOrClosedAtAfter(retentionCutoff, retentionCutoff);
            if (recentEntities != null) {
                recentEntities.forEach(e -> merged.put(e.getOrderId(), e));
            }

            for (WorkOrderStateEntity entity : merged.values()) {
                hydrateOrderState(entity, retentionCutoff);
            }
            log.info("Warmup completed: loaded {} order states into concurrent KPI memory", merged.size());
        } catch (Exception ex) {
            log.warn("Could not warm up KPI memory from storage: {}", ex.getMessage());
        }
    }

    @Override
    public void processEvent(WorkOrderEventDTO event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("Ignoring invalid work order event without orderId: {}", event);
            return;
        }

        WorkOrderStatus newStatus = WorkOrderStatus.fromString(event.getNewStatus());
        if (newStatus == WorkOrderStatus.DESCONOCIDO) {
            log.warn("Ignoring event {} for order {} with unknown newStatus={}",
                    event.getEventId(), event.getOrderId(), event.getNewStatus());
            return;
        }

        Instant now = Instant.now(clock);
        String eventId = event.getEventId();
        if (hasBeenProcessed(eventId, now)) {
            log.debug("Skipping duplicate eventId={} for orderId={}", eventId, event.getOrderId());
            return;
        }

        Instant occurredAt = parseTimestamp(event.getTimestamp(), now);
        OrderRuntimeState updatedState = applyStateTransition(event.getOrderId(), newStatus, occurredAt);

        persistEventAndState(event, newStatus, occurredAt, now, updatedState);
        pruneExpiredData(now);
    }

    @Override
    public KpiReportResponseDTO getKpis(String rangeParam) {
        KpiRange range = KpiRange.fromQueryParam(rangeParam);
        Instant cutoff = Instant.now(clock).minus(range.getDuration());

        long totalOrders = orderStates.values().stream()
                .filter(state -> !state.firstSeenAt().isBefore(cutoff))
                .count();

        double ordersPerHour = roundTwoDecimals(totalOrders / range.getHours());
        double avgResolutionMinutes = calculateAvgResolutionMinutes(cutoff);

        Map<String, Long> activeBreakdown = new LinkedHashMap<>();
        activeBreakdown.put(WorkOrderStatus.CREADA.name(), Math.max(0L, activeStatusCounts.get(WorkOrderStatus.CREADA).get()));
        activeBreakdown.put(WorkOrderStatus.ASIGNADA.name(), Math.max(0L, activeStatusCounts.get(WorkOrderStatus.ASIGNADA).get()));
        activeBreakdown.put(WorkOrderStatus.EN_DESPLAZAMIENTO.name(), Math.max(0L, activeStatusCounts.get(WorkOrderStatus.EN_DESPLAZAMIENTO).get()));
        activeBreakdown.put(WorkOrderStatus.EN_EJECUCION.name(), Math.max(0L, activeStatusCounts.get(WorkOrderStatus.EN_EJECUCION).get()));

        return KpiReportResponseDTO.builder()
                .totalOrdersLast24h(totalOrders)
                .ordersPerHour(ordersPerHour)
                .avgResolutionTimeMinutes(avgResolutionMinutes)
                .activeStatusCount(activeBreakdown)
                .build();
    }

    private void hydrateOrderState(WorkOrderStateEntity entity, Instant retentionCutoff) {
        WorkOrderStatus status = WorkOrderStatus.fromString(entity.getCurrentStatus());
        if (status == WorkOrderStatus.DESCONOCIDO || entity.getOrderId() == null) {
            return;
        }
        orderStates.compute(entity.getOrderId(), (id, existing) -> {
            if (existing != null) {
                return existing;
            }
            if (status.isActive()) {
                activeStatusCounts.get(status).incrementAndGet();
            }
            if (entity.getClosedAt() != null
                    && entity.getResolutionMillis() != null
                    && entity.getResolutionMillis() >= 0
                    && !entity.getClosedAt().isBefore(retentionCutoff)) {
                totalResolutionTimeMillis.addAndGet(entity.getResolutionMillis());
                resolvedOrdersCount.incrementAndGet();
                resolutionSamples.addLast(new ResolutionSample(entity.getClosedAt(), entity.getResolutionMillis()));
            }
            return new OrderRuntimeState(
                    status,
                    entity.getCreatedAt(),
                    entity.getFirstSeenAt(),
                    entity.getClosedAt(),
                    entity.getResolutionMillis()
            );
        });
    }

    private boolean hasBeenProcessed(String eventId, Instant now) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        if (processedEventIds.containsKey(eventId)) {
            return true;
        }
        if (eventRepository.existsByEventId(eventId)) {
            processedEventIds.putIfAbsent(eventId, now);
            return true;
        }
        return processedEventIds.putIfAbsent(eventId, now) != null;
    }

    private OrderRuntimeState applyStateTransition(Long orderId, WorkOrderStatus newStatus, Instant occurredAt) {
        return orderStates.compute(orderId, (id, current) -> {
            WorkOrderStatus oldStatus = current != null ? current.status() : null;

            if (oldStatus != null && oldStatus.isActive() && oldStatus != newStatus) {
                activeStatusCounts.get(oldStatus).decrementAndGet();
            }
            if ((oldStatus == null || oldStatus != newStatus) && newStatus.isActive()) {
                activeStatusCounts.get(newStatus).incrementAndGet();
            }

            Instant createdAt = current != null ? current.createdAt() : null;
            if (newStatus == WorkOrderStatus.CREADA && createdAt == null) {
                createdAt = occurredAt;
            }

            Instant firstSeenAt = current != null ? current.firstSeenAt() : occurredAt;
            Instant closedAt = current != null ? current.closedAt() : null;
            Long resolutionMillis = current != null ? current.resolutionMillis() : null;

            if (newStatus == WorkOrderStatus.CERRADA && closedAt == null) {
                closedAt = occurredAt;
                if (createdAt != null) {
                    long diffMillis = Duration.between(createdAt, occurredAt).toMillis();
                    if (diffMillis >= 0) {
                        resolutionMillis = diffMillis;
                        totalResolutionTimeMillis.addAndGet(diffMillis);
                        resolvedOrdersCount.incrementAndGet();
                        resolutionSamples.addLast(new ResolutionSample(occurredAt, diffMillis));
                    } else {
                        log.warn("Negative resolution duration for orderId={} (createdAt={}, closedAt={}); ignoring resolution time",
                                orderId, createdAt, occurredAt);
                    }
                } else {
                    log.debug("Ignoring resolution time for orderId={} closed without prior CREADA event", orderId);
                }
            }

            return new OrderRuntimeState(newStatus, createdAt, firstSeenAt, closedAt, resolutionMillis);
        });
    }

    private void persistEventAndState(
            WorkOrderEventDTO event,
            WorkOrderStatus newStatus,
            Instant occurredAt,
            Instant receivedAt,
            OrderRuntimeState state
    ) {
        String persistedEventId = (event.getEventId() != null && !event.getEventId().isBlank())
                ? event.getEventId()
                : event.getOrderId() + "-" + newStatus.name() + "-" + occurredAt.toEpochMilli();

        try {
            WorkOrderEventEntity eventEntity = WorkOrderEventEntity.builder()
                    .eventId(persistedEventId)
                    .eventType(event.getEventType())
                    .orderId(event.getOrderId())
                    .previousStatus(event.getPreviousStatus())
                    .newStatus(newStatus.name())
                    .assignedTechnicianId(event.getAssignedTechnicianId())
                    .clientId(event.getClientId())
                    .occurredAt(occurredAt)
                    .traceId(event.getTraceId())
                    .receivedAt(receivedAt)
                    .build();
            eventRepository.save(eventEntity);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate event persisted concurrently eventId={}", persistedEventId);
        }

        WorkOrderStateEntity stateEntity = WorkOrderStateEntity.builder()
                .orderId(event.getOrderId())
                .currentStatus(state.status().name())
                .createdAt(state.createdAt())
                .firstSeenAt(state.firstSeenAt())
                .closedAt(state.closedAt())
                .resolutionMillis(state.resolutionMillis())
                .lastEventId(persistedEventId)
                .updatedAt(receivedAt)
                .build();
        stateRepository.save(stateEntity);
    }

    private double calculateAvgResolutionMinutes(Instant cutoff) {
        long sumMillis = 0L;
        long count = 0L;
        for (ResolutionSample sample : resolutionSamples) {
            if (!sample.closedAt().isBefore(cutoff)) {
                sumMillis += sample.resolutionMillis();
                count++;
            }
        }
        if (count == 0L) {
            return 0.0;
        }
        double avgMinutes = (sumMillis / (double) count) / 60_000.0;
        return roundTwoDecimals(avgMinutes);
    }

    private void pruneExpiredData(Instant now) {
        Instant retentionCutoff = now.minus(MAX_RETENTION);

        while (true) {
            ResolutionSample head = resolutionSamples.peekFirst();
            if (head == null || !head.closedAt().isBefore(retentionCutoff)) {
                break;
            }
            resolutionSamples.pollFirst();
        }

        if (processedEventIds.size() > MAX_PROCESSED_EVENT_CACHE) {
            processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(retentionCutoff));
        }

        orderStates.entrySet().removeIf(entry ->
                !entry.getValue().status().isActive() && entry.getValue().firstSeenAt().isBefore(retentionCutoff));
    }

    private Instant parseTimestamp(String timestamp, Instant fallback) {
        if (timestamp == null || timestamp.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(timestamp.trim());
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(timestamp.trim()).toInstant();
            } catch (DateTimeParseException nestedEx) {
                log.warn("Invalid ISO-8601 timestamp '{}', falling back to current time", timestamp);
                return fallback;
            }
        }
    }

    private double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record OrderRuntimeState(
            WorkOrderStatus status,
            Instant createdAt,
            Instant firstSeenAt,
            Instant closedAt,
            Long resolutionMillis
    ) {
    }

    private record ResolutionSample(
            Instant closedAt,
            long resolutionMillis
    ) {
    }
}
