package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.repository.AlbumsRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class AlbumsServiceImpl extends CommonOpenSearchServiceImpl<Albums, AlbumsDTO, AlbumsCriteria, AlbumsMapper, AlbumsRepository>
    implements AlbumsService {

    private final TracksRepository tracksRepository;

    public AlbumsServiceImpl(AlbumsRepository repository, AlbumsMapper mapper, TracksRepository tracksRepository) {
        super(repository, mapper, AlbumsService.class, Albums.class);
        this.tracksRepository = tracksRepository;
    }

    @Override
    public AlbumsDTO save(AlbumsDTO dto, AbstractAuthenticationToken token) {
        Albums entity = getMapper().toEntity(dto);
        entity.setTracks(resolveTracks(dto));
        return saveEntity(entity, token, true);
    }

    @Override
    public AlbumsDTO update(Long id, AlbumsDTO dto, AbstractAuthenticationToken token) {
        Albums entity = getRepository().findByIdAndDeletedFalse(id).orElseThrow();
        getMapper().partialUpdate(entity, dto);
        List<Tracks> resolvedTracks = resolveTracks(dto);
        entity.getTracks().clear();
        getRepository().flush();
        entity.getTracks().addAll(resolvedTracks);
        return saveEntity(entity, token, false);
    }

    @Override
    public AlbumsDTO partialUpdate(Long id, AlbumsDTO dto, AbstractAuthenticationToken token) {
        return update(id, dto, token);
    }

    @Override
    protected Specification<Albums> buildSpecification(AlbumsCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) return cb.conjunction();
            addRangeFilter(predicates, cb, root.get("date"), criteria.getDate());
            addFilter(predicates, cb, root.get("state"), criteria.getState());
            if (criteria.getTrackId() != null || criteria.getTrackName() != null) {
                query.distinct(true);
                var track = root.join("tracks");
                addFilter(predicates, cb, track.get("id"), criteria.getTrackId());
                addStringFilter(predicates, cb, track.get("name"), criteria.getTrackName());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    private List<com.fundaro.zodiac.taurus.domain.Tracks> resolveTracks(AlbumsDTO dto) {
        if (dto.getTracks() == null) return new ArrayList<>();
        return dto.getTracks().stream()
            .sorted(Comparator.comparing(ref -> ref.getOrder() == null ? Long.MAX_VALUE : ref.getOrder()))
            .map(ref -> tracksRepository.getReferenceById(ref.getIndex()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
