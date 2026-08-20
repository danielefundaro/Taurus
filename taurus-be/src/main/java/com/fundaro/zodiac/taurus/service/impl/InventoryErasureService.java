package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureRequest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryErasureRequestRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryErasureRequestDTO;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryErasureService {
    private final InventoryErasureRequestRepository requestRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final DataErasureService dataErasureService;
    private final KeycloakService keycloakService;

    public InventoryErasureService(
        InventoryErasureRequestRepository requestRepository,
        InventoryAssignmentRepository assignmentRepository,
        DataErasureService dataErasureService,
        KeycloakService keycloakService
    ) {
        this.requestRepository = requestRepository;
        this.assignmentRepository = assignmentRepository;
        this.dataErasureService = dataErasureService;
        this.keycloakService = keycloakService;
    }

    @Transactional(readOnly = true)
    public List<InventoryErasureRequestDTO> findPending(AbstractAuthenticationToken token) {
        tenant(token);
        return requestRepository.findAllByStatusOrderByRequestedAtAsc(InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION)
            .stream().map(this::toDto).toList();
    }

    public InventoryErasureRequestDTO complete(long id, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        InventoryErasureRequest request = requestRepository.findById(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Pratica di cancellazione non trovata", "inventoryErasure", "inventory.erasure.notFound"));
        if (request.getStatus() != InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "La pratica è già chiusa", "inventoryErasure", "inventory.erasure.closed");
        }
        if (assignmentRepository.hasOutstanding(request.getUserKeycloakId(), InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Esiste ancora materiale da riconsegnare", "inventoryErasure", "inventory.erasure.outstanding");
        }

        assignmentRepository.pseudonymizeUser(request.getUserKeycloakId(), "erased:" + UUID.randomUUID());
        dataErasureService.eraseUserData(request.getUserKeycloakId(), tenant);
        var groups = keycloakService.getUserGroups(request.getUserKeycloakId());
        if (groups.size() <= 1) {
            keycloakService.deleteUser(request.getUserKeycloakId());
        } else {
            keycloakService.deleteUserGroup(request.getUserKeycloakId(), keycloakService.getGroupIdByName(tenant));
        }
        request.setStatus(InventoryErasureStatus.COMPLETED);
        request.setResolvedAt(ZonedDateTime.now());
        request.setResolvedBy(actor);
        request.touchAudit(actor);
        return toDto(requestRepository.save(request));
    }

    private InventoryErasureRequestDTO toDto(InventoryErasureRequest value) {
        return new InventoryErasureRequestDTO(value.getId(), value.getUserIndex(), value.getDisplayName(), value.getEmail(), value.getStatus(), value.getRequestedAt());
    }

    private static String tenant(AbstractAuthenticationToken token) {
        String tenant = SecurityUtils.getTenantIdFromAuthentication(token);
        if (tenant == null || tenant.isBlank()) throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Tenant non disponibile", "inventoryErasure", "inventory.tenant.missing");
        return tenant;
    }
}
