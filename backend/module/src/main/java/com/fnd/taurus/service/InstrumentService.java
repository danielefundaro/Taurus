package com.fnd.taurus.service;

import com.fnd.taurus.dto.InstrumentDTO;
import com.fnd.taurus.entity.Instrument;
import com.fnd.taurus.repository.InstrumentRepository;

public interface InstrumentService extends CommonService<Instrument, InstrumentDTO, InstrumentRepository> {
}
