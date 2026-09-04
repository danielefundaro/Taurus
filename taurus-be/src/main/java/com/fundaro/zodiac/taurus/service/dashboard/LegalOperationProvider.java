package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.service.LegalService;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalOperationProvider implements DashboardOperationProvider {

    private final LegalService legalService;

    public LegalOperationProvider(LegalService legalService) {
        this.legalService = legalService;
    }

    @Override
    public DashboardDomain domain() {
        return DashboardDomain.LEGAL;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
        LegalStatusDTO status = legalService.getStatus(context.authentication());
        if (status.compliant()) return List.of();
        long missing = status.documents().stream().filter(document -> document.required() && !document.accepted()).count();
        return List.of(new OperationalItemDTO(
            DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED.name(),
            DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED,
            DashboardDomain.LEGAL,
            DashboardSeverity.DANGER,
            missing,
            null,
            "Documenti legali da accettare",
            "Completa l’accettazione dei documenti richiesti per continuare.",
            null,
            "Continua",
            "/legal/accept"
        ));
    }

}
