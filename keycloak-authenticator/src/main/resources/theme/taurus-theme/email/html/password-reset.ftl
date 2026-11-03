<#import "template.ftl" as layout>
<#assign expiration = linkExpirationFormatter(linkExpiration)>
<@layout.emailLayout
  title="Reimposta la password"
  preheader="Scegli una nuova password per il tuo account Taurus."
  reason="Hai ricevuto questa e-mail perché è stato chiesto il recupero della password del tuo account.">
  <@layout.greeting name=(user.firstName)!"" />
  <@layout.paragraph>Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account Taurus. Usa il pulsante qui sotto per sceglierne una nuova.</@layout.paragraph>
  <@layout.button href=link label="Reimposta la password" />
  <@layout.notes>
    <@layout.note>Il link vale per <strong>${expiration}</strong>. Se non hai chiesto tu la reimpostazione, ignora questa e-mail: la password non cambia.</@layout.note>
    <@layout.fallbackLink href=link />
  </@layout.notes>
</@layout.emailLayout>
