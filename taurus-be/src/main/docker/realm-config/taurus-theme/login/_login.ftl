<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "form">
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <#if !usernameHidden??>
                <div class="pl-field">
                    <label for="username" class="pl-label">
                        <#if !realm.loginWithEmailAllowed>${msg("username")}
                        <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                        <#else>${msg("email")}
                        </#if>
                    </label>
                    <input tabindex="1" id="username" class="pl-input" name="username" value="${login.username!''}"
                           type="text" autofocus autocomplete="username"
                           placeholder="<#if !realm.loginWithEmailAllowed>${msg('username')}<#elseif !realm.registrationEmailAsUsername>${msg('usernameOrEmail')}<#else>${msg('email')}</#if> address"
                           aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                    />
                    <#if messagesPerField.existsError('username','password')>
                        <span class="pl-input-error">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <div class="pl-field">
                <label for="password" class="pl-label">${msg("password")}</label>
                <div class="pl-password-wrap">
                    <input tabindex="2" id="password" class="pl-input" name="password" type="password"
                           autocomplete="current-password" placeholder="${msg('password')}"
                           aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                    />
                    <button type="button" class="pl-password-toggle" aria-label="Show password" onclick="
                        var i=document.getElementById('password');
                        i.type = i.type === 'password' ? 'text' : 'password';
                    ">
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8">
                            <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/>
                            <circle cx="12" cy="12" r="3"/>
                        </svg>
                    </button>
                </div>
                <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                    <span class="pl-input-error">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                </#if>
            </div>

            <div class="pl-row">
                <label class="pl-checkbox">
                    <#if realm.rememberMe && !usernameHidden??>
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                        <span>${msg("rememberMe")}</span>
                    <#else>
                        <span></span>
                    </#if>
                </label>

                <#if realm.resetPasswordAllowed>
                    <a tabindex="5" class="pl-link" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                </#if>
            </div>

            <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth?has_content && auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
            <button tabindex="4" class="pl-button" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
        </form>

        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="pl-footer-text">
                ${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a>
            </div>
        </#if>

        <#if realm.password && social.providers?? && social.providers?has_content>
            <div class="pl-social">
                <div class="pl-social-title">${msg("identity-provider-login-label")}</div>
                <#list social.providers as p>
                    <a class="pl-social-btn" href="${p.loginUrl}" id="zocial-${p.alias}">${p.displayName}</a>
                </#list>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
