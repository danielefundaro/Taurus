package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.*;
import com.fundaro.zodiac.taurus.service.dto.*;
import org.apache.logging.log4j.util.Strings;
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

    private final UsersService usersService;

    private final TenantsService tenantsService;

    private final InstrumentsService instrumentsService;

    private final AlbumsService albumsService;

    private final TracksService tracksService;

    public NoticesAspect(NoticesService noticesService, UsersService usersService, TenantsService tenantsService, InstrumentsService instrumentsService, AlbumsService albumsService, TracksService tracksService) {
        this.noticesService = noticesService;
        this.usersService = usersService;
        this.tenantsService = tenantsService;
        this.instrumentsService = instrumentsService;
        this.albumsService = albumsService;
        this.tracksService = tracksService;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.save(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.save(..))")
    public Object addNoticesOnSave(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            if (!(result instanceof StateFieldOpenSearchDTO stateFieldOpenSearchDTO)) {
                return Mono.just(result);
            }

            String className = joinPoint.getTarget().getClass().getSimpleName();
            String name, nameWithStatus, message, messageWithStatus;

            if (className.toLowerCase().contains("album")) {
                name = "Nuovo album creato";
                nameWithStatus = "Nuovo album pubblicato";
                message = String.format("L'album \"%s\" è stato creato", stateFieldOpenSearchDTO.getName());
                messageWithStatus = String.format("Il nuovo album \"%s\" è stato pubblicato", stateFieldOpenSearchDTO.getName());
            } else {
                name = "Nuova traccia creata";
                nameWithStatus = "Nuova traccia pubblicata";
                message = String.format("La traccia \"%s\" è stata creata", stateFieldOpenSearchDTO.getName());
                messageWithStatus = String.format("La nuova traccia \"%s\" è stata pubblicata", stateFieldOpenSearchDTO.getName());
            }

            Mono<?> noticeMono;

            if (stateFieldOpenSearchDTO.getState() == StateEnum.PUBLIC) {
                noticeMono = noticesService.addNoticeWholeTenant(name, message, abstractAuthenticationToken);
            } else {
                noticeMono = noticesService.addNoticesExcludeRoleUsers(nameWithStatus, messageWithStatus, abstractAuthenticationToken);
            }

            return noticeMono.thenReturn(result);
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.partialUpdate(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.partialUpdate(..))")
    public Object addNoticesOnUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null || id == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            if (!(result instanceof StateFieldOpenSearchDTO stateFieldOpenSearchDTO)) {
                return Mono.just(result);
            }

            String className = joinPoint.getTarget().getClass().getSimpleName();
            return getStateFieldOpenSearchDTO(className, id, abstractAuthenticationToken).flatMap(oldState -> {
                if (oldState == null) {
                    return Mono.just(result);
                }

                String nameForUsers, namePublic, nameUpdate, nameDelete, messageForUsers, messagePublic, messageUpdate, messageDelete;

                if (className.toLowerCase().contains("album")) {
                    nameForUsers = "Nuovo album creato";
                    namePublic = "Album pubblicato";
                    nameUpdate = "Album aggiornato";
                    nameDelete = "Album rimosso";
                    messageForUsers = String.format("L'album \"%s\" è stato creato", stateFieldOpenSearchDTO.getName());
                    messagePublic = String.format("L'album \"%s\" è stato pubblicato", stateFieldOpenSearchDTO.getName());
                    messageUpdate = String.format("L'album \"%s\" è stato aggiornato", stateFieldOpenSearchDTO.getName());
                    messageDelete = String.format("L'album \"%s\" è stato rimosso", stateFieldOpenSearchDTO.getName());
                } else {
                    nameForUsers = "Nuova traccia creata";
                    namePublic = "Traccia pubblicata";
                    nameUpdate = "Traccia aggiornata";
                    nameDelete = "Traccia rimossa";
                    messageForUsers = String.format("La traccia \"%s\" è stata creata", stateFieldOpenSearchDTO.getName());
                    messagePublic = String.format("La traccia \"%s\" è stata pubblicata", stateFieldOpenSearchDTO.getName());
                    messageUpdate = String.format("La traccia \"%s\" è stata aggiornata", stateFieldOpenSearchDTO.getName());
                    messageDelete = String.format("La traccia \"%s\" è stata rimossa", stateFieldOpenSearchDTO.getName());
                }

                Mono<?> noticeMono;

                if (oldState.getState() != StateEnum.PUBLIC) {
                    if (stateFieldOpenSearchDTO.getState() == StateEnum.PUBLIC) {
                        noticeMono = noticesService.addNoticeOnlyRoleUsers(nameForUsers, messageForUsers, abstractAuthenticationToken)
                            .then(Mono.fromCallable(() -> noticesService.addNoticesExcludeRoleUsers(namePublic, messagePublic, abstractAuthenticationToken)));
                    } else {
                        noticeMono = noticesService.addNoticesExcludeRoleUsers(nameUpdate, messageUpdate, abstractAuthenticationToken);
                    }
                } else {
                    if (stateFieldOpenSearchDTO.getState() == StateEnum.PUBLIC) {
                        noticeMono = noticesService.addNoticeWholeTenant(nameUpdate, messageUpdate, abstractAuthenticationToken);
                    } else {
                        noticeMono = noticesService.addNoticeOnlyRoleUsers(nameDelete, messageDelete, abstractAuthenticationToken)
                            .then(Mono.fromCallable(() -> noticesService.addNoticesExcludeRoleUsers(nameUpdate, messageUpdate, abstractAuthenticationToken)));
                    }
                }

                return noticeMono.thenReturn(result);
            });
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.delete(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.delete(..))")
    public Object addNoticesOnDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null || id == null) {
            return proceed;
        }

        String className = joinPoint.getTarget().getClass().getSimpleName();
        return getStateFieldOpenSearchDTO(className, id, abstractAuthenticationToken).flatMap(currentState -> {
            if (currentState == null) {
                return mono;
            }

            return mono.flatMap(result -> {
                String name, message;

                if (className.toLowerCase().contains("album")) {
                    name = "Album rimosso";
                    message = String.format("L'album \"%s\" è stato rimosso", currentState.getName());
                } else {
                    name = "Traccia rimossa";
                    message = String.format("La traccia \"%s\" è stata rimossa", currentState.getName());
                }

                Mono<?> noticeMono;

                if (currentState.getState() == StateEnum.PUBLIC) {
                    noticeMono = noticesService.addNoticeWholeTenant(name, message, abstractAuthenticationToken);
                } else {
                    noticeMono = noticesService.addNoticesExcludeRoleUsers(name, message, abstractAuthenticationToken);
                }

                return noticeMono.thenReturn(result);
            });
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.save(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.partialUpdate(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.save(..))")
    public Object addNoticesOnInstrumentsAction(ProceedingJoinPoint joinPoint) throws Throwable {
        InstrumentsDTO instrumentsDTO = (InstrumentsDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || instrumentsDTO == null || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            String methodName = joinPoint.getSignature().getName();
            ResultNameMessage resultNameMessage = switch (methodName) {
                case "save":
                    yield new ResultNameMessage("Nuovo strumento", String.format("Lo strumento \"%s\" è stato aggiunto", instrumentsDTO.getName()));
                case "update":
                case "partialUpdate":
                    yield new ResultNameMessage("Strumento aggiornato", String.format("Le informazioni dello strumento \"%s\" sono state aggiornate", instrumentsDTO.getName()));
                case "delete":
                    yield new ResultNameMessage("Strumento rimosso", String.format("L'utente \"%s\" è stato rimosso", instrumentsDTO.getName()));
                default:
                    yield new ResultNameMessage(null, null);
            };

            if (Strings.isBlank(resultNameMessage.name()) || Strings.isBlank(resultNameMessage.message())) {
                return Mono.just(result);
            }

            return noticesService.addNoticesExcludeRoleUsers(resultNameMessage.name(), resultNameMessage.message(), abstractAuthenticationToken)
                .thenReturn(result);
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.InstrumentsServiceImpl.delete(..))")
    public Object addNoticesOnInstrumentsDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        String id = getId(joinPoint);
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || id == null || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            InstrumentsDTO instrumentsDTO = (InstrumentsDTO) result;
            ResultNameMessage resultNameMessage = new ResultNameMessage("Strumento rimosso", String.format("L'utente \"%s\" è stato rimosso", instrumentsDTO.getName()));

            if (Strings.isBlank(resultNameMessage.name()) || Strings.isBlank(resultNameMessage.message())) {
                return Mono.just(result);
            }

            return noticesService.addNoticesExcludeRoleUsers(resultNameMessage.name(), resultNameMessage.message(), abstractAuthenticationToken)
                .thenReturn(result);
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.save(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.partialUpdate(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.UsersServiceImpl.delete(..))")
    public Object addNoticesOnUsersAction(ProceedingJoinPoint joinPoint) throws Throwable {
        UsersDTO usersDTO = (UsersDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || usersDTO == null || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            String methodName = joinPoint.getSignature().getName();
            ResultNameMessage resultNameMessage = switch (methodName) {
                case "save":
                    yield new ResultNameMessage("Nuovo utente", String.format("L'utente \"%s %s\" è stato aggiunto", usersDTO.getName(), usersDTO.getLastName()));
                case "update":
                case "partialUpdate":
                    yield new ResultNameMessage("Utente aggiornato", String.format("Le informazioni dell'utente \"%s %s\" sono state aggiornate", usersDTO.getName(), usersDTO.getLastName()));
                case "delete":
                    yield new ResultNameMessage("Utente rimosso", String.format("L'utente \"%s %s\" è stato rimosso", usersDTO.getName(), usersDTO.getLastName()));
                default:
                    yield new ResultNameMessage(null, null);
            };

            if (Strings.isBlank(resultNameMessage.name()) || Strings.isBlank(resultNameMessage.message())) {
                return Mono.just(result);
            }

            return noticesService.addNoticesAdmins(resultNameMessage.name(), resultNameMessage.message(), abstractAuthenticationToken)
                .thenReturn(result);
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.save(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.partialUpdate(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TenantsServiceImpl.delete(..))")
    public Object addNoticesOnTenantsAction(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantsDTO tenantsDTO = (TenantsDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || tenantsDTO == null || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            String methodName = joinPoint.getSignature().getName();
            ResultNameMessage resultNameMessage = switch (methodName) {
                case "save":
                    yield new ResultNameMessage("Nuovo tenant", String.format("Il tenant \"%s\" e codice \"%s\" è stato aggiunto", tenantsDTO.getName(), tenantsDTO.getCode()));
                case "update":
                case "partialUpdate":
                    yield new ResultNameMessage("Tenant aggiornato", String.format("Le informazioni del tenant \"%s\" e codice \"%s\" sono state aggiornate", tenantsDTO.getName(), tenantsDTO.getCode()));
                case "delete":
                    yield new ResultNameMessage("Strumento rimosso", String.format("Il tenant \"%s\" e codice \"%s\" è stato rimosso", tenantsDTO.getName(), tenantsDTO.getCode()));
                default:
                    yield new ResultNameMessage(null, null);
            };

            if (Strings.isBlank(resultNameMessage.name()) || Strings.isBlank(resultNameMessage.message())) {
                return Mono.just(result);
            }

            return noticesService.addNoticesSuperAdmins(resultNameMessage.name(), resultNameMessage.message(), abstractAuthenticationToken)
                .thenReturn(result);
        });
    }

    private Mono<StateFieldOpenSearchDTO> getStateFieldOpenSearchDTO(String className, String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        CommonOpenSearchService service = className.toLowerCase().contains("album") ? albumsService : tracksService;
        return service.findOne(id, abstractAuthenticationToken).cast(StateFieldOpenSearchDTO.class);
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

    private record ResultNameMessage(String name, String message) {
    }
}
