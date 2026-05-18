package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.dto.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class NoticesAspect {

    private final NoticesService noticesService;

    public NoticesAspect(NoticesService noticesService) {
        this.noticesService = noticesService;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.save()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.save())")
    public Object addNoticesOnSave(ProceedingJoinPoint joinPoint) throws Throwable {
        StateAndTokenResult result = getStateAndToken(joinPoint);

        if (result.stateOpt() != null && result.tokenOpt() != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String name, namePublic, message, messagePublic;

            if (className.toLowerCase().contains("album")) {
                name = "Nuovo album creato";
                namePublic = "Nuovo album pubblicato";
                message = String.format("L'album \"%s\" è stato creato", result.stateOpt().getName());
                messagePublic = String.format("Il nuovo album \"%s\" è stato pubblicato", result.stateOpt().getName());
            } else {
                name = "Nuova traccia creata";
                namePublic = "Nuova traccia pubblicata";
                message = String.format("La traccia \"%s\" è stata creata", result.stateOpt().getName());
                messagePublic = String.format("La nuova traccia \"%s\" è stata pubblicata", result.stateOpt().getName());
            }

            if (result.stateOpt().getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(namePublic, messagePublic, result.tokenOpt()).block();
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, result.tokenOpt()).block();
            }
        }

        return joinPoint.proceed();
    }

    @Around("execution(private * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.updateDto())")
    public Object addNoticesOnUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        StateAndTokenResult result = getStateAndToken(joinPoint);
        Object proceed = joinPoint.proceed();
        StateFieldOpenSearchDTO newStateOpt = null;

        if (proceed instanceof StateFieldOpenSearchDTO) {
            newStateOpt = (StateFieldOpenSearchDTO) proceed;
        }

        if (result.stateOpt() != null && result.tokenOpt() != null && newStateOpt != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String name, namePublic, message, messagePublic;

            if (className.toLowerCase().contains("album")) {
                name = "Album aggiornato";
                namePublic = "Album pubblicato";
                message = String.format("L'album \"%s\" è stato aggiornato", newStateOpt.getName());
                messagePublic = String.format("L'album \"%s\" è stato pubblicato", newStateOpt.getName());
            } else {
                name = "Traccia aggiornata";
                namePublic = "Traccia pubblicata";
                message = String.format("La traccia \"%s\" è stata aggiornata", result.stateOpt().getName());
                messagePublic = String.format("La traccia \"%s\" è stata pubblicata", result.stateOpt().getName());
            }

            if (newStateOpt.getState() == StateEnum.PUBLIC) {
                if (result.stateOpt().getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeWholeTenant(namePublic, messagePublic, result.tokenOpt()).block();
                } else {
                    noticesService.addNoticeWholeTenant(name, message, result.tokenOpt()).block();
                }
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, result.tokenOpt()).block();
            }
        }

        return proceed;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl.delete()) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.delete())")
    public Object addNoticesOnDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        StateAndTokenResult result = getStateAndToken(joinPoint);

        if (result.stateOpt() != null && result.tokenOpt() != null) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String name, message;

            if (className.toLowerCase().contains("album")) {
                name = "Album rimosso";
                message = String.format("L'album \"%s\" è stato rimosso", result.stateOpt().getName());
            } else {
                name = "Traccia rimossa";
                message = String.format("La traccia \"%s\" è stata rimossa", result.stateOpt().getName());
            }

            if (result.stateOpt().getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, result.tokenOpt()).block();
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, result.tokenOpt()).block();
            }
        }

        return joinPoint.proceed();
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

            if (className.toLowerCase().contains("instrument")) {
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

    private static StateAndTokenResult getStateAndToken(ProceedingJoinPoint joinPoint) {
        StateFieldOpenSearchDTO stateOpt = (StateFieldOpenSearchDTO) getCommonFieldsOpenSearchDTO(joinPoint);
        return new StateAndTokenResult(stateOpt, getAbstractAuthenticationToken(joinPoint));
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

    private record StateAndTokenResult(StateFieldOpenSearchDTO stateOpt, AbstractAuthenticationToken tokenOpt) {
    }

    private record ResultNameMessage(String name, String message) {
    }
}
