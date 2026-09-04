package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import java.util.List;

public interface DashboardOperationProvider {
    DashboardDomain domain();

    List<OperationalItemDTO> getOperations(DashboardRequestContext context);
}
