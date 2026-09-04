package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NoticesServiceImpl extends CommonServiceImpl<Notices, NoticesDTO, NoticesCriteria, NoticesMapper, NoticesRepository> implements NoticesService {

    private final TenantFeatureService tenantFeatureService;

    public NoticesServiceImpl(NoticesRepository noticesRepository, NoticesMapper noticesMapper, TenantFeatureService tenantFeatureService) {
        super(noticesRepository, noticesMapper, NoticesService.class, Notices.class.getSimpleName());
        this.tenantFeatureService = tenantFeatureService;
    }

    @Override
    public void addNoticeToUser(NotificationDelivery delivery) {
        if (getRepository().findBySourceEventKeyAndUserId(delivery.eventKey(), delivery.userId()).isPresent()) return;
        ZonedDateTime now = ZonedDateTime.now();
        Notices notice = new Notices();
        notice.setName(delivery.title());
        notice.setMessage(delivery.message());
        notice.setUserId(delivery.userId());
        notice.setSource(delivery.source().name());
        notice.setSeverity(delivery.severity().name());
        notice.setTargetPath(delivery.targetPath());
        notice.setSourceEventKey(delivery.eventKey());
        notice.setDeleted(false);
        notice.setInsertBy(delivery.actorId());
        notice.setInsertDate(now);
        notice.setEditBy(delivery.actorId());
        notice.setEditDate(now);
        getRepository().save(notice);
    }

    @Override
    public void readAll(AbstractAuthenticationToken authentication) {
        String userId = SecurityUtils.getUserIdFromAuthentication(authentication);
        ZonedDateTime now = ZonedDateTime.now();
        getRepository().findAllUnread(userId).forEach(notice -> {
            NoticesDTO dto = getMapper().toDto(notice);
            dto.setReadDate(now);
            update(dto.getId(), dto, authentication);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(AbstractAuthenticationToken authentication) {
        return getRepository().countUnreadExcludingSources(
            SecurityUtils.getUserIdFromAuthentication(authentication),
            disabledSources()
        );
    }

    @Override
    public NoticesDTO read(Long id, AbstractAuthenticationToken authentication) {
        NoticesDTO dto = findOne(id, authentication)
            .orElseThrow(() -> new com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Entity not found", "notices", "id.notFound"));
        if (dto.getReadDate() == null) {
            dto.setReadDate(ZonedDateTime.now());
            return super.update(id, dto, authentication);
        }
        return dto;
    }

    @Override
    public void deleteAll(AbstractAuthenticationToken authentication) {
        String userId = SecurityUtils.getUserIdFromAuthentication(authentication);
        getRepository().findAllByUserId(userId).forEach(notice -> super.delete(notice.getId(), authentication));
    }

    @Override
    protected Specification<Notices> buildSpecification(NoticesCriteria criteria, String userId) {
        return super.buildSpecification(criteria, userId).and((root, query, cb) -> root.get("source").in(disabledSources()).not());
    }

    private List<String> disabledSources() {
        var features = tenantFeatureService.current();
        List<String> disabled = new ArrayList<>();
        if (!features.financeEnabled()) disabled.add("FINANCE");
        if (!features.inventoryEnabled()) disabled.add("INVENTORY");
        if (disabled.isEmpty()) disabled.add("__NONE__");
        return disabled;
    }
}
