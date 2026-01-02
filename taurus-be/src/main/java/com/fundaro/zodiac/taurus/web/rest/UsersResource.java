package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
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
}
