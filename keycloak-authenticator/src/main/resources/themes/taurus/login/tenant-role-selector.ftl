<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayMessage=!messagesPerField.existsError('tenant','role')
    displayInfo=false;
    section>

    <#assign singleTenant = (tenants?size == 1)>
    <#if singleTenant>
        <#assign theSingleTenant = tenants[0]>
        <#assign singleTenantRoles = tenantRolesMap[theSingleTenant]>
    </#if>

    <#if section = "header">
        <h1 class="taurus-title">${singleTenant?then(msg("roleSelectionTitle"), msg("tenantRoleSelectionTitle"))}</h1>
        <p class="taurus-subtitle">&nbsp;</p>

    <#elseif section = "form">
        <#-- Collect unique role codes across all tenants for the JS label map -->
        <#assign allRoles = []>
        <#list tenantRolesMap?keys as t>
            <#list tenantRolesMap[t] as r>
                <#if !allRoles?seq_contains(r)>
                    <#assign allRoles = allRoles + [r]>
                </#if>
            </#list>
        </#list>

        <form id="kc-tenant-role-select-form" action="${url.loginAction}" method="post">

            <#if singleTenant>
                <!-- Single tenant: hidden field + show only role selector -->
                <input type="hidden" name="tenant" value="${theSingleTenant}" />

                <div class="taurus-field">
                    <label for="role" class="taurus-label">${msg("selectRoleLabel")}</label>
                    <select id="role" name="role" class="taurus-input" autofocus required>
                        <option value="">${msg("selectRolePlaceholder")}</option>
                        <#list singleTenantRoles as role>
                            <option value="${role}">${msg('role.' + role)}</option>
                        </#list>
                    </select>
                    <#if messagesPerField.existsError('role')>
                        <span class="taurus-field-error">
                            ${kcSanitize(messagesPerField.get('role'))?no_esc}
                        </span>
                    </#if>
                </div>

            <#else>
                <!-- Multi-tenant: show tenant selector + role selector (JS-driven) -->
                <div class="taurus-field">
                    <label for="tenant" class="taurus-label">${msg("selectTenantLabel")}</label>
                    <select id="tenant" name="tenant" class="taurus-input"
                            onchange="updateRoles()" autofocus required>
                        <option value="">${msg("selectTenantPlaceholder")}</option>
                        <#list tenants as tenant>
                            <option value="${tenant}"
                                <#if selectedTenant?? && selectedTenant == tenant>selected</#if>>
                                ${(tenantNamesMap[tenant])!tenant}
                            </option>
                        </#list>
                    </select>
                    <#if messagesPerField.existsError('tenant')>
                        <span class="taurus-field-error">
                            ${kcSanitize(messagesPerField.get('tenant'))?no_esc}
                        </span>
                    </#if>
                </div>

                <!-- Role (initially hidden, shown by JS after tenant selection) -->
                <div class="taurus-field" id="role-group" style="display: none;">
                    <label for="role" class="taurus-label">${msg("selectRoleLabel")}</label>
                    <select id="role" name="role" class="taurus-input">
                        <option value="">${msg("selectRolePlaceholder")}</option>
                    </select>
                    <#if messagesPerField.existsError('role')>
                        <span class="taurus-field-error">
                            ${kcSanitize(messagesPerField.get('role'))?no_esc}
                        </span>
                    </#if>
                </div>
            </#if>

            <button class="taurus-btn-primary" type="submit">${msg("doSubmit")}</button>
        </form>

        <#if !singleTenant>
        <script type="text/javascript">
            var tenantRolesMap = {
                <#list tenantRolesMap?keys as tenant>
                "${tenant}": [
                    <#list tenantRolesMap[tenant] as role>
                    "${role}"<#if role?has_next>, </#if>
                    </#list>
                ]<#if tenant?has_next>, </#if>
                </#list>
            };

            var roleLabelMap = {
                <#list allRoles as role>
                "${role}": "${msg('role.' + role)}"<#sep>, </#sep>
                </#list>
            };

            function updateRoles() {
                var tenantSelect = document.getElementById('tenant');
                var roleSelect   = document.getElementById('role');
                var roleGroup    = document.getElementById('role-group');
                var selected     = tenantSelect.value;

                roleSelect.innerHTML = '<option value="">${msg("selectRolePlaceholder")}</option>';

                if (selected && tenantRolesMap[selected]) {
                    var roles = tenantRolesMap[selected];
                    roles.forEach(function(role) {
                        var opt = document.createElement('option');
                        opt.value = role;
                        opt.text  = roleLabelMap[role] || role;
                        roleSelect.appendChild(opt);
                    });
                    if (roles.length > 1) {
                        roleGroup.style.display = 'block';
                        roleSelect.required = true;
                    } else if (roles.length === 1) {
                        roleSelect.value   = roles[0];
                        roleGroup.style.display = 'none';
                        roleSelect.required = false;
                    }
                } else {
                    roleGroup.style.display = 'none';
                    roleSelect.required = false;
                }
            }

            document.addEventListener('DOMContentLoaded', function() {
                <#if selectedTenant??>updateRoles();</#if>
            });
        </script>
        </#if>
    </#if>

</@layout.registrationLayout>
