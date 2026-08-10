package com.fundaro.zodiac.taurus.service.dto;

import java.util.List;

public record LegalStatusDTO(boolean compliant, List<LegalDocumentStatusDTO> documents) {
}
