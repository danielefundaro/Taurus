package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationSeverity;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.*;
import com.fundaro.zodiac.taurus.service.NoticesService.FinanceNoticeCommand;
import com.fundaro.zodiac.taurus.service.dto.*;
import com.fundaro.zodiac.taurus.service.dto.inventory.*;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.*;
import com.fundaro.zodiac.taurus.service.impl.*;
import com.fundaro.zodiac.taurus.service.impl.FinanceNoticeDataService.AttachmentNotice;
import com.fundaro.zodiac.taurus.service.impl.FinanceNoticeDataService.MovementNotice;
import com.fundaro.zodiac.taurus.service.impl.FinanceNoticeDataService.NamedNotice;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService.AssignmentNotice;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService.ItemNotice;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService.PhotoNotice;
import org.apache.commons.io.FilenameUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
    private final FinanceNoticeDataService financeNoticeDataService;

    private static final Set<RoleEnum> FINANCE_NOTIFICATION_ROLES = Set.of(
        RoleEnum.ROLE_ADMIN,
        RoleEnum.ROLE_SUPER_ADMIN,
        RoleEnum.ROLE_TREASURER
    );

    public NoticesAspect(
        NoticesService noticesService,
        UsersService usersService,
        TenantsService tenantsService,
        InstrumentsService instrumentsService,
        AlbumsService albumsService,
        TracksService tracksService,
        CalendarEventsService calendarEventsService,
        InventoryNoticeDataService inventoryNoticeDataService,
        FinanceNoticeDataService financeNoticeDataService
    ) {
        this.noticesService = noticesService;
        this.usersService = usersService;
        this.tenantsService = tenantsService;
        this.instrumentsService = instrumentsService;
        this.albumsService = albumsService;
        this.tracksService = tracksService;
        this.calendarEventsService = calendarEventsService;
        this.inventoryNoticeDataService = inventoryNoticeDataService;
        this.financeNoticeDataService = financeNoticeDataService;
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

    @Around(
        "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.createAccount(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.updateAccount(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.archiveAccount(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.createCategory(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.updateCategory(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.archiveCategory(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.createMovement(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.updateMovement(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.deleteMovement(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.reconcile(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.createTransfer(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.addAttachment(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.getAttachment(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.deleteAttachment(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.updateEventBudget(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.rollover(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.rolloverForActor(..)) || " +
            "execution(* com.fundaro.zodiac.taurus.service.impl.FinanceService.recalculate(..))"
    )
    Object onFinanceOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = ((MethodSignature) joinPoint.getSignature()).getName();
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Long id = getLongArgument(joinPoint, 0);
        NamedNotice namedBefore = switch (method) {
            case "updateAccount", "archiveAccount" -> id == null ? null : financeNoticeDataService.findAccount(id);
            case "updateCategory", "archiveCategory" -> id == null ? null : financeNoticeDataService.findCategory(id);
            default -> null;
        };
        List<MovementNotice> movementsBefore = "deleteMovement".equals(method) && id != null
            ? financeNoticeDataService.findMovementGroup(id)
            : List.of();
        AttachmentNotice attachmentBefore = ("getAttachment".equals(method) || "deleteAttachment".equals(method)) && id != null
            ? financeNoticeDataService.findAttachment(id)
            : null;
        int year = getIntegerArgument(joinPoint, 0);
        AccountingYearStatus yearBefore = ("rollover".equals(method) || "rolloverForActor".equals(method))
            ? financeNoticeDataService.findYearStatus(year)
            : null;
        MovementRequest movementRequest = getArgument(joinPoint, MovementRequest.class);
        boolean movementAlreadyExisted = "createMovement".equals(method)
            && movementRequest != null
            && financeNoticeDataService.movementExists(movementRequest.requestKey());

        Object result = joinPoint.proceed();

        switch (method) {
            case "createAccount" -> notifyAccountCreated(result, getArgument(joinPoint, AccountRequest.class), token);
            case "updateAccount" -> notifyAccountUpdated(result, namedBefore, token);
            case "archiveAccount" -> notifyNamed(
                namedBefore,
                "ACCOUNT_ARCHIVED",
                "Economia: conto archiviato",
                "ha archiviato il conto ",
                FinanceNotificationSeverity.WARNING,
                "/finance?tab=accounts",
                token
            );
            case "createCategory" -> notifyCategory(result, "CATEGORY_CREATED", "Economia: categoria creata", "ha creato", token);
            case "updateCategory" -> notifyCategoryUpdated(result, namedBefore, token);
            case "archiveCategory" -> notifyNamed(
                namedBefore,
                "CATEGORY_ARCHIVED",
                "Economia: categoria archiviata",
                "ha archiviato la categoria ",
                FinanceNotificationSeverity.WARNING,
                "/finance?tab=categories",
                token
            );
            case "createMovement" -> {
                if (!movementAlreadyExisted && result instanceof MovementDTO movement) {
                    notifyMovement(toNotice(movement), "MOVEMENT_CREATED", "Economia: movimento registrato", "ha registrato", token);
                }
            }
            case "updateMovement" -> notifyMovementUpdated(result, token);
            case "deleteMovement" -> notifyMovementRemoved(movementsBefore, token);
            case "reconcile" -> notifyReconciliation(result, getArgument(joinPoint, ReconciliationRequest.class), token);
            case "createTransfer" -> {
                if (result instanceof TransferDTO transfer) notifyTransfer(transfer, "TRANSFER_CREATED", "Economia: trasferimento registrato", "ha registrato", token);
            }
            case "addAttachment" -> notifyAttachmentAdded(result, token);
            case "getAttachment" -> notifyAttachment(attachmentBefore, "ATTACHMENT_DOWNLOADED", "Economia: allegato scaricato", "ha scaricato", FinanceNotificationSeverity.INFO, token);
            case "deleteAttachment" -> notifyAttachment(attachmentBefore, "ATTACHMENT_REMOVED", "Economia: allegato rimosso", "ha rimosso", FinanceNotificationSeverity.WARNING, token);
            case "updateEventBudget" -> notifyEventBudget(result, token);
            case "rollover", "rolloverForActor" -> notifyRollover(joinPoint, result, yearBefore, token);
            case "recalculate" -> notifyRecalculation(result, token);
            default -> {
                // The pointcut enumerates all supported finance notification sources.
            }
        }
        return result;
    }

    @Around("execution(* com.fundaro.zodiac.taurus.service.impl.FinanceReportService.cashbook(..))")
    Object onFinanceReportExport(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        if (token == null) return result;
        LocalDate from = joinPoint.getArgs()[0] instanceof LocalDate value ? value : null;
        LocalDate to = joinPoint.getArgs()[1] instanceof LocalDate value ? value : null;
        LocalDate effectiveTo = Objects.requireNonNullElse(to, LocalDate.now());
        LocalDate effectiveFrom = Objects.requireNonNullElse(from, effectiveTo.withDayOfYear(1));
        String format = getArgument(joinPoint, String.class);
        publishFinance(
            null,
            "REPORT",
            null,
            "REPORT_EXPORTED",
            "Economia: rendiconto esportato",
            financeActor(token) + " ha esportato un rendiconto " + Objects.requireNonNullElse(format, "csv").toUpperCase(Locale.ROOT)
                + " per il periodo " + formatDate(effectiveFrom) + "–" + formatDate(effectiveTo) + ".",
            FinanceNotificationSeverity.INFO,
            "/finance?tab=movements",
            token,
            null,
            null
        );
        return result;
    }

    private void notifyAccountCreated(Object result, AccountRequest request, AbstractAuthenticationToken token) {
        if (!(result instanceof AccountDTO account) || request == null || token == null) return;
        String detail = request.initialBalance() == null || request.initialBalance().signum() == 0
            ? "."
            : " con saldo iniziale " + money(request.initialBalance(), account.currency()) + ".";
        publishFinance(null, "ACCOUNT", account.id(), "ACCOUNT_CREATED", "Economia: conto creato",
            financeActor(token) + " ha creato il conto " + quoted(account.name()) + detail,
            FinanceNotificationSeverity.INFO, "/finance?tab=accounts", token, null, null);
    }

    private void notifyAccountUpdated(Object result, NamedNotice before, AbstractAuthenticationToken token) {
        if (!(result instanceof AccountDTO account) || token == null) return;
        boolean reactivated = before != null && !before.active();
        publishFinance(null, "ACCOUNT", account.id(), reactivated ? "ACCOUNT_REACTIVATED" : "ACCOUNT_UPDATED",
            reactivated ? "Economia: conto riattivato" : "Economia: conto aggiornato",
            financeActor(token) + (reactivated ? " ha riattivato il conto " : " ha aggiornato il conto ") + quoted(account.name()) + ".",
            FinanceNotificationSeverity.INFO, "/finance?tab=accounts", token, null, null);
    }

    private void notifyCategory(Object result, String operation, String title, String action, AbstractAuthenticationToken token) {
        if (!(result instanceof CategoryDTO category) || token == null) return;
        publishFinance(null, "CATEGORY", category.id(), operation, title,
            financeActor(token) + " " + action + " la categoria " + quoted(category.name()) + ".",
            FinanceNotificationSeverity.INFO, "/finance?tab=categories", token, null, null);
    }

    private void notifyCategoryUpdated(Object result, NamedNotice before, AbstractAuthenticationToken token) {
        if (!(result instanceof CategoryDTO category) || token == null) return;
        boolean reactivated = before != null && !before.active();
        notifyCategory(result, reactivated ? "CATEGORY_REACTIVATED" : "CATEGORY_UPDATED",
            reactivated ? "Economia: categoria riattivata" : "Economia: categoria aggiornata",
            reactivated ? "ha riattivato" : "ha aggiornato", token);
    }

    private void notifyNamed(
        NamedNotice value,
        String operation,
        String title,
        String action,
        FinanceNotificationSeverity severity,
        String targetPath,
        AbstractAuthenticationToken token
    ) {
        if (value == null || token == null) return;
        publishFinance(null, operation.startsWith("ACCOUNT") ? "ACCOUNT" : "CATEGORY", value.id(), operation, title,
            financeActor(token) + " " + action + quoted(value.name()) + ".", severity, targetPath, token, null, null);
    }

    private void notifyMovementUpdated(Object result, AbstractAuthenticationToken token) {
        if (!(result instanceof MovementDTO movement) || token == null) return;
        if (movement.nature() == FinancialMovementNature.TRANSFER) {
            List<MovementNotice> pair = financeNoticeDataService.findMovementGroup(movement.id());
            notifyTransfer(pair, "TRANSFER_UPDATED", "Economia: trasferimento aggiornato", "ha aggiornato", token);
        } else {
            notifyMovement(toNotice(movement), "MOVEMENT_UPDATED", "Economia: movimento aggiornato", "ha aggiornato", token);
        }
    }

    private void notifyMovementRemoved(List<MovementNotice> movements, AbstractAuthenticationToken token) {
        if (movements.isEmpty() || token == null) return;
        if (movements.get(0).nature() == FinancialMovementNature.TRANSFER) {
            notifyTransfer(movements, "TRANSFER_REMOVED", "Economia: trasferimento rimosso", "ha rimosso", token);
        } else {
            notifyMovement(movements.get(0), "MOVEMENT_REMOVED", "Economia: movimento rimosso", "ha rimosso", token);
        }
    }

    private void notifyMovement(MovementNotice movement, String operation, String title, String action, AbstractAuthenticationToken token) {
        if (movement == null || token == null) return;
        String direction = movement.direction() == FinancialDirection.INCOME ? "un’entrata" : "un’uscita";
        publishFinance(null, "MOVEMENT", movement.id(), operation, title,
            financeActor(token) + " " + action + " " + direction + " di " + money(movement.amount(), movement.currency())
                + " sul conto " + quoted(movement.accountName()) + " in data " + formatDate(movement.bookingDate()) + ".",
            operation.endsWith("REMOVED") ? FinanceNotificationSeverity.WARNING : FinanceNotificationSeverity.INFO,
            operation.endsWith("REMOVED") ? "/finance?tab=movements" : "/finance?tab=movements&movementId=" + movement.id(),
            token, null, null);
    }

    private void notifyReconciliation(Object result, ReconciliationRequest request, AbstractAuthenticationToken token) {
        if (!(result instanceof MovementDTO movement) || request == null || token == null) return;
        boolean reconciled = request.reconciled();
        publishFinance(null, "MOVEMENT", movement.id(), reconciled ? "MOVEMENT_RECONCILED" : "MOVEMENT_UNRECONCILED",
            reconciled ? "Economia: movimento riconciliato" : "Economia: riconciliazione annullata",
            financeActor(token) + (reconciled ? " ha riconciliato il movimento " : " ha annullato la riconciliazione del movimento ")
                + quoted(movement.description()) + " di " + money(movement.amount(), movement.currency()) + ".",
            reconciled ? FinanceNotificationSeverity.INFO : FinanceNotificationSeverity.WARNING,
            "/finance?tab=movements&movementId=" + movement.id(), token, null, null);
    }

    private void notifyTransfer(TransferDTO transfer, String operation, String title, String action, AbstractAuthenticationToken token) {
        notifyTransfer(List.of(toNotice(transfer.outgoing()), toNotice(transfer.incoming())), operation, title, action, token);
    }

    private void notifyTransfer(List<MovementNotice> pair, String operation, String title, String action, AbstractAuthenticationToken token) {
        if (pair.size() < 2 || token == null) return;
        MovementNotice outgoing = pair.stream().filter(value -> value.direction() == FinancialDirection.EXPENSE).findFirst().orElse(null);
        MovementNotice incoming = pair.stream().filter(value -> value.direction() == FinancialDirection.INCOME).findFirst().orElse(null);
        if (outgoing == null || incoming == null) return;
        boolean removed = operation.endsWith("REMOVED");
        publishFinance(null, "TRANSFER", outgoing.id(), operation, title,
            financeActor(token) + " " + action + " il trasferimento di " + money(outgoing.amount(), outgoing.currency())
                + " da " + quoted(outgoing.accountName()) + " a " + quoted(incoming.accountName()) + ".",
            removed ? FinanceNotificationSeverity.WARNING : FinanceNotificationSeverity.INFO,
            removed ? "/finance?tab=movements" : "/finance?tab=movements&movementId=" + outgoing.id(), token, null, null);
    }

    private void notifyAttachmentAdded(Object result, AbstractAuthenticationToken token) {
        if (!(result instanceof AttachmentDTO attachment) || token == null) return;
        List<MovementNotice> movements = financeNoticeDataService.findMovementGroup(attachment.movementId());
        if (movements.isEmpty()) return;
        notifyAttachment(new AttachmentNotice(attachment.id(), attachment.fileName(), movements.get(0)), "ATTACHMENT_ADDED",
            "Economia: allegato aggiunto", "ha aggiunto", FinanceNotificationSeverity.INFO, token);
    }

    private void notifyAttachment(
        AttachmentNotice attachment,
        String operation,
        String title,
        String action,
        FinanceNotificationSeverity severity,
        AbstractAuthenticationToken token
    ) {
        if (attachment == null || token == null) return;
        publishFinance(null, "ATTACHMENT", attachment.id(), operation, title,
            financeActor(token) + " " + action + " l’allegato " + quoted(attachment.fileName()) + " del movimento "
                + quoted(attachment.movement().description()) + ".",
            severity, "/finance?tab=movements&movementId=" + attachment.movement().id(), token, null, null);
    }

    private void notifyEventBudget(Object result, AbstractAuthenticationToken token) {
        if (!(result instanceof EventSummaryDTO event) || token == null) return;
        publishFinance(null, "EVENT", event.eventId(), "EVENT_BUDGET_UPDATED", "Economia: preventivo aggiornato",
            financeActor(token) + " ha aggiornato il preventivo dell’evento " + quoted(event.eventName()) + ": compenso previsto "
                + money(event.expectedFee(), "EUR") + ", costi previsti " + money(event.expectedCosts(), "EUR") + ".",
            FinanceNotificationSeverity.INFO, "/finance?tab=events&eventId=" + event.eventId(), token, null, null);
    }

    private void notifyRollover(
        ProceedingJoinPoint joinPoint,
        Object result,
        AccountingYearStatus before,
        AbstractAuthenticationToken token
    ) {
        if (!(result instanceof YearDTO value) || before == AccountingYearStatus.ROLLED_OVER || value.status() != AccountingYearStatus.ROLLED_OVER) return;
        if (token != null) {
            publishFinance(null, "ACCOUNTING_YEAR", null, "YEAR_ROLLED_OVER", "Economia: riporto annuale completato",
                financeActor(token) + " ha completato il riporto dell’esercizio " + value.year() + " e aggiornato i saldi iniziali al 01/01/" + (value.year() + 1) + ".",
                FinanceNotificationSeverity.SUCCESS, "/finance?tab=years", token, null, null);
            return;
        }
        String systemActor = getArgument(joinPoint, String.class);
        publishFinance("finance-rollover:" + value.year(), "ACCOUNTING_YEAR", null, "YEAR_ROLLED_OVER",
            "Economia: riporto annuale completato",
            "Il riporto dell’esercizio " + value.year() + " è stato completato e i saldi iniziali al 01/01/" + (value.year() + 1) + " sono stati aggiornati.",
            FinanceNotificationSeverity.SUCCESS, "/finance?tab=years", null,
            Objects.requireNonNullElse(trimToNull(systemActor), FinanceRolloverScheduler.SYSTEM_ACTOR), "Sistema");
    }

    private void notifyRecalculation(Object result, AbstractAuthenticationToken token) {
        if (!(result instanceof YearDTO value) || token == null) return;
        publishFinance(null, "ACCOUNTING_YEAR", null, "YEAR_RECALCULATED", "Economia: esercizio ricalcolato",
            financeActor(token) + " ha ricalcolato l’esercizio " + value.year() + " e i riporti successivi.",
            FinanceNotificationSeverity.WARNING, "/finance?tab=years", token, null, null);
    }

    private void publishFinance(
        String eventKey,
        String aggregateType,
        Long aggregateId,
        String operation,
        String title,
        String message,
        FinanceNotificationSeverity severity,
        String targetPath,
        AbstractAuthenticationToken token,
        String actorId,
        String actorDisplayName
    ) {
        String effectiveActorId = token == null ? actorId : SecurityUtils.getUserIdFromAuthentication(token);
        String effectiveDisplayName = token == null ? actorDisplayName : financeActor(token);
        noticesService.enqueueFinanceNotice(new FinanceNoticeCommand(
            eventKey,
            aggregateType,
            aggregateId,
            operation,
            title,
            message,
            severity,
            targetPath,
            Objects.requireNonNullElse(trimToNull(effectiveActorId), "system"),
            Objects.requireNonNullElse(trimToNull(effectiveDisplayName), "Sistema"),
            FINANCE_NOTIFICATION_ROLES
        ));
    }

    private static MovementNotice toNotice(MovementDTO movement) {
        return new MovementNotice(
            movement.id(), movement.accountName(), movement.direction(), movement.nature(), movement.bookingDate(), movement.amount(), movement.currency(),
            movement.description(), movement.transferGroup()
        );
    }

    private static String financeActor(AbstractAuthenticationToken token) {
        String firstName = trimToNull(SecurityUtils.getFirstNameFromAuthentication(token));
        String lastName = trimToNull(SecurityUtils.getLastNameFromAuthentication(token));
        String displayName = String.join(" ", Objects.requireNonNullElse(firstName, ""), Objects.requireNonNullElse(lastName, "")).trim();
        if (!displayName.isBlank()) return displayName;
        if (token instanceof JwtAuthenticationToken jwtToken) {
            Object preferred = jwtToken.getTokenAttributes().get("preferred_username");
            if (preferred instanceof String value && trimToNull(value) != null) return value.trim();
        }
        return "Un utente";
    }

    private static String money(BigDecimal amount, String currency) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.ITALY);
        try {
            formatter.setCurrency(Currency.getInstance(Objects.requireNonNullElse(trimToNull(currency), "EUR")));
        } catch (IllegalArgumentException ignored) {
            formatter.setCurrency(Currency.getInstance("EUR"));
        }
        return formatter.format(Objects.requireNonNullElse(amount, BigDecimal.ZERO));
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

    private static int getIntegerArgument(ProceedingJoinPoint joinPoint, int index) {
        Object value = joinPoint.getArgs()[index];
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static <T> T getArgument(ProceedingJoinPoint joinPoint, Class<T> type) {
        return getArgument(joinPoint, type, 0);
    }

    private static <T> T getArgument(ProceedingJoinPoint joinPoint, Class<T> type, int occurrence) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(type::isInstance)
            .map(type::cast)
            .skip(occurrence)
            .findFirst()
            .orElse(null);
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
