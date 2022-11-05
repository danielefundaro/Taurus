package com.fnd.taurus.controller;

import com.fnd.taurus.dto.CommonFieldsDTO;
import com.fnd.taurus.entity.CommonFields;
import com.fnd.taurus.model.QueryPagination;
import com.fnd.taurus.repository.CommonRepository;
import com.fnd.taurus.service.CommonService;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
public interface CommonController<C extends CommonFields, D extends CommonFieldsDTO, R extends CommonRepository<C>, S extends CommonService<C, D, R>> {
    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<D>> getAllData(KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(getService().getAllData(keycloakAuthenticationToken));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<D> getDataById(@PathVariable("id") Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(getService().getDataById(id, keycloakAuthenticationToken));
    }

    @PostMapping(value = "/searches", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<Page<D>> getDataByQueryString(@RequestBody QueryPagination queryPagination, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(getService().getQueryString(queryPagination, keycloakAuthenticationToken));
    }

    @PostMapping(value = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<D> saveData(@RequestBody D d, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(getService().saveData(d, keycloakAuthenticationToken));
    }

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<D> deleteData(@PathVariable("id") Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(getService().deleteData(id, keycloakAuthenticationToken));
    }

    S getService();
}
