package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import com.fundaro.zodiac.taurus.service.scoreplayback.PlaybackSettingsValidator;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import jakarta.persistence.criteria.Predicate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
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

    @Override
    public InstrumentsDTO save(InstrumentsDTO dto, AbstractAuthenticationToken token) {
        requireValidPlaybackSettings(dto);
        return super.save(dto, token);
    }

    /**
     * A full update also replaces the playback settings: a value left empty clears the stored one, so an instrument can
     * go back to "not configured". The other fields keep the partial semantics of the common services.
     */
    @Override
    public InstrumentsDTO update(Long id, InstrumentsDTO dto, AbstractAuthenticationToken token) {
        requireValidPlaybackSettings(dto);
        if (id == null || !Objects.equals(id, dto.getId())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid ID", getEntityName(), "id.invalid");
        }
        Instruments entity = getRepository().findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        getMapper().partialUpdate(entity, dto);
        entity.setTranspositionSemitones(dto.getTranspositionSemitones());
        entity.setBassClefTranspositionSemitones(dto.getBassClefTranspositionSemitones());
        entity.setSoundBank(dto.getSoundBank());
        entity.setSoundProgram(dto.getSoundProgram());
        entity.setPercussionKey(dto.getPercussionKey());
        return saveEntity(entity, token, false);
    }

    @Override
    public InstrumentsDTO partialUpdate(Long id, InstrumentsDTO dto, AbstractAuthenticationToken token) {
        requireValidPlaybackSettings(dto);
        return super.update(id, dto, token);
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

    @Override
    protected Specification<Instruments> buildSpecification(InstrumentsCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            if (criteria == null || criteria.getPlaybackConfigured() == null || criteria.getPlaybackConfigured().getEquals() == null) {
                return cb.conjunction();
            }
            Predicate configured = cb.or(
                cb.isNotNull(root.get("percussionKey")),
                cb.and(cb.isNotNull(root.get("soundProgram")), cb.isNotNull(root.get("transpositionSemitones")))
            );
            return Boolean.TRUE.equals(criteria.getPlaybackConfigured().getEquals()) ? configured : cb.not(configured);
        });
    }

    private void requireValidPlaybackSettings(InstrumentsDTO dto) {
        PlaybackSettingsValidator.requireValidSound(
            getEntityName(),
            dto.getTranspositionSemitones(),
            dto.getBassClefTranspositionSemitones(),
            dto.getSoundBank(),
            dto.getSoundProgram(),
            dto.getPercussionKey()
        );
    }
}
