package com.fundaro.zodiac.taurus.aop.notices;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.*;
import com.fundaro.zodiac.taurus.service.dto.*;
import com.fundaro.zodiac.taurus.service.impl.*;
import org.apache.commons.io.FilenameUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
    private final CalendarEventsService calendarEventsService;

    public NoticesAspect(NoticesService noticesService, UsersService usersService, TenantsService tenantsService, InstrumentsService instrumentsService, AlbumsService albumsService, TracksService tracksService, CalendarEventsService calendarEventsService) {
        this.noticesService = noticesService;
        this.usersService = usersService;
        this.tenantsService = tenantsService;
        this.instrumentsService = instrumentsService;
        this.albumsService = albumsService;
        this.tracksService = tracksService;
        this.calendarEventsService = calendarEventsService;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.save(..))")
    private Object onSave(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        Object result = joinPoint.proceed();

        if (!(result instanceof CommonFieldsOpenSearchDTO dto) || token == null) {
            return result;
        }

        if (dto instanceof AlbumsDTO albumsDTO) {
            String name = "Nuovo album creato", message = String.format("L'album \"%s\" è stato creato", dto.getName());

            if (albumsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof TracksDTO tracksDTO) {
            String name = "Nuova traccia creata", message = String.format("La traccia \"%s\" è stata creata", dto.getName());

            if (tracksDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof CalendarEventsDTO calendarEventsDTO) {
            String name = "Nuovo evento creato", message = String.format("L'evento \"%s\" è stato creato", dto.getName());

            if (calendarEventsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof InstrumentsDTO) {
            noticesService.addNoticesExcludeRoleUsers("Nuovo strumento", String.format("Lo strumento \"%s\" è stato aggiunto", dto.getName()), token);
        } else if (dto instanceof UsersDTO usersDTO) {
            noticesService.addNoticesAdmins("Nuovo utente", String.format("L'utente \"%s %s\" è stato aggiunto", usersDTO.getName(), usersDTO.getLastName()), token);
        } else if (dto instanceof TenantsDTO tenantsDTO) {
            noticesService.addNoticesSuperAdmins("Nuovo tenant", String.format("Il tenant \"%s\" e codice \"%s\" è stato aggiunto", tenantsDTO.getName(), tenantsDTO.getCode()), token);
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.TracksServiceImpl.uploadFile(..))")
    private Object onUploadFile(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);
        MultipartFile file = getMultipartFile(joinPoint);
        Object result = joinPoint.proceed();

        if (token == null) {
            return result;
        }

        if (id != null) {
            tracksService.findOne(id, token).ifPresent(tracksDTO ->
                noticesService.addNoticesExcludeRoleUsers("Traccia aggiornata", String.format("Le informazioni della traccia \"%s\" sono state aggiornate", tracksDTO.getName()), token)
            );
        } else if (file != null) {
            noticesService.addNoticesExcludeRoleUsers("Nuova traccia creata", String.format("La traccia \"%s\" è stata creata", FilenameUtils.removeExtension(file.getOriginalFilename())), token);
        }

        return result;
    }

    @Around("execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.update(..)) || " +
        "execution(public * com.fundaro.zodiac.taurus.service.impl.CommonOpenSearchServiceImpl.partialUpdate(..))")
    private Object onUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        AbstractAuthenticationToken token = getAbstractAuthenticationToken(joinPoint);
        String id = getId(joinPoint);

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

        if (dto instanceof AlbumsDTO albumsDTO) {
            AlbumsDTO oldAlbum = (AlbumsDTO) oldFinal;
            String name = "Album aggiornato", message = String.format("Le informazioni dell'album \"%s\" sono state aggiornate", dto.getName());

            if (oldAlbum.getState() != StateEnum.PUBLIC) {
                if (albumsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeOnlyRoleUsers("Nuovo album creato", String.format("L'album \"%s\" è stato creato", dto.getName()), token);
                    noticesService.addNoticesExcludeRoleUsers("Album pubblicato", String.format("L'album \"%s\" è stato pubblicato", dto.getName()), token);
                }
            } else {
                if (albumsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers("Album rimosso", String.format("L'album \"%s\" è stato rimosso", dto.getName()), token);
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeWholeTenant(name, message, token);
                }
            }
        } else if (dto instanceof TracksDTO tracksDTO) {
            TracksDTO oldTrack = (TracksDTO) oldFinal;
            String name = "Traccia aggiornata", message = String.format("Le informazioni della traccia \"%s\" sono state aggiornate", dto.getName());

            if (oldTrack.getState() != StateEnum.PUBLIC) {
                if (tracksDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeOnlyRoleUsers("Nuova traccia creata", String.format("La traccia \"%s\" è stato creata", dto.getName()), token);
                    noticesService.addNoticesExcludeRoleUsers("Traccia pubblicata", String.format("L'album \"%s\" è stata pubblicata", dto.getName()), token);
                }
            } else {
                if (tracksDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers("Traccia rimossa", String.format("La traccia \"%s\" è stata rimossa", dto.getName()), token);
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeWholeTenant(name, message, token);
                }
            }
        } else if (dto instanceof CalendarEventsDTO calendarEventsDTO) {
            CalendarEventsDTO oldCalendar = (CalendarEventsDTO) oldFinal;
            String name = "Evento aggiornato", message = String.format("Le informazioni dell'evento \"%s\" sono state aggiornate", dto.getName());

            if (oldCalendar.getState() != StateEnum.PUBLIC) {
                if (calendarEventsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeOnlyRoleUsers("Nuovo evento creato", String.format("L'evento \"%s\" è stato creato", dto.getName()), token);
                    noticesService.addNoticesExcludeRoleUsers("Evento pubblicato", String.format("L'evento \"%s\" è stato pubblicato", dto.getName()), token);
                }
            } else {
                if (calendarEventsDTO.getState() != StateEnum.PUBLIC) {
                    noticesService.addNoticeOnlyRoleUsers("Evento rimosso", String.format("L'evento \"%s\" è stato rimosso", dto.getName()), token);
                    noticesService.addNoticesExcludeRoleUsers(name, message, token);
                } else {
                    noticesService.addNoticeWholeTenant(name, message, token);
                }
            }
        } else if (dto instanceof InstrumentsDTO) {
            noticesService.addNoticesExcludeRoleUsers("Strumento aggiornato", String.format("Lo strumento \"%s\" è stato aggiornato", dto.getName()), token);
        } else if (dto instanceof UsersDTO usersDTO) {
            noticesService.addNoticesAdmins("Utente aggiornato", String.format("L'utente \"%s %s\" è stato aggiornato", usersDTO.getName(), usersDTO.getLastName()), token);
        } else if (dto instanceof TenantsDTO tenantsDTO) {
            noticesService.addNoticesSuperAdmins("Tenant aggiornato", String.format("Il tenant \"%s\" con codice \"%s\" è stato aggiornato", tenantsDTO.getName(), tenantsDTO.getCode()), token);
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

        if (dto instanceof AlbumsDTO albumsDTO) {
            String name = "Album rimosso", message = String.format("L'album \"%s\" è stato rimosso", dto.getName());

            if (albumsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof TracksDTO tracksDTO) {
            String name = "Traccia rimossa", message = String.format("La traccia \"%s\" è stata rimossa", dto.getName());

            if (tracksDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof CalendarEventsDTO calendarEventsDTO) {
            String name = "Evento rimosso", message = String.format("L'evento \"%s\" è stato rimosso", dto.getName());

            if (calendarEventsDTO.getState() == StateEnum.PUBLIC) {
                noticesService.addNoticeWholeTenant(name, message, token);
            } else {
                noticesService.addNoticesExcludeRoleUsers(name, message, token);
            }
        } else if (dto instanceof InstrumentsDTO) {
            noticesService.addNoticesExcludeRoleUsers("Strumento rimosso", String.format("Lo strumento \"%s\" è stato rimosso", dto.getName()), token);
        } else if (dto instanceof UsersDTO usersDTO) {
            noticesService.addNoticesAdmins("Utente rimosso", String.format("L'utente \"%s %s\" è stato rimosso", usersDTO.getName(), usersDTO.getLastName()), token);
        } else if (dto instanceof TenantsDTO tenantsDTO) {
            noticesService.addNoticesSuperAdmins("Tenant rimosso", String.format("Il tenant \"%s\" con codice \"%s\" è stato rimosso", tenantsDTO.getName(), tenantsDTO.getCode()), token);
        }

        return result;
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

    private static MultipartFile getMultipartFile(ProceedingJoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof MultipartFile)
            .map(arg -> (MultipartFile) arg)
            .findFirst().orElse(null);
    }
}
