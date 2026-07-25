<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayMessage=!messagesPerField.existsError('password','password-confirm');
    section>

    <#if section = "header">
        <h1 class="taurus-title">${msg("updatePasswordTitle")}</h1>
        <p class="taurus-subtitle">&nbsp;</p>

    <#elseif section = "form">
        <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">

            <input type="text" id="username" name="username"
                   value="${username!'krb-'}"
                   autocomplete="username"
                   readonly
                   style="display:none" />

            <!-- Nuova password -->
            <div class="taurus-field">
                <label for="password-new" class="taurus-label">
                    ${msg("passwordNew")}
                </label>
                <div class="taurus-password-wrapper">
                    <input
                        id="password-new"
                        name="password-new"
                        type="password"
                        class="taurus-input<#if messagesPerField.existsError('password')> taurus-input-error</#if>"
                        autocomplete="new-password"
                        placeholder="${msg("passwordNew")}"
                        autofocus
                    />
                    <button type="button"
                            class="taurus-eye-btn"
                            onclick="togglePasswordVisibility('password-new', this)"
                            tabindex="-1"
                            aria-label="${msg("showPassword")}">
                        <svg class="eye-open" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                        <svg class="eye-slash" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                            <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                        </svg>
                    </button>
                </div>
                <#if messagesPerField.existsError('password')>
                    <span class="taurus-field-error">
                        ${kcSanitize(messagesPerField.get('password'))?no_esc}
                    </span>
                </#if>
            </div>

            <!-- Conferma password -->
            <div class="taurus-field">
                <label for="password-confirm" class="taurus-label">
                    ${msg("passwordConfirm")}
                </label>
                <div class="taurus-password-wrapper">
                    <input
                        id="password-confirm"
                        name="password-confirm"
                        type="password"
                        class="taurus-input<#if messagesPerField.existsError('password-confirm')> taurus-input-error</#if>"
                        autocomplete="new-password"
                        placeholder="${msg("passwordConfirm")}"
                    />
                    <button type="button"
                            class="taurus-eye-btn"
                            onclick="togglePasswordVisibility('password-confirm', this)"
                            tabindex="-1"
                            aria-label="${msg("showPassword")}">
                        <svg class="eye-open" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                        <svg class="eye-slash" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                            <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                        </svg>
                    </button>
                </div>
                <#if messagesPerField.existsError('password-confirm')>
                    <span class="taurus-field-error">
                        ${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}
                    </span>
                </#if>
            </div>

            <!-- Logout other sessions -->
            <#if isAppInitiatedAction??>
            <label class="taurus-checkbox-label taurus-logout-check">
                <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" checked>
                <span>${msg("logoutOtherSessions")}</span>
            </label>
            </#if>

            <div class="taurus-totp-buttons">
                <button class="taurus-btn-primary" type="submit">
                    ${msg("doSubmit")}
                </button>
                <#if isAppInitiatedAction??>
                <button class="taurus-btn-secondary" type="submit"
                        name="cancel-aia" value="true">
                    ${msg("doCancel")}
                </button>
                </#if>
            </div>
        </form>
    </#if>

</@layout.registrationLayout>
