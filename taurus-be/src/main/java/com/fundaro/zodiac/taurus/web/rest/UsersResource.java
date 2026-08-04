package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
