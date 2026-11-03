<#ftl output_format="plainText">
<#assign actions = requiredActions![]>
<#if actions?seq_contains("UPDATE_PASSWORD")>
<#assign actionTitle = "Benvenuto in Taurus">
<#assign actionIntro = "Il tuo account è stato creato. Apri questo link per scegliere la password e accedere a Taurus:">
<#assign actionReason = "Hai ricevuto questa e-mail perché è stato creato un account Taurus con questo indirizzo.">
<#elseif actions?seq_contains("VERIFY_EMAIL")>
<#assign actionTitle = "Verifica il tuo indirizzo e-mail">
<#assign actionIntro = "Apri questo link per verificare il tuo indirizzo e-mail e attivare il tuo account:">
<#assign actionReason = "Hai ricevuto questa e-mail perché è stata chiesta un'azione sul tuo account Taurus.">
<#elseif actions?seq_contains("CONFIGURE_TOTP")>
<#assign actionTitle = "Configura l'app di autenticazione">
<#assign actionIntro = "Apri questo link per collegare un'app di autenticazione al tuo account e proteggere l'accesso:">
<#assign actionReason = "Hai ricevuto questa e-mail perché è stata chiesta un'azione sul tuo account Taurus.">
<#else>
<#assign actionTitle = "Azione richiesta">
<#assign actionIntro = "Apri questo link per completare la configurazione del tuo account Taurus:">
<#assign actionReason = "Hai ricevuto questa e-mail perché è stata chiesta un'azione sul tuo account Taurus.">
</#if>
${actionTitle}

Ciao<#if (user.firstName)?has_content> ${user.firstName}</#if>,

${actionIntro}
${link}

Il link vale per ${linkExpirationFormatter(linkExpiration)}. Se non ti aspettavi questa e-mail, ignorala.

--
Taurus
${actionReason}
