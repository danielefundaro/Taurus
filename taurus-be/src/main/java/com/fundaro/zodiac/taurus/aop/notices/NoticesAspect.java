package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.CommonOpenSearchService;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Aspect
@Component
public class NoticesAspect {

    private final NoticesService noticesService;

    private final AlbumsService albumsService;

    private final TracksService tracksService;

    public NoticesAspect(NoticesService noticesService, AlbumsService albumsService, TracksService tracksService) {
        this.noticesService = noticesService;
        this.albumsService = albumsService;
        this.tracksService = tracksService;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.save()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.save())")
    public Object addNoticesOnSave(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        FinalValue finalValue = getFinalValue(joinPoint);

        if (finalValue.stateOpt() != null && abstractAuthenticationToken != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String name, nameWithStatus, message, messageWithStatus;

            if (className.toLowerCase().contains("album")) {
                name = "Nuovo album creato";
                nameWithStatus = "Nuovo album pubblicato";
                message = String.format("L'album \"%s\" è stato creato", finalValue.stateOpt().getName());
                messageWithStatus = String.format("Il nuovo album \"%s\" è stato pubblicato", finalValue.stateOpt().getName());
            } else {
                name = "Nuova traccia creata";
                nameWithStatus = "Nuova traccia pubblicata";
                message = String.format("La traccia \"%s\" è stata creata", finalValue.stateOpt().getName());
                messageWithStatus = String.format("La nuova traccia \"%s\" è stata pubblicata", finalValue.stateOpt().getName());
            }

            if (finalValue.stateOpt().getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, abstractAuthenticationToken).block();
            } else {
                noticesService.addNoticesExcludeRoleUsers(nameWithStatus, messageWithStatus, abstractAuthenticationToken).block();
            }
        }

