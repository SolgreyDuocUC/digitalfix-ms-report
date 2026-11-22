package df.digitalfix_ms_report.repository;

import df.digitalfix_ms_report.model.common.WorkOrderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderEventRepository extends JpaRepository<WorkOrderEventEntity, Long> {
    boolean existsByEventId(String eventId);
}
