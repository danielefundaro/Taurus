package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningException;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.mapper.TenantsMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.time.DateTimeException;
import java.time.ZoneId;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantsServiceImpl extends CommonOpenSearchServiceImpl<Tenants, TenantsDTO, TenantsCriteria, TenantsMapper, TenantsRepository>
    implements TenantsService {

    private final KeycloakService keycloakService;
    private final DataErasureService dataErasureService;
    private final TenantSchemaProvisioningService provisioningService;

    public TenantsServiceImpl(
        TenantsRepository repository,
        TenantsMapper mapper,
        KeycloakService keycloakService,
        DataErasureService dataErasureService,
        TenantSchemaProvisioningService provisioningService
    ) {
        super(repository, mapper, TenantsService.class, Tenants.class);
        this.keycloakService = keycloakService;
        this.dataErasureService = dataErasureService;
        this.provisioningService = provisioningService;
    }

    @Override
    public TenantsDTO save(TenantsDTO dto, AbstractAuthenticationToken token) {
        normalizeTimeZone(dto);
        if (getRepository().findByCodeAndDeletedFalse(dto.getCode()).isPresent()) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Tenant code already exists", getEntityName(), "code.exists");
        }
        TenantsDTO saved = super.save(dto, token);
        try {
            provisioningService.provision(saved.getCode());
            provisioningService.linkTenant(saved.getId(), saved.getCode());
            provisionKeycloakGroup(saved);
            return saved;
        } catch (RuntimeException exception) {
            getRepository().deleteById(saved.getId());
            if (exception instanceof TenantSchemaProvisioningException) {
                throw new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, "Tenant schema provisioning failed", getEntityName(), "save.tenant.schema");
            }
            throw exception;
        }
    }

    @Override
    public TenantsDTO update(Long id, TenantsDTO dto, AbstractAuthenticationToken token) {
        normalizeTimeZone(dto);
        return super.update(id, dto, token);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantsDTO> findByCode(String code, AbstractAuthenticationToken token) {
        return getRepository().findByCodeAndDeletedFalse(code).map(getMapper()::toDto);
    }

    @Override
    public TenantsDTO delete(Long id, AbstractAuthenticationToken token) {
        Tenants tenant = getRepository().findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Tenant not found", getEntityName(), "id.notFound"));
        String groupId = keycloakService.getGroupIdByName(tenant.getCode());
        TenantsDTO deleted = super.delete(id, token);
        provisioningService.deactivate(tenant.getCode(), SecurityUtils.getUserIdFromAuthentication(token));
        keycloakService.deleteGroup(groupId);
        return deleted;
    }

    @Override
    protected Specification<Tenants> buildSpecification(TenantsCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) return cb.conjunction();
            addStringFilter(predicates, cb, root.get("code"), criteria.getCode());
            addStringFilter(predicates, cb, root.get("email"), criteria.getEmail());
            addStringFilter(predicates, cb, root.get("domain"), criteria.getDomain());
            addRangeFilter(predicates, cb, root.get("maxUsers"), criteria.getMaxUsers());
            addRangeFilter(predicates, cb, root.get("expireDate"), criteria.getExpireDate());
            addFilter(predicates, cb, root.get("active"), criteria.getActive());
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public void deleteForGdpr(Long id, AbstractAuthenticationToken token) {
        Tenants tenant = getRepository().findById(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Tenant not found", getEntityName(), "id.notFound"));
        String groupId = keycloakService.getGroupIdByName(tenant.getCode());
        dataErasureService.eraseTenantData(tenant.getCode());
        keycloakService.deleteGroup(groupId);
        getRepository().delete(tenant);
    }

    private void provisionKeycloakGroup(TenantsDTO tenant) {
        keycloakService.saveGroup(new Group(tenant.getCode(), tenant.getName()));
        String groupId = keycloakService.getGroupIdByName(tenant.getCode());
        for (User user : keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN)) {
            keycloakService.updateUserGroup(user.getId(), groupId);
            List<Role> roles = keycloakService.getUserRoles(user.getId());
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes != null) {
                attributes.put(tenant.getCode() + "_roles", roles.stream().map(Role::getName).toList());
                user.setAttributes(attributes);
                keycloakService.updateUser(user);
            }
        }
    }

    private void normalizeTimeZone(TenantsDTO dto) {
        String timeZone = dto.getTimeZone();
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = "Europe/Rome";
        }
        try {
            dto.setTimeZone(ZoneId.of(timeZone.trim()).getId());
        } catch (DateTimeException exception) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid tenant time zone", getEntityName(), "timeZone.invalid");
        }
    }
}
