package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItemPhoto;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryNoticeDataService {

    private static final Logger log = LoggerFactory.getLogger(InventoryNoticeDataService.class);
    private static final List<RoleEnum> ADMIN_ROLES = List.of(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_SUPER_ADMIN);

    private final InventoryItemRepository itemRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryItemPhotoRepository photoRepository;
    private final InventoryReturnRepository returnRepository;
    private final UsersRepository usersRepository;
    private final KeycloakService keycloakService;

    public InventoryNoticeDataService(
        InventoryItemRepository itemRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryItemPhotoRepository photoRepository,
        InventoryReturnRepository returnRepository,
        UsersRepository usersRepository,
        KeycloakService keycloakService
    ) {
        this.itemRepository = itemRepository;
        this.assignmentRepository = assignmentRepository;
        this.photoRepository = photoRepository;
        this.returnRepository = returnRepository;
        this.usersRepository = usersRepository;
        this.keycloakService = keycloakService;
    }

    public ItemNotice findItem(Long id) {
        return itemRepository.findByIdAndDeletedFalse(id).map(ItemNotice::from).orElse(null);
    }

    public AssignmentNotice findAssignment(Long id) {
        return assignmentRepository.findNoticeTargetById(id).map(AssignmentNotice::from).orElse(null);
    }

    public AssignmentNotice findReturnAssignment(Long returnId) {
        return returnRepository.findNoticeTargetById(returnId)
            .map(value -> AssignmentNotice.from(value.getAssignment()))
            .orElse(null);
    }

    public PhotoNotice findPhoto(Long id) {
        return photoRepository.findNoticeTargetById(id).map(PhotoNotice::from).orElse(null);
    }

    public boolean isPreviewPhoto(Long itemId, Long photoId) {
        return photoRepository.findByIdAndItem_IdAndDeletedFalse(photoId, itemId)
            .map(InventoryItemPhoto::isPreview)
            .orElse(false);
    }

    public Set<String> findAdminIds() {
        Set<String> adminIds = new LinkedHashSet<>(usersRepository.findActiveKeycloakIdsByRolesIn(ADMIN_ROLES));
        try {
            keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN).stream()
                .map(user -> user.getId())
                .filter(userId -> userId != null && !userId.isBlank())
                .forEach(adminIds::add);
        } catch (RuntimeException exception) {
            log.warn("Unable to load Keycloak super admins for inventory expiration notices", exception);
        }
        return adminIds;
    }

    public record ItemNotice(String inventoryNumber, String name) {
        private static ItemNotice from(InventoryItem item) {
            return new ItemNotice(item.getInventoryNumber(), item.getName());
        }
    }

    public record AssignmentNotice(String userKeycloakId, String userName, String userLastName, ItemNotice item) {
        private static AssignmentNotice from(InventoryAssignment assignment) {
            return new AssignmentNotice(
                assignment.getUserKeycloakId(),
                assignment.getUserName(),
                assignment.getUserLastName(),
                ItemNotice.from(assignment.getItem())
            );
        }
    }

    public record PhotoNotice(String fileName, ItemNotice item) {
        private static PhotoNotice from(InventoryItemPhoto photo) {
            return new PhotoNotice(photo.getMediaAsset().getOriginalFilename(), ItemNotice.from(photo.getItem()));
        }
    }
}
