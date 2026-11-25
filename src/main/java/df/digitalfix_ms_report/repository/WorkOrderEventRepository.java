package df.digitalfix_ms_report.repository;

import df.digitalfix_ms_report.model.common.WorkOrderEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WorkOrderEventRepository extends JpaRepository<WorkOrderEventEntity, Long> {

    boolean existsByEventId(String eventId);

    /** Bitácora de auditoría: eventos desde {@code from}, opcionalmente de un solo estado, más recientes primero. */
    @Query("""
            select e from WorkOrderEventEntity e
            where e.occurredAt >= :from
              and (:status is null or e.newStatus = :status)
            order by e.occurredAt desc, e.id desc
            """)
    List<WorkOrderEventEntity> searchSince(@Param("from") Instant from,
                                           @Param("status") String status,
                                           Pageable pageable);

    /** Línea de tiempo completa de una orden, en orden cronológico. */
    List<WorkOrderEventEntity> findByOrderIdOrderByOccurredAtAscIdAsc(Long orderId);

    /** Eventos de la ventana de reportería, en orden cronológico. */
    List<WorkOrderEventEntity> findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(Instant from);
}
