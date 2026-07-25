<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??
    socialProvidersSupported=realm.password && social.providers?? && social.providers?has_content;
    section>

    <#if section = "header">
        <h1 class="taurus-title">${msg("doWelcomeTo",(realm.displayName!''))}</h1>
        <p class="taurus-subtitle">${msg("loginSubtitle")}</p>

    <#elseif section = "form">
        <form id="kc-form-login"
              onsubmit="login.disabled = true; return true;"
              action="${url.loginAction}"
              method="post">

            <!-- Username / Email -->
            <div class="taurus-field">
                <label for="username" class="taurus-label">
                    <#if !realm.loginWithEmailAllowed>
                        ${msg("username")}
                    <#elseif !realm.registrationEmailAsUsername>
                        ${msg("usernameOrEmail")}
                    <#else>
                        ${msg("email")}
                    </#if>
                </label>
                <input
                    id="username"
                    name="username"
                    type="text"
                    class="taurus-input<#if messagesPerField.existsError('username','password')> taurus-input-error</#if>"
                    value="${(login.username!'')}"
                    autocomplete="username"
                    placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("emailPlaceholder")}</#if>"
                    autofocus
                />
            </div>

            <!-- Password -->
            <div class="taurus-field">
                <label for="password" class="taurus-label">${msg("password")}</label>
                <div class="taurus-password-wrapper">
                    <input
                        id="password"
                        name="password"
                        type="password"
                        class="taurus-input<#if messagesPerField.existsError('username','password')> taurus-input-error</#if>"
                        autocomplete="current-password"
                        placeholder="${msg("password")}"
                    />
                    <button type="button"
                            class="taurus-eye-btn"
                            onclick="togglePasswordVisibility('password', this)"
                            tabindex="-1"
                            aria-label="${msg("showPassword")}">
                        <!-- eye open -->
                        <svg class="eye-open" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                        <!-- eye closed (slash) -->
                        <svg class="eye-slash" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2"
                             stroke-linecap="round" stroke-linejoin="round">
                            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8
                                     a18.45 18.45 0 0 1 5.06-5.94"/>
                            <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8
                                     a18.5 18.5 0 0 1-2.16 3.19"/>
                            <line x1="1" y1="1" x2="23" y2="23"/>
                        </svg>
                    </button>
                </div>
            </div>

            <!-- Remember me + Forgot password -->
            <#if realm.rememberMe || realm.resetPasswordAllowed>
            <div class="taurus-options-row">
                <#if realm.rememberMe>
                <label class="taurus-checkbox-label">
                    <input type="checkbox" name="rememberMe"<#if login.rememberMe?? && login.rememberMe> checked</#if>>
                    <span>${msg("rememberMe")}</span>
                </label>
                <#else>
                <span></span>
                </#if>
                <#if realm.resetPasswordAllowed>
                <a href="${url.loginResetCredentialsUrl}" class="taurus-forgot-link">
                    ${msg("doForgotPassword")}
                </a>
                </#if>
            </div>
            </#if>

            <input type="hidden" id="id-hidden-input" name="credentialId"
                   <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>>

            <button id="kc-login" class="taurus-btn-primary" type="submit" name="login">
                ${msg("doLogIn")}
            </button>
        </form>

    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="taurus-register-row">
                <span>${msg("noAccount")}</span>
                <a href="${url.registrationUrl}">${msg("doRegister")}</a>
            </div>
        </#if>

    <#elseif section = "socialProviders">
        <#if realm.password && social.providers?? && social.providers?has_content>
            <div class="taurus-divider">
                <span>${msg("identity-provider-login-label")}</span>
            </div>
            <ul class="taurus-social-list">
                <#list social.providers as p>
                <li>
                    <a id="social-${p.alias}" href="${p.loginUrl}" class="taurus-social-btn">
                        <#if p.iconClasses?has_content>
                            <i class="${p.iconClasses!}" aria-hidden="true"></i>
                        </#if>
                        <span>${p.displayName!}</span>
                    </a>
                </li>
                </#list>
            </ul>
        </#if>
    </#if>

</@layout.registrationLayout>
