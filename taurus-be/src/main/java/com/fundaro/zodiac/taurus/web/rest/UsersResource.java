package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.PatchMapping;
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
     * {@code PATCH /me} : Update the current user's own profile (name, lastName, email only).
     */
    @PatchMapping(value = "/me", consumes = {"application/json", "application/merge-patch+json"})
    public ResponseEntity<UsersDTO> partialUpdateOwnEntity(@Valid @RequestBody UsersMeDTO dto,
                                                           AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersDTO result = getService().partialUpdateOwn(dto, abstractAuthenticationToken);
        return ResponseEntity.ok(result);
    }
}
