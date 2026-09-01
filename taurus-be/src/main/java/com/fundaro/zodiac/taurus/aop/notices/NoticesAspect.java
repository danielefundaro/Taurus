package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.*;
import com.fundaro.zodiac.taurus.service.dto.*;
import com.fundaro.zodiac.taurus.service.dto.inventory.*;
import com.fundaro.zodiac.taurus.service.impl.*;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService.AssignmentNotice;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService.ItemNotice;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService.PhotoNotice;
import org.apache.commons.io.FilenameUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Aspect
@Component
public class NoticesAspect {

    private final NoticesService noticesService;
    private final UsersService usersService;
    private final TenantsService tenantsService;
    private final InstrumentsService instrumentsService;
    private final AlbumsService albumsService;
    private final TracksService tracksService;
    private final CalendarEventsService calendarEventsService;
    private final InventoryNoticeDataService inventoryNoticeDataService;

    public NoticesAspect(
        NoticesService noticesService,
        UsersService usersService,
        TenantsService tenantsService,
        InstrumentsService instrumentsService,
        AlbumsService albumsService,
        TracksService tracksService,
        CalendarEventsService calendarEventsService,
        InventoryNoticeDataService inventoryNoticeDataService
    ) {
        this.noticesService = noticesService;
        this.usersService = usersService;
        this.tenantsService = tenantsService;
        this.instrumentsService = instrumentsService;
        this.albumsService = albumsService;
        this.tracksService = tracksService;
        this.calendarEventsService = calendarEventsService;
        this.inventoryNoticeDataService = inventoryNoticeDataService;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.save(..))")
    private Object onSave(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Object result = joinPoint.proceed();

        if (!(result instanceof CommonFieldsOpenSearchDTO dto) || token == null) {
            return result;
        }

        String actor = getActorDisplayName(token);
        if (dto instanceof AlbumsDTO albumsDTO) {
            String name = "Album: creato", message = actor + " ha creato l'album " + quoted(dto.getName()) + ".";

            if (albumsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof TracksDTO tracksDTO) {
            String name = "Traccia: creata", message = actor + " ha creato la traccia " + quoted(dto.getName()) + ".";

            if (tracksDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof CalendarEventsDTO calendarEventsDTO) {
            String name = "Evento: creato", message = actor + " ha creato l'evento " + quoted(dto.getName()) + ".";

            if (calendarEventsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof InstrumentsDTO) {
            noticesService.addNoticesExcludeRoleUsers("Strumento: creato", actor + " ha creato lo strumento " + quoted(dto.getName()) + ".", token);
        } else if (dto instanceof UsersDTO usersDTO) {
            String user = displayName(usersDTO.getName(), usersDTO.getLastName(), "un utente senza nome");
            noticesService.addNoticesAdmins("Utente: creato", actor + " ha creato l'utente " + user + ".", token);
        } else if (dto instanceof TenantsDTO tenantsDTO) {
            noticesService.addNoticesSuperAdminsForTenant(
                tenantsDTO.getCode(),
                "Tenant: creato",
                actor + " ha creato il tenant " + quoted(tenantsDTO.getName()) + " con codice " + quoted(tenantsDTO.getCode()) + ".",
                token
            );
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.uploadFile(..))")
    private Object onUploadFile(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Object[] args = joinPoint.getArgs();
        Long id = (args.length > 0 && args[0] instanceof Long value) ? value : null;
        MultipartFile file = getMultipartFile(joinPoint);
        Object result = joinPoint.proceed();

        if (token == null) {
            return result;
        }

        String actor = getActorDisplayName(token);
        if (id != null) {
            tracksService.findOne(id, token).ifPresent(tracksDTO ->
                noticesService.addNoticesExcludeRoleUsers("Traccia: aggiornata", actor + " ha aggiornato la traccia " + quoted(tracksDTO.getName()) + ".", token)
            );
        } else if (file != null) {
            noticesService.addNoticesExcludeRoleUsers(
                "Traccia: creata",
                actor + " ha creato la traccia " + quoted(FilenameUtils.removeExtension(file.getOriginalFilename())) + ".",
                token
            );
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.partialUpdate(..))")
    private Object onUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Long id = getId(joinPoint);

        CommonFieldsOpenSearchDTO oldDto = null;
        if (token != null && id != null) {
            if (joinPoint.getTarget() instanceof AlbumsServiceImpl) {
                oldDto = albumsService.findOne(id, token).orElse(null);
            } else if (joinPoint.getTarget() instanceof TracksServiceImpl) {
                oldDto = tracksService.findOne(id, token).orElse(null);
            } else if (joinPoint.getTarget() instanceof CalendarEventsServiceImpl) {
                oldDto = calendarEventsService.findOne(id, token).orElse(null);
            } else if (joinPoint.getTarget() instanceof InstrumentsServiceImpl) {
                oldDto = instrumentsService.findOne(id, token).orElse(null);
            } else if (joinPoint.getTarget() instanceof UsersServiceImpl) {
                oldDto = usersService.findOne(id, token).orElse(null);
            } else if (joinPoint.getTarget() instanceof TenantsServiceImpl) {
                oldDto = tenantsService.findOne(id, token).orElse(null);
            }
        }

        Object result = joinPoint.proceed();

        if (!(result instanceof CommonFieldsOpenSearchDTO dto) || token == null || oldDto == null) {
            return result;
        }

        final CommonFieldsOpenSearchDTO oldFinal = oldDto;
        String actor = getActorDisplayName(token);

        if (dto instanceof AlbumsDTO albumsDTO) {
            AlbumsDTO oldAlbum = (AlbumsDTO) oldFinal;
            String name = "Album: aggiornato", message = actor + " ha aggiornato l'album " + quoted(dto.getName()) + ".";

            if (oldAlbum.getState() != StateEnum.PUBLIC) {
                if (albumsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    String publishedMessage = actor + " ha pubblicato l'album " + quoted(dto.getName()) + ".";
                    noticesService.addNoticeOnlyRoleUsers("Album: pubblicato", publishedMessage, token);
                    noticesService.addNoticesExcludeRoleUsers("Album: pubblicato", publishedMessage, token);
                }
            } else {
                if (albumsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers(
                        "Album: rimosso",
                        actor + " ha rimosso l'album " + quoted(dto.getName()) + " dai contenuti pubblicati.",
                        token
                    );
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeWholeTenant(name, message, token);
                }
            }
        } else if (dto instanceof TracksDTO tracksDTO) {
            TracksDTO oldTrack = (TracksDTO) oldFinal;
            String name = "Traccia: aggiornata", message = actor + " ha aggiornato la traccia " + quoted(dto.getName()) + ".";

            if (oldTrack.getState() != StateEnum.PUBLIC) {
                if (tracksDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    String publishedMessage = actor + " ha pubblicato la traccia " + quoted(dto.getName()) + ".";
                    noticesService.addNoticeOnlyRoleUsers("Traccia: pubblicata", publishedMessage, token);
                    noticesService.addNoticesExcludeRoleUsers("Traccia: pubblicata", publishedMessage, token);
                }
            } else {
                if (tracksDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers(
                        "Traccia: rimossa",
                        actor + " ha rimosso la traccia " + quoted(dto.getName()) + " dai contenuti pubblicati.",
                        token
                    );
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeWholeTenant(name, message, token);
                }
            }
        } else if (dto instanceof CalendarEventsDTO calendarEventsDTO) {
            CalendarEventsDTO oldCalendar = (CalendarEventsDTO) oldFinal;
            String name = "Evento: aggiornato", message = actor + " ha aggiornato l'evento " + quoted(dto.getName()) + ".";

            if (oldCalendar.getState() != StateEnum.PUBLIC) {
                if (calendarEventsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    String publishedMessage = actor + " ha pubblicato l'evento " + quoted(dto.getName()) + ".";
                    noticesService.addNoticeOnlyRoleUsers("Evento: pubblicato", publishedMessage, token);
                    noticesService.addNoticesExcludeRoleUsers("Evento: pubblicato", publishedMessage, token);
                }
            } else {
                if (calendarEventsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers(
                        "Evento: rimosso",
                        actor + " ha rimosso l'evento " + quoted(dto.getName()) + " dai contenuti pubblicati.",
                        token
                    );
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeWholeTenant(name, message, token);
                }
            }
        } else if (dto instanceof InstrumentsDTO) {
            noticesService.addNoticesExcludeRoleUsers("Strumento: aggiornato", actor + " ha aggiornato lo strumento " + quoted(dto.getName()) + ".", token);
        } else if (dto instanceof UsersDTO usersDTO) {
            String user = displayName(usersDTO.getName(), usersDTO.getLastName(), "un utente senza nome");
            noticesService.addNoticesAdmins("Utente: aggiornato", actor + " ha aggiornato l'utente " + user + ".", token);
        } else if (dto instanceof TenantsDTO tenantsDTO) {
            noticesService.addNoticesSuperAdminsForTenant(
                tenantsDTO.getCode(),
                "Tenant: aggiornato",
                actor + " ha aggiornato il tenant " + quoted(tenantsDTO.getName()) + " con codice " + quoted(tenantsDTO.getCode()) + ".",
                token
            );
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.delete(..))")
    private Object onDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Object result = joinPoint.proceed();

        if (!(result instanceof CommonFieldsOpenSearchDTO dto) || token == null) {
            return result;
        }

        String actor = getActorDisplayName(token);
        if (dto instanceof AlbumsDTO albumsDTO) {
            String name = "Album: rimosso", message = actor + " ha rimosso l'album " + quoted(dto.getName()) + ".";

            if (albumsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof TracksDTO tracksDTO) {
            String name = "Traccia: rimossa", message = actor + " ha rimosso la traccia " + quoted(dto.getName()) + ".";

            if (tracksDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof CalendarEventsDTO calendarEventsDTO) {
            String name = "Evento: rimosso", message = actor + " ha rimosso l'evento " + quoted(dto.getName()) + ".";

            if (calendarEventsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof InstrumentsDTO) {
            noticesService.addNoticesExcludeRoleUsers("Strumento: rimosso", actor + " ha rimosso lo strumento " + quoted(dto.getName()) + ".", token);
        } else if (dto instanceof UsersDTO usersDTO) {
            String user = displayName(usersDTO.getName(), usersDTO.getLastName(), "un utente senza nome");
            noticesService.addNoticesAdmins("Utente: rimosso", actor + " ha rimosso l'utente " + user + ".", token);
        } else if (dto instanceof TenantsDTO tenantsDTO) {
            noticesService.addNoticesSuperAdminsForTenant(
                tenantsDTO.getCode(),
                "Tenant: rimosso",
                actor + " ha rimosso il tenant " + quoted(tenantsDTO.getName()) + " con codice " + quoted(tenantsDTO.getCode()) + ".",
                token
            );
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CalendarEventsServiceImpl.setAvailability(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.user.impl.CalendarEventsServiceImpl.setAvailability(..))")
    private Object onSetAvailability(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Boolean available = getBoolean(joinPoint);
        Object result = joinPoint.proceed();

        if (!(result instanceof CalendarEventsDTO dto) || token == null) {
            return result;
        }

        String userName = getUserDisplayName(token);
        if (Boolean.TRUE.equals(available)) {
            noticesService.addNoticesAdmins(
                "Evento: disponibilità confermata",
                sentenceSubject(userName) + " ha confermato la disponibilità per l'evento " + quoted(dto.getName()) + ".",
                token
            );
        } else {
            noticesService.addNoticesAdmins(
                "Evento: disponibilità rifiutata",
                sentenceSubject(userName) + " ha indicato di non essere disponibile per l'evento " + quoted(dto.getName()) + ".",
                token
            );
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CalendarEventsServiceImpl.cancelAvailability(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.user.impl.CalendarEventsServiceImpl.cancelAvailability(..))")
    private Object onCancelAvailability(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Object result = joinPoint.proceed();

        if (!(result instanceof CalendarEventsDTO dto) || token == null) {
            return result;
        }

        String userName = getUserDisplayName(token);
        noticesService.addNoticesAdmins(
            "Evento: disponibilità annullata",
            sentenceSubject(userName) + " ha annullato la risposta relativa all'evento " + quoted(dto.getName()) + ".",
            token
        );

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CalendarEventsServiceImpl.setPresentUsers(..))")
    private Object onSetPresentUsers(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Object result = joinPoint.proceed();

        if (!(result instanceof CalendarEventsDTO dto) || token == null) {
            return result;
        }

        noticesService.addNoticesAdmins(
            "Evento: presenze aggiornate",
            getActorDisplayName(token) + " ha aggiornato le presenze dell'evento " + quoted(dto.getName()) + ".",
            token
        );

        return result;
    }

    @Around(
        "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.createItem(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.updateItem(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.deleteItem(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.assign(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.updateAssignment(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.deleteAssignment(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.reissue(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.decide(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.requestReturn(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.completeReturn(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.addPhoto(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.deletePhoto(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.reorderPhotos(..)) || " +
            "execution(public * com.fundaro.zodiac.taurus.service.impl.InventoryService.setPreviewPhoto(..))"
    )
    private Object onInventoryChange(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        ItemNotice itemBefore = null;
        AssignmentNotice assignmentBefore = null;
        PhotoNotice photoBefore = null;
        boolean selectedPhotoWasAlreadyPreview = false;

        if ("deleteItem".equals(method)) {
            itemBefore = findItemNotice(getLongArgument(joinPoint, 0));
        } else if ("requestReturn".equals(method) || "deleteAssignment".equals(method)) {
            assignmentBefore = inventoryNoticeDataService.findAssignment(getLongArgument(joinPoint, 0));
        } else if ("completeReturn".equals(method)) {
            assignmentBefore = inventoryNoticeDataService.findReturnAssignment(getLongArgument(joinPoint, 0));
        } else if ("deletePhoto".equals(method)) {
            photoBefore = inventoryNoticeDataService.findPhoto(getLongArgument(joinPoint, 0));
        } else if ("setPreviewPhoto".equals(method)) {
            Long itemId = getLongArgument(joinPoint, 0);
            Long photoId = getLongArgument(joinPoint, 1);
            selectedPhotoWasAlreadyPreview = inventoryNoticeDataService.isPreviewPhoto(itemId, photoId);
        }

        Object result = joinPoint.proceed();
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        if (token == null) {
            return result;
        }

        String actor = getActorDisplayName(token);
        switch (method) {
            case "createItem" ->
                notifyItem(result, "Inventario: oggetto creato", actor + " ha creato l'oggetto ", token);
            case "updateItem" ->
                notifyItem(result, "Inventario: oggetto aggiornato", actor + " ha aggiornato l'oggetto ", token);
            case "deleteItem" -> {
                if (itemBefore != null) {
                    noticesService.addNoticesAdmins(
                        "Inventario: oggetto rimosso",
                        actor + " ha rimosso l'oggetto " + itemLabel(itemBefore) + ".",
                        token
                    );
                }
            }
            case "assign" -> {
                if (result instanceof InventoryAssignmentDTO assignment) {
                    noticesService.addNoticesAdmins(
                        "Inventario: oggetto assegnato",
                        actor + " ha assegnato " + assignment.assignedQuantity() + " unità dell'oggetto " + itemLabel(assignment)
                            + " a " + assignmentOwner(assignment) + ".",
                        token
                    );
                }
            }
            case "updateAssignment" -> notifyAssignment(
                result,
                "Inventario: assegnazione aggiornata",
                actor + " ha aggiornato l'assegnazione dell'oggetto ",
                " a ",
                token
            );
            case "deleteAssignment" -> {
                if (assignmentBefore != null) {
                    noticesService.addNoticesAdmins(
                        "Inventario: assegnazione rimossa",
                        actor + " ha rimosso l'assegnazione dell'oggetto " + itemLabel(assignmentBefore.item())
                            + " a " + assignmentOwner(assignmentBefore) + ".",
                        token
                    );
                }
            }
            case "reissue" -> notifyAssignment(
                result,
                "Inventario: presa visione riemessa",
                actor + " ha riemesso la presa visione dell'oggetto ",
                " per ",
                token
            );
            case "decide" -> notifyInventoryDecision(joinPoint, result, token);
            case "requestReturn" -> notifyReturnRequested(assignmentBefore, result, token);
            case "completeReturn" -> notifyReturnCompleted(assignmentBefore, result, actor, token);
            case "addPhoto" -> {
                ItemNotice item = findItemNotice(getLongArgument(joinPoint, 0));
                if (item != null && result instanceof InventoryPhotoDTO photo) {
                    noticesService.addNoticesAdmins(
                        "Inventario: fotografia aggiunta",
                        actor + " ha aggiunto la fotografia " + quoted(photo.fileName()) + " all'oggetto " + itemLabel(item) + ".",
                        token
                    );
                }
            }
            case "deletePhoto" -> {
                if (photoBefore != null) {
                    noticesService.addNoticesAdmins(
                        "Inventario: fotografia rimossa",
                        actor + " ha rimosso la fotografia " + quoted(photoBefore.fileName()) + " dall'oggetto " + itemLabel(photoBefore.item()) + ".",
                        token
                    );
                }
            }
            case "reorderPhotos" -> {
                ItemNotice item = findItemNotice(getLongArgument(joinPoint, 0));
                if (item != null) {
                    noticesService.addNoticesAdmins(
                        "Inventario: fotografie aggiornate",
                        actor + " ha aggiornato l'ordine delle fotografie dell'oggetto " + itemLabel(item) + ".",
                        token
                    );
                }
            }
            case "setPreviewPhoto" -> {
                if (!selectedPhotoWasAlreadyPreview) {
                    ItemNotice item = findItemNotice(getLongArgument(joinPoint, 0));
                    Long selectedId = getLongArgument(joinPoint, 1);
                    if (item != null && result instanceof List<?> photos) {
                        photos.stream()
                            .filter(InventoryPhotoDTO.class::isInstance)
                            .map(InventoryPhotoDTO.class::cast)
                            .filter(photo -> Objects.equals(photo.id(), selectedId))
                            .findFirst()
                            .ifPresent(photo -> noticesService.addNoticesAdmins(
                                "Inventario: fotografie aggiornate",
                                actor + " ha impostato " + quoted(photo.fileName()) + " come fotografia di anteprima dell'oggetto " + itemLabel(item) + ".",
                                token
                            ));
                    }
                }
            }
            default -> {
                // The pointcut enumerates all supported inventory notification sources.
            }
        }

        return result;
    }

    @Around("this(com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRevisionRepository) && execution(* save(..))")
    private Object onInventoryRevisionSaved(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof InventoryAssignmentRevision revision) {
            InventoryAssignment assignment = revision.getAssignment();
            noticesService.addNoticeToUser(
                assignment.getUserKeycloakId(),
                "Inventario: presa visione richiesta",
                "La revisione " + revision.getRevisionNumber() + " della tua assegnazione dell'oggetto "
                    + itemLabel(new ItemNotice(assignment.getItem().getInventoryNumber(), assignment.getItem().getName()))
                    + " è disponibile per la presa visione.",
                revision.getCreatedBy()
            );
        }
        return result;
    }

    @Around("this(com.fundaro.zodiac.taurus.repository.inventory.InventoryExpirationNoticeRepository) && execution(* save(..))")
    private Object onInventoryExpirationNoticeSaved(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (!(result instanceof InventoryExpirationNotice delivery)) {
            return result;
        }

        InventoryAssignment assignment = delivery.getAssignment();
        InventoryExpirationNoticeType type = delivery.getNoticeType();
        String title = expirationTitle(type);
        String item = itemLabel(new ItemNotice(assignment.getItem().getInventoryNumber(), assignment.getItem().getName()));
        String date = formatDate(delivery.getExpirationDate());

        noticesService.addNoticeToUser(
            assignment.getUserKeycloakId(),
            title,
            expirationUserMessage(type, item, date),
            InventoryExpirationNotificationScheduler.SYSTEM_ACTOR
        );

        inventoryNoticeDataService.findAdminIds()
            .stream()
            .filter(adminId -> !adminId.equals(assignment.getUserKeycloakId()))
            .forEach(adminId -> noticesService.addNoticeToUser(
                adminId,
                title,
                expirationAdminMessage(type, assignment, item, date),
                InventoryExpirationNotificationScheduler.SYSTEM_ACTOR
            ));

        return result;
    }

    private void notifyItem(Object result, String title, String messagePrefix, AbstractAuthenticationToken token) {
        if (result instanceof InventoryItemDTO item) {
            noticesService.addNoticesAdmins(title, messagePrefix + itemLabel(item) + ".", token);
        }
    }

    private void notifyAssignment(
        Object result,
        String title,
        String messagePrefix,
        String ownerConnector,
        AbstractAuthenticationToken token
    ) {
        if (result instanceof InventoryAssignmentDTO assignment) {
            noticesService.addNoticesAdmins(
                title,
                messagePrefix + itemLabel(assignment) + ownerConnector + assignmentOwner(assignment) + ".",
                token
            );
        }
    }

    private void notifyInventoryDecision(ProceedingJoinPoint joinPoint, Object result, AbstractAuthenticationToken token) {
        if (!(result instanceof InventoryAssignmentDTO assignment)) {
            return;
        }
        InventoryDecisionRequest request = Arrays.stream(joinPoint.getArgs())
            .filter(InventoryDecisionRequest.class::isInstance)
            .map(InventoryDecisionRequest.class::cast)
            .findFirst()
            .orElse(null);
        if (request == null) {
            return;
        }

        boolean accepted = request.decision() == InventoryDecisionType.ACCEPTED;
        String message = sentenceSubject(assignmentOwner(assignment)) + (accepted ? " ha accettato" : " ha rifiutato")
            + " la revisione " + assignment.revision() + " dell'assegnazione dell'oggetto " + itemLabel(assignment) + ".";
        String reason = trimToNull(request.rejectionReason());
        if (!accepted && reason != null) {
            message += " Motivazione: " + reason + ".";
        }
        noticesService.addNoticesAdmins(
            accepted ? "Inventario: presa visione accettata" : "Inventario: presa visione rifiutata",
            message,
            token
        );
    }

    private void notifyReturnRequested(AssignmentNotice assignment, Object result, AbstractAuthenticationToken token) {
        if (assignment != null && result instanceof InventoryReturnDTO inventoryReturn) {
            noticesService.addNoticesAdmins(
                "Inventario: riconsegna richiesta",
                sentenceSubject(assignmentOwner(assignment)) + " ha richiesto la riconsegna di " + inventoryReturn.quantity()
                    + " unità dell'oggetto " + itemLabel(assignment.item()) + ".",
                token
            );
        }
    }

    private void notifyReturnCompleted(
        AssignmentNotice assignment,
        Object result,
        String actor,
        AbstractAuthenticationToken token
    ) {
        if (assignment == null || !(result instanceof InventoryReturnDTO inventoryReturn)) {
            return;
        }
        noticesService.addNoticeToUser(
            assignment.userKeycloakId(),
            "Inventario: riconsegna completata",
            "È stata completata la riconsegna di " + inventoryReturn.quantity() + " unità dell'oggetto "
                + itemLabel(assignment.item()) + " assegnato a te.",
            token
        );
        noticesService.addNoticesAdmins(
            "Inventario: riconsegna completata",
            actor + " ha completato la riconsegna di " + inventoryReturn.quantity() + " unità dell'oggetto "
                + itemLabel(assignment.item()) + " assegnato a " + assignmentOwner(assignment) + ".",
            token
        );
    }

    private ItemNotice findItemNotice(Long id) {
        return inventoryNoticeDataService.findItem(id);
    }

    private static String itemLabel(InventoryItemDTO item) {
        return itemLabel(new ItemNotice(item.inventoryNumber(), item.name()));
    }

    private static String itemLabel(InventoryAssignmentDTO assignment) {
        return itemLabel(new ItemNotice(assignment.inventoryNumber(), assignment.itemName()));
    }

    private static String itemLabel(ItemNotice item) {
        String inventoryNumber = trimToNull(item.inventoryNumber());
        String name = trimToNull(item.name());
        if (inventoryNumber != null && name != null) {
            return quoted(inventoryNumber + " — " + name);
        }
        return quoted(Objects.requireNonNullElse(inventoryNumber, Objects.requireNonNullElse(name, "oggetto senza identificativo")));
    }

    private static String assignmentOwner(InventoryAssignmentDTO assignment) {
        return displayName(assignment.userName(), assignment.userLastName(), "un utente non identificato");
    }

    private static String assignmentOwner(AssignmentNotice assignment) {
        return displayName(assignment.userName(), assignment.userLastName(), "un utente non identificato");
    }

    private static String displayName(String name, String lastName, String fallback) {
        String value = String.join(" ", Objects.requireNonNullElse(name, ""), Objects.requireNonNullElse(lastName, "")).trim();
        return value.isBlank() ? fallback : value;
    }

    private static String quoted(String value) {
        return "“" + Objects.requireNonNullElse(trimToNull(value), "senza nome") + "”";
    }

    private static String sentenceSubject(String value) {
        String subject = Objects.requireNonNullElse(trimToNull(value), "Un utente");
        return Character.toUpperCase(subject.charAt(0)) + subject.substring(1);
    }

    private static String getActorDisplayName(AbstractAuthenticationToken token) {
        String firstName = trimToNull(SecurityUtils.getFirstNameFromAuthentication(token));
        String lastName = trimToNull(SecurityUtils.getLastNameFromAuthentication(token));
        String displayName = String.join(" ", Objects.requireNonNullElse(firstName, ""), Objects.requireNonNullElse(lastName, "")).trim();
        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        return displayName.isBlank() ? Objects.requireNonNullElse(trimToNull(userId), "Un utente") : displayName;
    }

    private static String expirationTitle(InventoryExpirationNoticeType type) {
        return switch (type) {
            case THIRTY_DAYS, SEVEN_DAYS, DUE_TODAY -> "Inventario: assegnazione in scadenza";
            case OVERDUE -> "Inventario: assegnazione scaduta";
        };
    }

    private static String expirationUserMessage(InventoryExpirationNoticeType type, String item, String date) {
        return switch (type) {
            case THIRTY_DAYS, SEVEN_DAYS -> "La tua assegnazione dell'oggetto " + item + " scadrà il " + date + ".";
            case DUE_TODAY -> "La tua assegnazione dell'oggetto " + item + " scade oggi, " + date + ".";
            case OVERDUE -> "La tua assegnazione dell'oggetto " + item + " è scaduta il " + date + ".";
        };
    }

    private static String expirationAdminMessage(
        InventoryExpirationNoticeType type,
        InventoryAssignment assignment,
        String item,
        String date
    ) {
        String owner = displayName(assignment.getUserName(), assignment.getUserLastName(), "un utente non identificato");
        return switch (type) {
            case THIRTY_DAYS, SEVEN_DAYS -> "L'assegnazione dell'oggetto " + item + " a " + owner + " scadrà il " + date + ".";
            case DUE_TODAY -> "L'assegnazione dell'oggetto " + item + " a " + owner + " scade oggi, " + date + ".";
            case OVERDUE -> "L'assegnazione dell'oggetto " + item + " a " + owner + " è scaduta il " + date + ".";
        };
    }

    private static String formatDate(LocalDate value) {
        return "%02d/%02d/%04d".formatted(value.getDayOfMonth(), value.getMonthValue(), value.getYear());
    }

    private static Long getLongArgument(ProceedingJoinPoint joinPoint, int index) {
        Object value = joinPoint.getArgs()[index];
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AbstractAuthenticationToken getAbstractAuthenticationToken(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof AbstractAuthenticationToken)
            .map(arg -> (AbstractAuthenticationToken) arg)
            .findFirst().orElse(null);
    }

    private static Long getId(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof Long)
            .map(arg -> (Long) arg)
            .findFirst().orElse(null);
    }

    private static MultipartFile getMultipartFile(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof MultipartFile)
            .map(arg -> (MultipartFile) arg)
            .findFirst().orElse(null);
    }

    private static Boolean getBoolean(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof Boolean)
            .map(arg -> (Boolean) arg)
            .findFirst().orElse(null);
    }

    private static String getUserDisplayName(AbstractAuthenticationToken token) {
        if (token instanceof JwtAuthenticationToken jwtToken) {
            Object given = jwtToken.getTokenAttributes().get("given_name");
            Object family = jwtToken.getTokenAttributes().get("family_name");
            String fullName = displayName(
                given instanceof String value ? value : null,
                family instanceof String value ? value : null,
                null
            );
            if (fullName != null) {
                return fullName;
            }
            Object preferred = jwtToken.getTokenAttributes().get("preferred_username");
            if (preferred instanceof String value && trimToNull(value) != null) return value.trim();
        }
        return Objects.requireNonNullElse(trimToNull(token.getName()), "Un utente");
    }
}
