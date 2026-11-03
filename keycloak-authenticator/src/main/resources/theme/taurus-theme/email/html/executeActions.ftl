<#import "template.ftl" as layout>
<#assign actions = requiredActions![]>
<#if actions?seq_contains("UPDATE_PASSWORD")>
  <#assign actionTitle = "Benvenuto in Taurus">
  <#assign actionIntro = "Il tuo account è stato creato. Usa il pulsante qui sotto per scegliere la password e accedere a Taurus.">
  <#assign actionButton = "Imposta la password">
  <#assign actionReason = "Hai ricevuto questa e-mail perché è stato creato un account Taurus con questo indirizzo.">
<#elseif actions?seq_contains("VERIFY_EMAIL")>
  <#assign actionTitle = "Verifica il tuo indirizzo e-mail">
  <#assign actionIntro = "Usa il pulsante qui sotto per verificare il tuo indirizzo e-mail e attivare il tuo account.">
  <#assign actionButton = "Verifica l'indirizzo">
  <#assign actionReason = "Hai ricevuto questa e-mail perché è stata chiesta un'azione sul tuo account Taurus.">
<#elseif actions?seq_contains("CONFIGURE_TOTP")>
  <#assign actionTitle = "Configura l'app di autenticazione">
  <#assign actionIntro = "Usa il pulsante qui sotto per collegare un'app di autenticazione al tuo account e proteggere l'accesso.">
  <#assign actionButton = "Configura l'app">
  <#assign actionReason = "Hai ricevuto questa e-mail perché è stata chiesta un'azione sul tuo account Taurus.">
<#else>
  <#assign actionTitle = "Azione richiesta">
  <#assign actionIntro = "Usa il pulsante qui sotto per completare la configurazione del tuo account Taurus.">
  <#assign actionButton = "Accedi a Taurus">
  <#assign actionReason = "Hai ricevuto questa e-mail perché è stata chiesta un'azione sul tuo account Taurus.">
</#if>
<#assign expiration = linkExpirationFormatter(linkExpiration)>
<@layout.emailLayout title=actionTitle preheader=actionTitle + "." reason=actionReason>
  <@layout.greeting name=(user.firstName)!"" />
  <@layout.paragraph>${actionIntro}</@layout.paragraph>
  <@layout.button href=link label=actionButton />
  <@layout.notes>
    <@layout.note>Il link vale per <strong>${expiration}</strong>. Se non ti aspettavi questa e-mail, ignorala.</@layout.note>
    <@layout.fallbackLink href=link />
  </@layout.notes>
</@layout.emailLayout>
