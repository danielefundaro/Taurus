<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('tenant','role') displayInfo=false; section>
  <#if section = "header">
    ${msg("tenantRoleSelectionTitle")}
  <#elseif section = "form">
    <div id="kc-form">
      <div id="kc-form-wrapper">
        <form id="kc-tenant-role-select-form" class="${properties.kcFormClass!}" action="${url.loginAction}"
              method="post">

          <!-- Selezione Tenant -->
          <div class="${properties.kcFormGroupClass!}">
            <label for="tenant" class="${properties.kcLabelClass!}">
              ${msg("selectTenantLabel")}
            </label>

            <div class="${properties.kcInputWrapperClass!}">
              <select id="tenant"
                      name="tenant"
                      class="${properties.kcInputClass!}"
                      onchange="updateRoles()"
                      autofocus
                      required>
                <option value="">${msg("selectTenantPlaceholder")}</option>
                <#list tenants as tenant>
                  <option value="${tenant}" <#if selectedTenant?? && selectedTenant == tenant>selected</#if>>
                    ${tenant}
                  </option>
                </#list>
              </select>

              <#if messagesPerField.existsError('tenant')>
                <span id="input-error-tenant" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.get('tenant'))?no_esc}
                                </span>
              </#if>
            </div>
          </div>

          <!-- Selezione Ruolo -->
          <div class="${properties.kcFormGroupClass!}" id="role-group" style="display: none;">
            <label for="role" class="${properties.kcLabelClass!}">
              ${msg("selectRoleLabel")}
            </label>

            <div class="${properties.kcInputWrapperClass!}">
              <select id="role"
                      name="role"
                      class="${properties.kcInputClass!}">
                <option value="">${msg("selectRolePlaceholder")}</option>
              </select>

              <#if messagesPerField.existsError('role')>
                <span id="input-error-role" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                    ${kcSanitize(messagesPerField.get('role'))?no_esc}
                                </span>
              </#if>
            </div>
          </div>

          <div class="${properties.kcFormGroupClass!} ${properties.kcFormSettingClass!}">
            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
              <input
                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                type="submit"
                value="${msg("doSubmit")}" />
            </div>
          </div>
        </form>
      </div>
    </div>

    <script type="text/javascript">
      // Mappa tenant -> ruoli (passata dal backend)
      var tenantRolesMap = {
        <#list tenantRolesMap?keys as tenant>
        "${tenant}": [
          <#list tenantRolesMap[tenant] as role>
          "${role}"<#if role?has_next>, </#if>
          </#list>
        ]<#if tenant?has_next>, </#if>
        </#list>
      };

      function updateRoles() {
        var tenantSelect = document.getElementById('tenant');
        var roleSelect = document.getElementById('role');
        var roleGroup = document.getElementById('role-group');
        var selectedTenant = tenantSelect.value;

        // Reset role select
        roleSelect.innerHTML = '<option value="">${msg("selectRolePlaceholder")}</option>';

        if (selectedTenant && tenantRolesMap[selectedTenant]) {
          var roles = tenantRolesMap[selectedTenant];

          // Se c'è un solo ruolo, il form viene inviato automaticamente dal backend
          // quindi mostriamo comunque la selezione
          roles.forEach(function(role) {
            var option = document.createElement('option');
            option.value = role;
            option.text = role;
            roleSelect.appendChild(option);
          });

          // Mostra il campo ruolo solo se ci sono più ruoli
          if (roles.length > 1) {
            roleGroup.style.display = 'block';
            roleSelect.required = true;
          } else if (roles.length === 1) {
            // Auto-seleziona l'unico ruolo disponibile
            roleSelect.value = roles[0];
            roleGroup.style.display = 'none';
            roleSelect.required = false;
          }
        } else {
          roleGroup.style.display = 'none';
          roleSelect.required = false;
        }
      }

      // Inizializza il form al caricamento
      document.addEventListener('DOMContentLoaded', function() {
        <#if selectedTenant??>
        updateRoles();
        </#if>
      });
    </script>
  </#if>
</@layout.registrationLayout>
