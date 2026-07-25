<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false socialProvidersSupported=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="${locale!'en'}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/login.css">
</head>
<body>

<div class="taurus-bg">
    <div class="taurus-card">

        <div class="taurus-logo">
            <img src="${url.resourcesPath}/img/logo.svg" alt="Taurus" />
        </div>

        <div class="taurus-header">
            <#nested "header">
        </div>

        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="taurus-alert taurus-alert-${message.type}" role="alert">
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <div class="taurus-form-body">
            <#nested "form">
        </div>

        <#if displayInfo>
            <div class="taurus-info-body">
                <#nested "info">
            </div>
        </#if>

        <#if socialProvidersSupported>
            <div class="taurus-social-body">
                <#nested "socialProviders">
            </div>
        </#if>

    </div>
</div>

<script>
    function togglePasswordVisibility(fieldId, btn) {
        var field = document.getElementById(fieldId);
        if (field.type === 'password') {
            field.type = 'text';
            btn.setAttribute('data-visible', 'true');
        } else {
            field.type = 'password';
            btn.setAttribute('data-visible', 'false');
        }
    }
</script>

<#if scripts??>
    <#list scripts as script>
        <script src="${script}" type="text/javascript"></script>
    </#list>
</#if>

</body>
</html>
</#macro>
