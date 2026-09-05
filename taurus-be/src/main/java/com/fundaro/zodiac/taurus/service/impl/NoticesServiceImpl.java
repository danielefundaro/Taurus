package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.NotificationPreferenceResolver;
import com.fundaro.zodiac.taurus.service.NotificationPushDeliveryService;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import jakarta.persistence.criteria.Predicate;
import com.fundaro.zodiac.taurus.domain.notification.NoticeView;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NoticesServiceImpl extends CommonServiceImpl<Notices, NoticesDTO, NoticesCriteria, NoticesMapper, NoticesRepository> implements NoticesService {

    private final TenantFeatureService tenantFeatureService;
    private NotificationPushDeliveryService pushDeliveryService;
    private NotificationPreferenceResolver preferenceResolver;
    private com.fundaro.zodiac.taurus.config.ApplicationProperties.NotificationPreferencesProperties preferenceProperties =
        new com.fundaro.zodiac.taurus.config.ApplicationProperties.NotificationPreferencesProperties();

    public NoticesServiceImpl(NoticesRepository noticesRepository, NoticesMapper noticesMapper, TenantFeatureService tenantFeatureService) {
        super(noticesRepository, noticesMapper, NoticesService.class, Notices.class.getSimpleName());
        this.tenantFeatureService = tenantFeatureService;
    }

    @Autowired
    void setPushDependencies(NotificationPushDeliveryService pushDeliveryService, NotificationPreferenceResolver preferenceResolver) {
        this.pushDeliveryService = pushDeliveryService;
        this.preferenceResolver = preferenceResolver;
    }

    @Autowired
    void setApplicationProperties(com.fundaro.zodiac.taurus.config.ApplicationProperties applicationProperties) {
        this.preferenceProperties = applicationProperties.getNotificationPreferences();
    }

    @Override
    public void addNoticeToUser(NotificationDelivery delivery) {
        addNoticeToUserAndGetId(delivery);
    }

    @Override
    public Long addNoticeToUserAndGetId(NotificationDelivery delivery) {
        Notices existing = getRepository().findBySourceEventKeyAndUserId(delivery.eventKey(), delivery.userId()).orElse(null);
        if (existing != null) return existing.getId();
        ZonedDateTime now = ZonedDateTime.now();
        Notices notice = new Notices();
        notice.setName(delivery.title());
        notice.setMessage(delivery.message());
        notice.setUserId(delivery.userId());
        notice.setSource(delivery.source().name());
        notice.setSeverity(delivery.severity().name());
        notice.setTargetPath(delivery.targetPath());
        notice.setSourceEventKey(delivery.eventKey());
        notice.setPreferencePolicy(delivery.preferencePolicy() == null
            ? com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy.CONFIGURABLE
            : delivery.preferencePolicy());
        notice.setDeleted(false);
        notice.setInsertBy(delivery.actorId());
        notice.setInsertDate(now);
        notice.setEditBy(delivery.actorId());
        notice.setEditDate(now);
        Notices saved = getRepository().save(notice);
        return saved == null ? notice.getId() : saved.getId();
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
            dto.setSnoozedUntil(null);
            dto.setSnoozeRevision(dto.getSnoozeRevision() + 1);
            if (pushDeliveryService != null) pushDeliveryService.cancelSnooze(getMapper().toEntity(dto));
            return super.update(id, dto, authentication);
        }
        return dto;
    }

    @Override
    public void deleteAll(AbstractAuthenticationToken authentication) {
        String userId = SecurityUtils.getUserIdFromAuthentication(authentication);
        getRepository().findAllByUserId(userId).forEach(notice -> {
            if (pushDeliveryService != null) pushDeliveryService.cancelSnooze(notice);
            super.delete(notice.getId(), authentication);
        });
    }

    @Override
    public void delete(Long id, AbstractAuthenticationToken authentication) {
        Notices notice = owned(id, authentication);
        if (pushDeliveryService != null) pushDeliveryService.cancelSnooze(notice);
        super.delete(id, authentication);
    }

    @Override
    protected Specification<Notices> buildSpecification(NoticesCriteria criteria, String userId) {
        Specification<Notices> specification = super.buildSpecification(criteria, userId)
            .and((root, query, cb) -> root.get("source").in(disabledSources()).not())
            .and((root, query, cb) -> criteria.getView() == NoticeView.SNOOZED
                ? cb.greaterThan(root.get("snoozedUntil"), ZonedDateTime.now())
                : cb.or(cb.isNull(root.get("snoozedUntil")), cb.lessThanOrEqualTo(root.get("snoozedUntil"), ZonedDateTime.now())));
        if (criteria.getSource() != null && criteria.getSource().getEquals() != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("source"), criteria.getSource().getEquals()));
        }
        if (criteria.getUnread() != null && criteria.getUnread().getEquals() != null) {
            boolean unread = Boolean.TRUE.equals(criteria.getUnread().getEquals());
            specification = specification.and((root, query, cb) -> unread ? cb.isNull(root.get("readDate")) : cb.isNotNull(root.get("readDate")));
        }
        return specification;
    }

    @Override
    public NoticesDTO snooze(Long id, ZonedDateTime until, AbstractAuthenticationToken authentication) {
        ZonedDateTime now = ZonedDateTime.now();
        long minMinutes = preferenceProperties.getMinSnoozeMinutes();
        long maxDays = preferenceProperties.getMaxSnoozeDays();
        if (until == null || until.isBefore(now.plusMinutes(minMinutes)) || until.isAfter(now.plusDays(maxDays))) {
            throw new RequestAlertException(
                HttpStatus.BAD_REQUEST,
                "Snooze must be between " + minMinutes + " minutes and " + maxDays + " days",
                "notices",
                "snooze.invalid"
            );
        }
        Notices notice = owned(id, authentication);
        if (notice.getReadDate() != null) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "A read notice cannot be snoozed", "notices", "snooze.read");
        }
        notice.setSnoozedUntil(until);
        notice.setSnoozeRevision(notice.getSnoozeRevision() + 1);
        audit(notice, authentication);
        notice = getRepository().saveAndFlush(notice);
        if (pushDeliveryService != null && preferenceResolver != null) {
            var preference = preferenceResolver.resolve(
                NotificationSource.valueOf(notice.getSource()),
                NotificationPreferencePolicy.CONFIGURABLE,
                java.util.Set.of(notice.getUserId())
            ).get(notice.getUserId());
            pushDeliveryService.enqueueSnooze(notice, preference);
        }
        return getMapper().toDto(notice);
    }

    @Override
    public NoticesDTO unsnooze(Long id, AbstractAuthenticationToken authentication) {
        Notices notice = owned(id, authentication);
        notice.setSnoozedUntil(null);
        notice.setSnoozeRevision(notice.getSnoozeRevision() + 1);
        audit(notice, authentication);
        if (pushDeliveryService != null) pushDeliveryService.cancelSnooze(notice);
        return getMapper().toDto(getRepository().saveAndFlush(notice));
    }

    private Notices owned(Long id, AbstractAuthenticationToken authentication) {
        String userId = SecurityUtils.getUserIdFromAuthentication(authentication);
        return getRepository().findByIdAndUserId(id, userId)
            .filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", "notices", "id.notFound"));
    }

    private static void audit(Notices notice, AbstractAuthenticationToken authentication) {
        String userId = SecurityUtils.getUserIdFromAuthentication(authentication);
        notice.setEditBy(userId);
        notice.setEditDate(ZonedDateTime.now());
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
