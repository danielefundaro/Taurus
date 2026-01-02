package com.fundaro.zodiac.taurus.utils.keycloak.service;

import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;

import java.util.List;

public interface KeycloakService {
    List<User> getUsers();

    User getUser(String id);

    String getUserIdByUsernameOrEmail(String username, String email);

    User saveUser(User user);

    User updateUser(User user);
}
