package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link Instruments} and its DTO {@link InstrumentsDTO}.
 */
@Mapper(componentModel = "spring")
public interface InstrumentsMapper extends EntityOpenSearchMapper<InstrumentsDTO, Instruments> {
}
