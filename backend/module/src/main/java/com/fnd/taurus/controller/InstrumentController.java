package com.fnd.taurus.controller;

import com.fnd.taurus.dto.InstrumentDTO;
import com.fnd.taurus.entity.Instrument;
import com.fnd.taurus.repository.InstrumentRepository;
import com.fnd.taurus.service.InstrumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "instruments")
@RestController
@Validated
public class InstrumentController implements CommonController<Instrument, InstrumentDTO, InstrumentRepository, InstrumentService> {
    private final InstrumentService instrumentService;

    @Autowired
    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @Override
    public InstrumentService getService() {
        return instrumentService;
    }
}
