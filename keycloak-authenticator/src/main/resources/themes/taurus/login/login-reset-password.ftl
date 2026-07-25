<#import "template.ftl" as layout>
<@layout.registrationLayout
    displayInfo=true
    displayMessage=!messagesPerField.existsError('username');
    section>

    <#if section = "header">
        <h1 class="taurus-title">${msg("emailForgotTitle")}</h1>
        <p class="taurus-subtitle">&nbsp;</p>

    <#elseif section = "form">
        <form id="kc-reset-password-form" action="${url.loginAction}" method="post">

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
                    type="text"
                    id="username"
                    name="username"
                    class="taurus-input<#if messagesPerField.existsError('username')> taurus-input-error</#if>"
                    autofocus
                    value="${(auth.attemptedUsername!'')}"
                    placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("emailPlaceholder")}</#if>"
                    aria-invalid="<#if messagesPerField.existsError('username')>true</#if>"
                />
                <#if messagesPerField.existsError('username')>
                    <span class="taurus-field-error">
                        ${kcSanitize(messagesPerField.get('username'))?no_esc}
                    </span>
                </#if>
            </div>

            <button class="taurus-btn-primary" type="submit">
                ${msg("doSubmit")}
            </button>

            <div class="taurus-back-row">
                <a href="${url.loginUrl}" class="taurus-back-link">
                    ← ${kcSanitize(msg("backToLogin"))?no_esc}
                </a>
            </div>
        </form>

    <#elseif section = "info">
        <div class="taurus-reset-info">
            ${msg("emailInstruction")}
        </div>
    </#if>

</@layout.registrationLayout>
