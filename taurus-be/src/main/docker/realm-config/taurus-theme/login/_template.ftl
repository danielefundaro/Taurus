<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${locale.currentLanguageTag!'en'}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="data:;base64,iVBORw0KGgo=">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
</head>
<body class="${bodyClass}">
    <div class="pl-page">
        <div class="pl-card">
            <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
                <div class="pl-locale">
                    <select onchange="if (this.value) window.location.href=this.value;">
                        <#list locale.supported as l>
                            <option value="${l.url}" <#if l.languageTag == locale.current>selected</#if>>${l.label}</option>
                        </#list>
                    </select>
                </div>
            </#if>

            <div class="pl-logo">
                <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                    <ellipse cx="32" cy="24" rx="22" ry="9" fill="none" stroke="currentColor" stroke-width="3.2"/>
                    <ellipse cx="32" cy="24" rx="14" ry="5.5" fill="none" stroke="currentColor" stroke-width="3.2"/>
                    <path d="M14 26c0 11 8 20 18 20s18-9 18-20" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round"/>
                    <circle cx="26" cy="32" r="2.4" fill="currentColor"/>
                    <circle cx="38" cy="32" r="2.4" fill="currentColor"/>
                </svg>
            </div>

            <h1 class="pl-title">${msg("plWelcomeTitle","Welcome to Taurus!")}</h1>
            <#if displayMessage && message?has_content>
                <p class="pl-subtitle pl-message pl-message-${message.type}">${kcSanitize(message.summary)?no_esc}</p>
            <#else>
                <p class="pl-subtitle">${msg("plWelcomeSubtitle","Sign in to continue")}</p>
            </#if>

            <#nested "form">

            <#if displayInfo>
                <div class="pl-info">
                    <#nested "info">
                </div>
            </#if>
        </div>
    </div>
</body>
</html>
</#macro>
