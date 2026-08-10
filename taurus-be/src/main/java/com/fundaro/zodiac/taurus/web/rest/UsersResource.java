package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UserCalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing {@link Users}.
 */
@RestController
@RequestMapping("/api/users")
public class UsersResource extends CommonOpenSearchResource<Users, UsersDTO, UsersCriteria, UsersService> {

    public UsersResource(UsersService usersService) {
        super(usersService, Users.class.getSimpleName(), UsersResource.class);
    }

    /**
     * {@code PUT /:id/send-setup-email} : Send account setup email to the user.
     */
    @PutMapping("/{id}/send-setup-email")
    public ResponseEntity<Void> sendSetupEmail(@PathVariable(value = "id") final String id,
                                               AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to send setup email to user {}", id);
        getService().sendSetupEmail(id, abstractAuthenticationToken);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET /{id}/calendar-events} : Get all the calendar events the user was present at
     *
     * @param id the id of the user
     * @param pageable the pagination information.
     * @param request  a {@link HttpServletRequest} request.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of entity in body.
     */
    @GetMapping("/{id}/calendar-events")
    public ResponseEntity<Page<CalendarEventsDTO>> getUserCalendarEvents(@PathVariable(value = "id") String id, UserCalendarEventsCriteria criteria, @ParameterObject Pageable pageable, HttpServletRequest request, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to get calendar events for user {}", id);
        Page<CalendarEventsDTO> page = getService().getUserCalendarEvents(id, criteria, pageable, abstractAuthenticationToken);
        return ResponseEntity.ok()
            .headers(PaginationUtil.generatePaginationHttpHeaders(UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString()), page))
            .body(page);
    }

    /**
     * {@code GET /me} : Get the current user's own profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UsersDTO> getMe(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to get current user profile");
        return getService().findMe(abstractAuthenticationToken)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * {@code PATCH /me} : Update the current user's own profile (name, lastName, email only).
     */
    @PatchMapping(value = "/me", consumes = {"application/json", "application/merge-patch+json"})
    public ResponseEntity<UsersDTO> partialUpdateOwnEntity(@Valid @RequestBody UsersMeDTO dto,
                                                           AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO result = getService().partialUpdateOwn(dto, abstractAuthenticationToken);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code DELETE /me} : Delete the current user's own account.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnEntity(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to delete current user account");
        getService().deleteOwn(abstractAuthenticationToken);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET /me/calendar-events} : Get all the calendar events of the current user was present at
     *
     * @param pageable the pagination information.
     * @param request  a {@link HttpServletRequest} request.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of entity in body.
     */
    @GetMapping("/me/calendar-events")
    public ResponseEntity<Page<CalendarEventsDTO>> getCurrentUserCalendarEvents(UserCalendarEventsCriteria criteria, @ParameterObject Pageable pageable, HttpServletRequest request, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to get calendar events for the current user");
        Page<CalendarEventsDTO> page = getService().getCurrentUserCalendarEvents(criteria, pageable, abstractAuthenticationToken);
        return ResponseEntity.ok()
            .headers(PaginationUtil.generatePaginationHttpHeaders(UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString()), page))
            .body(page);
    }
}
