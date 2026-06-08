package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.*;
import com.fundaro.zodiac.taurus.service.dto.*;
import com.fundaro.zodiac.taurus.service.impl.*;
import org.apache.commons.io.FilenameUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.codec.multipart.FilePart;
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

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.save(..))")
    private Object onSave(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            if (!(result instanceof CommonFieldsOpenSearchDTO commonFieldsOpenSearchDTO)) {
                return Mono.just(result);
            }

            Mono<?> noticeMono;

            if (commonFieldsOpenSearchDTO instanceof AlbumsDTO albumsDTO) {
                if (albumsDTO.getState() == StateEnum.PUBLIC) {
                    noticeMono = noticesService.addNoticeWholeTenant("Nuovo album creato", String.format("L'album \"%s\" è stato creato", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                } else {
                    noticeMono = noticesService.addNoticesExcludeRoleUsers("Nuovo album creato", String.format("L'album \"%s\" è stato creato", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                }
            } else if (commonFieldsOpenSearchDTO instanceof TracksDTO tracksDTO) {
                if (tracksDTO.getState() == StateEnum.PUBLIC) {
                    noticeMono = noticesService.addNoticeWholeTenant("Nuova traccia creata", String.format("La traccia \"%s\" è stata creata", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                } else {
                    noticeMono = noticesService.addNoticesExcludeRoleUsers("Nuova traccia creata", String.format("La traccia \"%s\" è stata creata", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                }
            } else if (commonFieldsOpenSearchDTO instanceof InstrumentsDTO) {
                noticeMono = noticesService.addNoticesExcludeRoleUsers("Nuovo strumento", String.format("Lo strumento \"%s\" è stato aggiunto", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
            } else if (commonFieldsOpenSearchDTO instanceof UsersDTO usersDTO) {
                noticeMono = noticesService.addNoticesAdmins("Nuovo utente", String.format("L'utente \"%s %s\" è stato aggiunto", usersDTO.getName(), usersDTO.getLastName()), abstractAuthenticationToken);
            } else if (commonFieldsOpenSearchDTO instanceof TenantsDTO tenantsDTO) {
                noticeMono = noticesService.addNoticesSuperAdmins("Nuovo tenant", String.format("Il tenant \"%s\" e codice \"%s\" è stato aggiunto", tenantsDTO.getName(), tenantsDTO.getCode()), abstractAuthenticationToken);
            } else {
                return Mono.just(result);
            }

            return noticeMono.thenReturn(result);
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.uploadFile(..))")
    private Object onUploadFile(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        FilePart filePart = getFilePart(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null) {
            return proceed;
        }

        return mono.then(Mono.defer(() -> {
            if (id != null) {
                return tracksService.findOne(id, abstractAuthenticationToken)
                    .flatMap(tracksDTO -> noticesService.addNoticesExcludeRoleUsers("Traccia aggiornata", String.format("Le informazioni della traccia \"%s\" sono state aggiornate", tracksDTO.getName()), abstractAuthenticationToken));
            }

            return noticesService.addNoticesExcludeRoleUsers("Nuova traccia creata", String.format("La traccia \"%s\" è stata creata", FilenameUtils.removeExtension(filePart.filename())), abstractAuthenticationToken);
        }));
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.partialUpdate(..))")
    private Object onUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null || id == null) {
            return proceed;
        }

        Mono<?> monoFindOne;

        if (joinPoint.getTarget() instanceof AlbumsServiceImpl) {
            monoFindOne = albumsService.findOne(id, abstractAuthenticationToken);
        } else if (joinPoint.getTarget() instanceof TracksServiceImpl) {
            monoFindOne = tracksService.findOne(id, abstractAuthenticationToken);
        } else if (joinPoint.getTarget() instanceof InstrumentsServiceImpl) {
            monoFindOne = instrumentsService.findOne(id, abstractAuthenticationToken);
        } else if (joinPoint.getTarget() instanceof UsersServiceImpl) {
            monoFindOne = usersService.findOne(id, abstractAuthenticationToken);
        } else if (joinPoint.getTarget() instanceof TenantsServiceImpl) {
            monoFindOne = tenantsService.findOne(id, abstractAuthenticationToken);
        } else {
            monoFindOne = Mono.empty();
        }

        return monoFindOne.flatMap(o -> {
            if (!(o instanceof CommonFieldsOpenSearchDTO oldCommonField)) {
                return Mono.just(o);
            }

            return mono.flatMap(result -> {
                if (!(result instanceof CommonFieldsOpenSearchDTO commonFieldsOpenSearchDTO)) {
                    return Mono.just(result);
                }

                Mono<?> noticeMono;

                if (commonFieldsOpenSearchDTO instanceof AlbumsDTO albumsDTO) {
                    AlbumsDTO oldAlbum = (AlbumsDTO) oldCommonField;

                    if (oldAlbum.getState() != StateEnum.PUBLIC) {
                        if (albumsDTO.getState() != StateEnum.PUBLIC) {
                            noticeMono = noticesService.addNoticesExcludeRoleUsers("Album aggiornato", String.format("Le informazioni dell'album \"%s\" sono state aggiornate", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                        } else {
                            noticeMono = noticesService.addNoticeOnlyRoleUsers("Nuovo album creato", String.format("L'album \"%s\" è stato creato", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)
                                .then(Mono.defer(() -> noticesService.addNoticesExcludeRoleUsers("Album pubblicato", String.format("L'album \"%s\" è stato pubblicato", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)));
                        }
                    } else {
                        if (albumsDTO.getState() != StateEnum.PUBLIC) {
                            noticeMono = noticesService.addNoticeOnlyRoleUsers("Album rimosso", String.format("L'album \"%s\" è stato rimosso", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)
                                .then(Mono.defer(() -> noticesService.addNoticesExcludeRoleUsers("Album aggiornato", String.format("Le informazioni dell'album \"%s\" sono state aggiornate", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)));
                        } else {
                            noticeMono = noticesService.addNoticeWholeTenant("Album aggiornato", String.format("Le informazioni dell'album \"%s\" sono state aggiornate", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                        }
                    }
                } else if (commonFieldsOpenSearchDTO instanceof TracksDTO tracksDTO) {
                    TracksDTO oldTrack = (TracksDTO) oldCommonField;

                    if (oldTrack.getState() != StateEnum.PUBLIC) {
                        if (tracksDTO.getState() != StateEnum.PUBLIC) {
                            noticeMono = noticesService.addNoticesExcludeRoleUsers("Traccia aggiornata", String.format("Le informazioni della traccia \"%s\" sono state aggiornate", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                        } else {
                            noticeMono = noticesService.addNoticeOnlyRoleUsers("Nuova traccia creata", String.format("La traccia \"%s\" è stato creata", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)
                                .then(Mono.defer(() -> noticesService.addNoticesExcludeRoleUsers("Traccia pubblicata", String.format("L'album \"%s\" è stata pubblicata", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)));
                        }
                    } else {
                        if (tracksDTO.getState() != StateEnum.PUBLIC) {
                            noticeMono = noticesService.addNoticeOnlyRoleUsers("Traccia rimossa", String.format("La traccia \"%s\" è stata rimossa", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)
                                .then(Mono.defer(() -> noticesService.addNoticesExcludeRoleUsers("Traccia aggiornata", String.format("Le informazioni della traccia \"%s\" sono state aggiornate", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken)));
                        } else {
                            noticeMono = noticesService.addNoticeWholeTenant("Traccia aggiornata", String.format("Le informazioni della traccia \"%s\" sono state aggiornate", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                        }
                    }
                } else if (commonFieldsOpenSearchDTO instanceof InstrumentsDTO) {
                    noticeMono = noticesService.addNoticesExcludeRoleUsers("Strumento aggiornato", String.format("Lo strumento \"%s\" è stato aggiornato", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                } else if (commonFieldsOpenSearchDTO instanceof UsersDTO usersDTO) {
                    noticeMono = noticesService.addNoticesAdmins("Utente aggiornato", String.format("L'utente \"%s %s\" è stato aggiornato", usersDTO.getName(), usersDTO.getLastName()), abstractAuthenticationToken);
                } else if (commonFieldsOpenSearchDTO instanceof TenantsDTO tenantsDTO) {
                    noticeMono = noticesService.addNoticesSuperAdmins("Tenant aggiornato", String.format("Il tenant \"%s\" con codice \"%s\" è stato aggiornato", tenantsDTO.getName(), tenantsDTO.getCode()), abstractAuthenticationToken);
                } else {
                    return Mono.just(result);
                }

                return noticeMono.thenReturn(result);
            });
        });
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.delete(..))")
    private Object onDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken abstractAuthenticationToken = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        Object proceed = joinPoint.proceed();

        if (!(proceed instanceof Mono<?> mono) || abstractAuthenticationToken == null || id == null) {
            return proceed;
        }

        return mono.flatMap(result -> {
            if (!(result instanceof CommonFieldsOpenSearchDTO commonFieldsOpenSearchDTO)) {
                return Mono.just(result);
            }

            Mono<?> noticeMono;

            if (commonFieldsOpenSearchDTO instanceof AlbumsDTO albumsDTO) {
                if (albumsDTO.getState() == StateEnum.PUBLIC) {
                    noticeMono = noticesService.addNoticeWholeTenant("Album rimosso", String.format("L'album \"%s\" è stato rimosso", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                } else {
                    noticeMono = noticesService.addNoticesExcludeRoleUsers("Album rimosso", String.format("L'album \"%s\" è stato rimosso", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                }
            } else if (commonFieldsOpenSearchDTO instanceof TracksDTO tracksDTO) {
                if (tracksDTO.getState() == StateEnum.PUBLIC) {
                    noticeMono = noticesService.addNoticeWholeTenant("Traccia rimossa", String.format("La traccia \"%s\" è stata rimossa", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                } else {
                    noticeMono = noticesService.addNoticesExcludeRoleUsers("Traccia rimossa", String.format("La traccia \"%s\" è stata rimossa", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
                }
            } else if (commonFieldsOpenSearchDTO instanceof InstrumentsDTO) {
                noticeMono = noticesService.addNoticesExcludeRoleUsers("Strumento rimosso", String.format("Lo strumento \"%s\" è stato rimosso", commonFieldsOpenSearchDTO.getName()), abstractAuthenticationToken);
            } else if (commonFieldsOpenSearchDTO instanceof UsersDTO usersDTO) {
                noticeMono = noticesService.addNoticesAdmins("Utente rimosso", String.format("L'utente \"%s %s\" è stato rimosso", usersDTO.getName(), usersDTO.getLastName()), abstractAuthenticationToken);
            } else if (commonFieldsOpenSearchDTO instanceof TenantsDTO tenantsDTO) {
                noticeMono = noticesService.addNoticesSuperAdmins("Tenant rimosso", String.format("Il tenant \"%s\" con codice \"%s\" è stato rimosso", tenantsDTO.getName(), tenantsDTO.getCode()), abstractAuthenticationToken);
            } else {
                return Mono.just(result);
            }

            return noticeMono.thenReturn(result);
        });
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

    private static FilePart getFilePart(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof FilePart)
            .map(arg -> (FilePart) arg)
            .findFirst().orElse(null);
    }
}
