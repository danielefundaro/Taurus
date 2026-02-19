package com.fundaro.zodiac.keycloakauthenticator.authentication;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.*;
import java.util.stream.Collectors;

public class TenantRoleSelectorAuthenticator implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        String preselectedTenant = null;

        if (user == null) {
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        // Recupera i tenant disponibili per l'utente
        Map<String, List<String>> tenantRolesMap = getTenantRolesMap(user);

        if (tenantRolesMap.isEmpty()) {
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        // Caso 1: Un solo tenant e un solo ruolo -> selezione automatica
        if (tenantRolesMap.size() == 1) {
            Map.Entry<String, List<String>> entry = tenantRolesMap.entrySet().iterator().next();
            String tenant = entry.getKey();
            List<String> roles = entry.getValue();

            preselectedTenant = tenant;

            if (roles.size() == 1) {
                // Selezione automatica
                setTenantAndRole(context, tenant, roles.get(0));
                context.success();
                return;
            }
        }

        // Altri casi: mostra il form
        showSelectionForm(context, tenantRolesMap, null, preselectedTenant);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String selectedTenant = formData.getFirst("tenant");
        String selectedRole = formData.getFirst("role");

        UserModel user = context.getUser();
        Map<String, List<String>> tenantRolesMap = getTenantRolesMap(user);

        // Validazione tenant
        if (selectedTenant == null || selectedTenant.isEmpty()) {
            showSelectionForm(context, tenantRolesMap, "tenantRequired", null);
            return;
        }

        if (!tenantRolesMap.containsKey(selectedTenant)) {
            showSelectionForm(context, tenantRolesMap, "invalidTenant", null);
            return;
        }

        List<String> availableRoles = tenantRolesMap.get(selectedTenant);

        // Caso 2: Tenant selezionato con un solo ruolo -> selezione automatica del ruolo
        if (availableRoles.size() == 1) {
            setTenantAndRole(context, selectedTenant, availableRoles.get(0));
            context.success();
            return;
        }

        // Validazione ruolo
        if (selectedRole == null || selectedRole.isEmpty()) {
            showSelectionForm(context, tenantRolesMap, "roleRequired", selectedTenant);
            return;
        }

        if (!availableRoles.contains(selectedRole)) {
            showSelectionForm(context, tenantRolesMap, "invalidRole", selectedTenant);
            return;
        }

        // Tutto OK: salva tenant e ruolo
        setTenantAndRole(context, selectedTenant, selectedRole);
        context.success();
    }

    private void showSelectionForm(AuthenticationFlowContext context,
                                   Map<String, List<String>> tenantRolesMap,
                                   String errorMessage,
                                   String preselectedTenant) {
        LoginFormsProvider form = context.form();

        // Passa la mappa completa al template
        form.setAttribute("tenantRolesMap", tenantRolesMap);

        // Passa i tenant come lista separata per facilitare l'iterazione
        form.setAttribute("tenants", new ArrayList<>(tenantRolesMap.keySet()));

        if (preselectedTenant != null) {
            form.setAttribute("selectedTenant", preselectedTenant);
            form.setAttribute("roles", tenantRolesMap.get(preselectedTenant));
        }

        if (errorMessage != null) {
            form.setError(errorMessage);
        }

        Response response = form.createForm("tenant-role-selector.ftl");
        context.challenge(response);
    }

    private void setTenantAndRole(AuthenticationFlowContext context, String tenant, String role) {
        context.getAuthenticationSession().setUserSessionNote("selected_tenant", tenant);
        context.getAuthenticationSession().setUserSessionNote("selected_role", role);

        // IMPORTANTE: Salva anche come attributo della AuthenticationSession per debug
        context.getAuthenticationSession().setAuthNote("selected_tenant", tenant);
        context.getAuthenticationSession().setAuthNote("selected_role", role);
    }

    private Map<String, List<String>> getTenantRolesMap(UserModel user) {
        Map<String, List<String>> tenantRolesMap = new LinkedHashMap<>();

        // Recupera la lista dei tenant
//        List<String> tenants = user.getAttributeStream("tenants")
//            .flatMap(attr -> Arrays.stream(attr.split(",")))
//            .map(String::trim)
//            .filter(s -> !s.isEmpty())
//            .toList();

        List<String> tenants = user.getGroupsStream()
                .map(GroupModel::getName)
                .filter(s -> !s.isEmpty())
                .map(String::trim)
                .toList();

        // Per ogni tenant, recupera i ruoli associati
        for (String tenant : tenants) {
            List<String> roles = user.getAttributeStream(tenant + "_roles")
                .flatMap(attr -> Arrays.stream(attr.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

            if (!roles.isEmpty()) {
                tenantRolesMap.put(tenant, roles);
            }
        }

        return tenantRolesMap;
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