        return joinPoint.proceed();
    }

    @Around("execution(private * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.update()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.update()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.partialUpdate()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.partialUpdate())")
    public Object addNoticesOnUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        FinalValue finalValue = getFinalValue(joinPoint);

        if (id != null && abstractAuthenticationToken != null && finalValue.stateOpt() != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String nameForUsers, namePublic, nameUpdate, nameDelete, messageForUsers, messagePublic, messageUpdate, messageDelete;
            StateFieldOpenSearchDTO oldState = getStateFieldOpenSearchDTO(className, id, abstractAuthenticationToken);

            if (className.toLowerCase().contains("album")) {
                nameForUsers = "Nuovo album creato";
                namePublic = "Album pubblicato";
                nameUpdate = "Album aggiornato";
                nameDelete = "Album rimosso";
                messageForUsers = String.format("L'album \"%s\" è stato creato", finalValue.stateOpt().getName());
                messagePublic = String.format("L'album \"%s\" è stato pubblicato", finalValue.stateOpt().getName());
                messageUpdate = String.format("L'album \"%s\" è stato aggiornato", finalValue.stateOpt().getName());
                messageDelete = String.format("L'album \"%s\" è stato rimosso", finalValue.stateOpt().getName());;
            } else {
                nameForUsers = "Nuova traccia creata";
                namePublic = "Traccia pubblicata";
                nameUpdate = "Traccia aggiornata";
                nameDelete = "Traccia rimossa";
                messageForUsers = String.format("La traccia \"%s\" è stata creata", finalValue.stateOpt().getName());
                messagePublic = String.format("La traccia \"%s\" è stata pubblicata", finalValue.stateOpt().getName());
                messageUpdate = String.format("La traccia \"%s\" è stata aggiornata", finalValue.stateOpt().getName());
                messageDelete = String.format("La traccia \"%s\" è stata rimossa", finalValue.stateOpt().getName());
            }

            if (oldState.getState() != StateEnum.PUBLIC) {
                if (finalValue.stateOpt.getState() == StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers(nameForUsers, messageForUsers, abstractAuthenticationToken)
                        .then(Mono.fromCallable(() -> noticesService.addNoticesExcludeRoleUsers(namePublic, messagePublic, abstractAuthenticationToken))).block();
                } else {
                    noticesService.addNoticesExcludeRoleUsers(nameUpdate, messageUpdate, abstractAuthenticationToken).block();
                }
            } else {
                if (finalValue.stateOpt.getState() == StateEnum.PUBLIC) {
                    noticesService.addNoticeWholeTenant(nameUpdate, messageUpdate, abstractAuthenticationToken).block();
                } else {
                    noticesService.addNoticeOnlyRoleUsers(nameDelete, messageDelete, abstractAuthenticationToken)
                        .then(Mono.fromCallable(() -> noticesService.addNoticesExcludeRoleUsers(nameUpdate, messageUpdate, abstractAuthenticationToken))).block();
                }
            }
        }

        return finalValue.proceed();
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.delete()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.delete())")
    public Object addNoticesOnDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        FinalValue finalValue = getFinalValue(joinPoint);

        if (id != null && abstractAuthenticationToken != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String name, message;
            StateFieldOpenSearchDTO currentStateObj = getStateFieldOpenSearchDTO(className, id, abstractAuthenticationToken);

            if (currentStateObj != null) {
                if (className.toLowerCase().contains("album")) {
                    name = "Album rimosso";
                    message = String.format("L'album \"%s\" è stato rimosso", currentStateObj.getName());
                } else {
                    name = "Traccia rimossa";
                    message = String.format("La traccia \"%s\" è stata rimossa", currentStateObj.getName());
                }

                if (currentStateObj.getState() == StateEnum.PUBLIC) {
                    noticesService.addNoticeWholeTenant(name, message, abstractAuthenticationToken).block();
                } else {
                    noticesService.addNoticesExcludeRoleUsers(name, message, abstractAuthenticationToken).block();
                }
            }
        }

        return finalValue.proceed();
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.save()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.update()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.partialUpdate()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.delete())")
    public Object addNoticesOnInstrumentsAction(ProceedingJoinPoint joinPoint) throws Throwable {
        InstrumentsDTO instrumentOpt = (InstrumentsDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        AbstractAuthenticationToken tokenOpt = getAbstractAuthenticationToken(joinPoint);

        if (instrumentOpt != null && tokenOpt != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();

            if (className.toLowerCase().contains("instrument")) {
                ResultNameMessage resultNameMessage = switch (methodName) {
                    case "save":
                        yield new ResultNameMessage("Nuovo strumento", String.format("Lo strumento \"%s\" è stato aggiunto", instrumentOpt.getName()));
                    case "update":
                    case "partialUpdate":
                        yield new ResultNameMessage("Strumento aggiornato", String.format("Le informazioni dello strumento \"%s\" sono state aggiornate", instrumentOpt.getName()));
                    case "delete":
                        yield new ResultNameMessage("Strumento rimosso", String.format("L'utente \"%s\" è stato rimosso", instrumentOpt.getName()));
                    default:
                        yield new ResultNameMessage(null, null);
                };

                if (resultNameMessage.name() != null && resultNameMessage.message() != null) {
                    noticesService.addNoticesExcludeRoleUsers(resultNameMessage.name(), resultNameMessage.message(), tokenOpt).block();
                }
            }
        }

        return joinPoint.proceed();
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.save()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.update()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.partialUpdate()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.delete())")
    public Object addNoticesOnUsersAction(ProceedingJoinPoint joinPoint) throws Throwable {
        UsersDTO userOpt = (UsersDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        AbstractAuthenticationToken tokenOpt = getAbstractAuthenticationToken(joinPoint);

        if (userOpt != null && tokenOpt != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();

            if (className.toLowerCase().contains("user")) {
                ResultNameMessage resultNameMessage = switch (methodName) {
                    case "save":
                        yield new ResultNameMessage("Nuovo utente", String.format("L'utente \"%s %s\" è stato aggiunto", userOpt.getName(), userOpt.getLastName()));
                    case "update":
                    case "partialUpdate":
                        yield new ResultNameMessage("Utente aggiornato", String.format("Le informazioni dell'utente \"%s %s\" sono state aggiornate", userOpt.getName(), userOpt.getLastName()));
                    case "delete":
                        yield new ResultNameMessage("Utente rimosso", String.format("L'utente \"%s %s\" è stato rimosso", userOpt.getName(), userOpt.getLastName()));
                    default:
                        yield new ResultNameMessage(null, null);
                };

                if (resultNameMessage.name() != null && resultNameMessage.message() != null) {
                    noticesService.addNoticesAdmins(resultNameMessage.name(), resultNameMessage.message(), tokenOpt).block();
                }
            }
        }

        return joinPoint.proceed();
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.save()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.update()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.partialUpdate()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.delete())")
    public Object addNoticesOnTenantsAction(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantsDTO tenantOpt = (TenantsDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        AbstractAuthenticationToken tokenOpt = getAbstractAuthenticationToken(joinPoint);

        if (tenantOpt != null && tokenOpt != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();

            if (className.toLowerCase().contains("tenant")) {
                ResultNameMessage resultNameMessage = switch (methodName) {
                    case "save":
                        yield new ResultNameMessage("Nuovo tenant", String.format("Il tenant \"%s\" e codice \"%s\" è stato aggiunto", tenantOpt.getName(), tenantOpt.getCode()));
                    case "update":
                    case "partialUpdate":
                        yield new ResultNameMessage("Tenant aggiornato", String.format("Le informazioni del tenant \"%s\" e codice \"%s\" sono state aggiornate", tenantOpt.getName(), tenantOpt.getCode()));
                    case "delete":
                        yield new ResultNameMessage("Strumento rimosso", String.format("Il tenant \"%s\" e codice \"%s\" è stato rimosso", tenantOpt.getName(), tenantOpt.getCode()));
                    default:
                        yield new ResultNameMessage(null, null);
                };

                if (resultNameMessage.name() != null && resultNameMessage.message() != null) {
                    noticesService.addNoticesSuperAdmins(resultNameMessage.name(), resultNameMessage.message(), tokenOpt).block();
                }
            }
        }

        return joinPoint.proceed();
    }

    private static FinalValue getFinalValue(ProceedingJoinPoint joinPoint) throws Throwable {
        Object proceed = joinPoint.proceed();
        StateFieldOpenSearchDTO stateOpt = null;

        if (proceed instanceof StateFieldOpenSearchDTO) {
            stateOpt = (StateFieldOpenSearchDTO) proceed;
        }

        return new FinalValue(proceed, stateOpt);
    }

    private StateFieldOpenSearchDTO getStateFieldOpenSearchDTO(String className, String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        CommonOpenSearchService s = className.toLowerCase().contains("album") ? albumsService : tracksService;
        StateFieldOpenSearchDTO currentStateObj = (StateFieldOpenSearchDTO) s.findOne(id, abstractAuthenticationToken).block();
        return currentStateObj;
    }

    private static CommonFieldsOpenSearchDTO getCommonFieldsOpenSearchDTO(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof CommonFieldsOpenSearchDTO)
            .map(arg -> (CommonFieldsOpenSearchDTO) arg)
            .findFirst().orElse(null);
    }

    private static AbstractAuthenticationToken getAbstractAuthenticationToken(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof AbstractAuthenticationToken)
            .map(arg -> (AbstractAuthenticationToken) arg)
            .findFirst().orElse(null);
    }

    private static String getId(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof String)
            .map(arg -> (String) arg)
            .findFirst().orElse(null);
    }

    private record FinalValue(Object proceed, StateFieldOpenSearchDTO stateOpt) {
    }

    private record ResultNameMessage(String name, String message) {
    }
}
