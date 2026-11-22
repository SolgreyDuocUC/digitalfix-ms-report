package df.digitalfix_ms_report.repository;

import df.digitalfix_ms_report.model.common.WorkOrderStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface WorkOrderStateRepository extends JpaRepository<WorkOrderStateEntity, Long> {
    List<WorkOrderStateEntity> findByCurrentStatusIn(Collection<String> statuses);

    List<WorkOrderStateEntity> findByFirstSeenAtAfterOrClosedAtAfter(Instant firstSeenCutoff, Instant closedCutoff);
}
