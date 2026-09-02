package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InstrumentsServiceImpl extends CommonOpenSearchServiceImpl<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsMapper, InstrumentsRepository>
    implements InstrumentsService {

    private final UsersRepository usersRepository;

    public InstrumentsServiceImpl(InstrumentsRepository repository, InstrumentsMapper mapper, UsersRepository usersRepository) {
        super(repository, mapper, InstrumentsService.class, Instruments.class);
        this.usersRepository = usersRepository;
    }

    /**
     * L'elenco degli strumenti mostra quanti utenti hanno assegnato ciascuno
     * strumento. Il conteggio non sta sull'entità — la relazione è dichiarata
     * dalla parte degli utenti — e viene quindi recuperato con una sola query
     * raggruppata per l'intera pagina, non una per riga.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<InstrumentsDTO> findEntitiesByCriteria(InstrumentsCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        Page<InstrumentsDTO> page = super.findEntitiesByCriteria(criteria, pageable, token);

        List<Long> instrumentIds = page.getContent().stream().map(InstrumentsDTO::getId).filter(Objects::nonNull).toList();

        if (instrumentIds.isEmpty()) {
            return page;
        }

        Map<Long, Long> countByInstrumentId = new HashMap<>();
        for (Object[] row : usersRepository.countUsersByInstrumentIds(instrumentIds)) {
            countByInstrumentId.put((Long) row[0], (Long) row[1]);
        }

        page.getContent().forEach(instrument -> instrument.setUsersCount(countByInstrumentId.getOrDefault(instrument.getId(), 0L)));

        return page;
    }
}
