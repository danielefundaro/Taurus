package com.fundaro.zodiac.taurus.utils.keycloak.service;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

public interface KeycloakService {
    List<User> getUsers();

    User getUser(String id);

    String getUserIdByUsernameOrEmail(String username, String email);

    void saveUser(User user);

    void updateUser(User user);

    void updateUserGroup(String userId, String groupId);

    void deleteUser(String user);

    List<Role> getUserRoles(String userId);

    void saveUserRoles(String userId, List<Role> roles);

    void deleteUserRoles(String userId, List<Role> roles);

    void sendResetPasswordEmail(String userId);

    void sendVerifyEmail(String userId);

    void saveGroup(Group group);

    String getGroupIdByName(String name);

    String getIdByClientId();

    List<Role> getClientRoles();

    List<User> getUsersByClientRoles(RoleEnum roleEnum);
}
