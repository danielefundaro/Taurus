package com.fundaro.zodiac.taurus.utils.keycloak.service;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;

import java.util.List;

public interface KeycloakService {
    List<User> getUsers();

    User getUser(String id);

    String getUserIdByUsernameOrEmail(String username, String email);

    void saveUser(User user);

    void updateUser(User user);

    void updateUserGroup(String userId, String groupId);

    void deleteUserGroup(String userId, String groupId);

    List<Group> getUserGroups(String userId);

    void deleteUser(String user);

    /**
     * Ends every session of the user, in every tenant. Best effort: false, with a warning in the log, when Keycloak
     * does not confirm it.
     */
    boolean logoutUser(String userId);

    List<Role> getUserRoles(String userId);

    void saveUserRoles(String userId, List<Role> roles);

    void deleteUserRoles(String userId, List<Role> roles);

    void sendResetPasswordEmail(String userId);

    void sendVerifyEmail(String userId);

    void sendExecuteActionsEmail(String userId, List<String> actions);

    void saveGroup(Group group);

    void deleteGroup(String groupId);

    String getGroupIdByName(String name);

    String getIdByClientId();

    List<Role> getClientRoles();

    /**
     * Creates the client roles that are missing, reading the existing ones once; existing roles are left as they are.
     *
     * @param descriptions description of each role, by role name
     * @return the names of the roles created
     */
    List<String> ensureClientRoles(java.util.Map<String, String> descriptions);

    List<User> getUsersByClientRoles(RoleEnum roleEnum);
}
