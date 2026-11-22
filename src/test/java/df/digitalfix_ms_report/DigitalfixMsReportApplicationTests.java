package df.digitalfix_ms_report;

import df.digitalfix_ms_report.repository.WorkOrderEventRepository;
import df.digitalfix_ms_report.repository.WorkOrderStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DigitalfixMsReportApplicationTests {

    @MockitoBean
    private WorkOrderEventRepository workOrderEventRepository;

    @MockitoBean
    private WorkOrderStateRepository workOrderStateRepository;

    @Test
    void contextLoads() {
    }
}
