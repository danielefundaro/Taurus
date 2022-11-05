package com.fnd.taurus.service;

import com.fnd.taurus.dto.CommonFieldsDTO;
import com.fnd.taurus.entity.Audit;
import com.fnd.taurus.entity.AuditTuple;
import com.fnd.taurus.entity.CommonFields;
import com.fnd.taurus.enums.AuditTypeEnum;
import com.fnd.taurus.model.GenericSpecificationNotDistinct;
import com.fnd.taurus.model.GenericSpecificationsBuilder;
import com.fnd.taurus.model.QueryPagination;
import com.fnd.taurus.model.QueryParser;
import com.fnd.taurus.repository.AuditRepository;
import com.fnd.taurus.repository.CommonRepository;
import org.jetbrains.annotations.NotNull;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.IDToken;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface CommonService<C extends CommonFields, D extends CommonFieldsDTO, R extends CommonRepository<C>> {
    Logger log = LoggerFactory.getLogger(CommonService.class);
    ModelMapper modelMapper = new ModelMapper();

    AuditRepository getAuditRepository();

    R getRepository();

    Class<C> getEntity();

    Class<D> getDTO();

    default List<D> getAllData(KeycloakAuthenticationToken keycloakAuthenticationToken) {
        String principalName = principalGetName(keycloakAuthenticationToken);
        List<C> cList = (List<C>) getRepository().findAll();
        String info = String.format("The user %s got all the object %s. Count %s", principalName, getDTO().getSimpleName(), cList.size());

        saveAuditInfo(cList.stream().map(CommonFields::getId).collect(Collectors.toList()), principalName, AuditTypeEnum.SELECT_ALL, info, true);

        return cList.stream().map(this::entity2DTO).collect(Collectors.toList());
    }

    default D getDataById(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        String principalName = principalGetName(keycloakAuthenticationToken);
        C c = getEntityById(id, principalName);
        String info = String.format("The user %s got the object %s with id %s", principalName, getDTO().getSimpleName(), id);

        saveAuditInfo(Collections.singletonList(c.getId()), principalName, AuditTypeEnum.SELECT, info, true);

        return entity2DTO(c);
    }

    default Page<D> getQueryString(@NotNull QueryPagination queryPagination, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        String principalName = principalGetName(keycloakAuthenticationToken);
        int pageIndex = queryPagination.index() == null ? 0 : queryPagination.index();
        int pageSize = queryPagination.size() == null ? 10 : queryPagination.size();
        Sort sort;

        if (queryPagination.sort() != null) {
            sort = Sort.by(queryPagination.sort().stream().map(s -> {
                Sort.Direction direction = s.charAt(0) == '-' ? Sort.Direction.DESC : Sort.Direction.ASC;

                if (s.charAt(0) == '-' || s.charAt(0) == '+') s = s.substring(1);

                return new Sort.Order(direction, s);
            }).collect(Collectors.toList()));
        } else {
            sort = Sort.by(Sort.Direction.ASC, "id");
        }

        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize, sort);
        Page<C> cPage;

        if (queryPagination.q() == null || queryPagination.q().isBlank()) {
            cPage = getRepository().findAll(pageRequest);
        } else {
            QueryParser parser = new QueryParser(queryPagination.q());
            GenericSpecificationsBuilder<C> specBuilder = new GenericSpecificationsBuilder<>();
            cPage = getRepository().findAll(specBuilder.build(parser.parse(), GenericSpecificationNotDistinct::new), pageRequest);
        }

        String info = String.format("The user %s got all object %s with ids %s. Count %s; total elements %s; total pages %s", principalName, getDTO().getSimpleName(), cPage.stream().map(CommonFields::getId).toList(), cPage.getNumberOfElements(), cPage.getTotalElements(), cPage.getTotalPages());
        saveAuditInfo(cPage.stream().map(CommonFields::getId).collect(Collectors.toList()), principalName, AuditTypeEnum.SEARCH, info, true);

        return new PageImpl<>(cPage.map(this::entity2DTO).toList(), cPage.getPageable(), cPage.getTotalElements());
    }

    default D saveData(@NotNull D d, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        String principalName = principalGetName(keycloakAuthenticationToken);
        String action = "saved";
        AuditTypeEnum auditTypeEnum = AuditTypeEnum.INSERT;

        if (d.getId() != null) {
            action = "updated";
            auditTypeEnum = AuditTypeEnum.UPDATE;
        }

        C c = DTO2Entity(d);
        setDefaultDelete(c);
        c = getRepository().save(c);

        String info = String.format("The user %s %s the object %s with id %s", principalName, action, getDTO().getSimpleName(), c.getId());
        saveAuditInfo(Collections.singletonList(c.getId()), principalName, auditTypeEnum, info, true);

        return entity2DTO(c);
    }

    default D deleteData(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        String principalName = principalGetName(keycloakAuthenticationToken);
        C c = getEntityById(id, principalName);

        c.setDeleted(true);
        setDefaultDelete(c);
        c = getRepository().save(c);

        String info = String.format("The user %s deleted the object %s with id %s", principalName, getDTO().getSimpleName(), c.getId());
        saveAuditInfo(Collections.singletonList(c.getId()), principalName, AuditTypeEnum.DELETE, info, true);

        return entity2DTO(c);
    }

    private D entity2DTO(C c) {
        return modelMapper.map(c, getDTO());
    }

    private C DTO2Entity(D d) {
        return modelMapper.map(d, getEntity());
    }

    @NotNull
    private C getEntityById(Long id, String principalName) {
        Optional<C> optionalC = getRepository().findById(id);

        if (optionalC.isEmpty()) {
            String error = String.format("The user %s didn't find the object %s with id %s", principalName, getDTO().getSimpleName(), id);

            saveAuditInfo(Collections.singletonList(id), principalName, AuditTypeEnum.ERROR, error, false);
            log.error(error);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found");
        }

        return optionalC.get();
    }

    private void setDefaultDelete(@NotNull C c) {
        if (c.getDeleted() == null) c.setDeleted(false);
    }

    private void saveAuditInfo(List<Long> ids, String principalName, AuditTypeEnum auditTypeEnum, String description, boolean showLog) {
        Audit audit = new Audit();
        Date now = new Date();

        audit.setDeleted(false);
        audit.setDate(now);
        audit.setUserId(principalName);
        audit.setType(auditTypeEnum);
        audit.setDescription(description);
        audit.setTableName(getEntity().getSimpleName());
        audit.setTuples(ids.stream().map(id -> {
            AuditTuple auditTuple = new AuditTuple();

            auditTuple.setDeleted(false);
            auditTuple.setDate(now);
            auditTuple.setAudit(audit);
            auditTuple.setTupleId(id);

            return auditTuple;
        }).collect(Collectors.toList()));

        getAuditRepository().save(audit);

        if (showLog)
            log.info(description);
    }

    private String principalGetName(KeycloakAuthenticationToken keycloakAuthenticationToken) {
        if (keycloakAuthenticationToken != null) {
            Principal principal = (Principal) keycloakAuthenticationToken.getPrincipal();

            if (principal instanceof KeycloakPrincipal<?> keycloakPrincipal) {
                IDToken token = keycloakPrincipal.getKeycloakSecurityContext().getToken();
                return token.getSubject();
            }
        }

        return "SYSTEM";
    }
}
