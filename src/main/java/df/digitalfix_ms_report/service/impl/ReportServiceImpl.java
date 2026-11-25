package df.digitalfix_ms_report.service.impl;

import df.digitalfix_ms_report.dto.report.OrderEventResponseDTO;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO.Bucket;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO.Resolution;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO.TechnicianClosures;
import df.digitalfix_ms_report.dto.report.ReportSummaryResponseDTO.Totals;
import df.digitalfix_ms_report.exception.InvalidRangeException;
import df.digitalfix_ms_report.model.common.WorkOrderEventEntity;
import df.digitalfix_ms_report.model.common.WorkOrderStateEntity;
import df.digitalfix_ms_report.model.enums.KpiRange;
import df.digitalfix_ms_report.model.enums.WorkOrderStatus;
import df.digitalfix_ms_report.repository.WorkOrderEventRepository;
import df.digitalfix_ms_report.repository.WorkOrderStateRepository;
import df.digitalfix_ms_report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final int MAX_EVENTS = 500;
    private static final int TOP_TECHNICIANS = 5;

    private final WorkOrderEventRepository eventRepository;
    private final WorkOrderStateRepository stateRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportSummaryResponseDTO getSummary(String rangeParam) {
        KpiRange range = KpiRange.fromQueryParam(rangeParam);
        Duration bucket = bucketFor(range);
        Instant now = Instant.now();
        // El primer tramo empieza alineado (p. ej. a la hora en punto) para que el eje sea legible.
        Instant from = align(now.minus(range.getDuration()), bucket);
        int bucketCount = (int) Math.ceil((double) Duration.between(from, now).toMillis() / bucket.toMillis());

        long[] created = new long[bucketCount];
        long[] closed = new long[bucketCount];
        long[] cancelled = new long[bucketCount];
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(WorkOrderStatus.values())
                .filter(s -> s != WorkOrderStatus.DESCONOCIDO)
                .forEach(s -> byStatus.put(s.name(), 0L));
        Map<Long, Long> closuresByTechnician = new HashMap<>();
        Set<Long> orders = new HashSet<>();

        List<WorkOrderEventEntity> events = eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(from);
        for (WorkOrderEventEntity e : events) {
            int i = (int) (Duration.between(from, e.getOccurredAt()).toMillis() / bucket.toMillis());
            if (i < 0 || i >= bucketCount) {
                continue;
            }
            orders.add(e.getOrderId());
            WorkOrderStatus status = WorkOrderStatus.fromString(e.getNewStatus());
            byStatus.merge(status.name(), 1L, Long::sum);
            switch (status) {
                case CREADA -> created[i]++;
                case CERRADA -> {
                    closed[i]++;
                    if (e.getAssignedTechnicianId() != null) {
                        closuresByTechnician.merge(e.getAssignedTechnicianId(), 1L, Long::sum);
                    }
                }
                case CANCELADA -> cancelled[i]++;
                default -> { }
            }
        }
        byStatus.remove(WorkOrderStatus.DESCONOCIDO.name());

        List<Bucket> series = new java.util.ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            series.add(new Bucket(from.plus(bucket.multipliedBy(i)).toString(), created[i], closed[i], cancelled[i]));
        }

        Totals totals = new Totals(
                Arrays.stream(created).sum(),
                Arrays.stream(closed).sum(),
                Arrays.stream(cancelled).sum(),
                events.size(),
                orders.size());

        List<TechnicianClosures> topTechnicians = closuresByTechnician.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_TECHNICIANS)
                .map(entry -> new TechnicianClosures(entry.getKey(), entry.getValue()))
                .toList();

        return new ReportSummaryResponseDTO(
                range.getParamValue(),
                from.toString(),
                now.toString(),
                (int) bucket.toMinutes(),
                series,
                totals,
                byStatus,
                resolution(from),
                topTechnicians);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderEventResponseDTO> getEvents(String rangeParam, String status, int limit) {
        KpiRange range = KpiRange.fromQueryParam(rangeParam);
        String statusFilter = null;
        if (status != null && !status.isBlank()) {
            WorkOrderStatus parsed = WorkOrderStatus.fromString(status);
            if (parsed == WorkOrderStatus.DESCONOCIDO) {
                throw new InvalidRangeException("Invalid status '" + status + "'");
            }
            statusFilter = parsed.name();
        }
        int size = Math.max(1, Math.min(limit, MAX_EVENTS));
        Instant from = Instant.now().minus(range.getDuration());
        return eventRepository.searchSince(from, statusFilter, PageRequest.of(0, size)).stream()
                .map(OrderEventResponseDTO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderEventResponseDTO> getOrderTimeline(Long orderId) {
        return eventRepository.findByOrderIdOrderByOccurredAtAscIdAsc(orderId).stream()
                .map(OrderEventResponseDTO::from)
                .toList();
    }

    private Resolution resolution(Instant from) {
        List<Long> millis = stateRepository.findByClosedAtGreaterThanEqualAndResolutionMillisIsNotNull(from).stream()
                .map(WorkOrderStateEntity::getResolutionMillis)
                .filter(m -> m >= 0)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (millis.isEmpty()) {
            return new Resolution(0, 0, 0, 0, 0);
        }
        double avg = millis.stream().mapToLong(Long::longValue).average().orElse(0);
        return new Resolution(
                millis.size(),
                minutes(avg),
                minutes(percentile(millis, 0.5)),
                minutes(percentile(millis, 0.9)),
                minutes(millis.get(millis.size() - 1)));
    }

    /** Percentil por rango más cercano sobre una lista ya ordenada. */
    private static double percentile(List<Long> sorted, double p) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double minutes(double millis) {
        return BigDecimal.valueOf(millis / 60_000.0).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 1 h → tramos de 5 min (12), 24 h → 1 h (24), 7 d → 6 h (28). */
    private static Duration bucketFor(KpiRange range) {
        return switch (range) {
            case LAST_1H -> Duration.ofMinutes(5);
            case LAST_24H -> Duration.ofHours(1);
            case LAST_7D -> Duration.ofHours(6);
        };
    }

    private static Instant align(Instant instant, Duration bucket) {
        long size = bucket.toMillis();
        long epoch = instant.truncatedTo(ChronoUnit.MINUTES).toEpochMilli();
        return Instant.ofEpochMilli(epoch - Math.floorMod(epoch, size));
    }
}
