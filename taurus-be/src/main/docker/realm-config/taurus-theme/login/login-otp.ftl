<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>

  <#if section = "header">
    <h1 class="taurus-title">${msg("loginOtpTitle")}</h1>
    <p class="taurus-subtitle">${msg("loginOtpSubtitle")}</p>
    <#if auth?has_content && auth.showUsername()>
      <p class="taurus-otp-account">
        ${msg("loginOtpAccount")} <strong>${auth.attemptedUsername}</strong>
        <a href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}">${msg("loginOtpChangeAccount")}</a>
      </p>
    </#if>

  <#elseif section = "form">
    <form id="kc-otp-login-form"
          onsubmit="login.disabled = true; return true;"
          action="${url.loginAction}"
          method="post">

      <!-- Device: chosen only when the user has more than one authenticator app -->
      <#if otpLogin.userOtpCredentials?size gt 1>
        <fieldset class="taurus-otp-devices">
          <legend class="taurus-label">${msg("loginOtpDevice")}</legend>
          <#list otpLogin.userOtpCredentials as otpCredential>
            <label class="taurus-otp-device">
              <input type="radio" name="selectedCredentialId" value="${otpCredential.id}"
                     <#if otpCredential.id == (otpLogin.selectedCredentialId!'')>checked</#if> />
              <svg class="taurus-otp-device-icon" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="6" y="2" width="12" height="20" rx="2" />
                <line x1="11" y1="18" x2="13" y2="18" />
              </svg>
              <span>
                <#if otpCredential.userLabel?has_content>
                  ${otpCredential.userLabel}
                <#else>
                  ${msg("loginOtpUnnamedDevice", otpCredential?counter?c)}
                </#if>
              </span>
            </label>
          </#list>
        </fieldset>
      <#else>
        <input type="hidden" name="selectedCredentialId" value="${otpLogin.selectedCredentialId!''}" />
      </#if>

      <!-- One-time code -->
      <div class="taurus-field">
        <label for="otp" class="taurus-label">${msg("loginOtpOneTime")}</label>
        <input
          id="otp"
          name="otp"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
          class="taurus-input taurus-otp-input<#if messagesPerField.existsError('totp')> taurus-input-error</#if>"
          placeholder="000000"
          <#if messagesPerField.existsError('totp')>aria-invalid="true" aria-describedby="otp-error"</#if>
          autofocus
        />
        <#if messagesPerField.existsError('totp')>
          <span id="otp-error" class="taurus-field-error" aria-live="polite">
            ${kcSanitize(messagesPerField.get('totp'))?no_esc}
          </span>
        </#if>
      </div>

      <button id="kc-login" class="taurus-btn-primary" type="submit" name="login">
        ${msg("doLogIn")}
      </button>
    </form>
  </#if>

</@layout.registrationLayout>
