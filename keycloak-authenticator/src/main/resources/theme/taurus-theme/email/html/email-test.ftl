<#import "template.ftl" as layout>
<@layout.emailLayout
  title="Prova di invio"
  preheader="Il server SMTP del realm funziona."
  reason="Hai ricevuto questa e-mail perché un amministratore ha provato la configurazione SMTP di Taurus.">
  <@layout.greeting name=(user.firstName)!"" />
  <@layout.paragraph>Questo messaggio conferma che Taurus riesce a inviare e-mail con il server SMTP configurato nel realm.</@layout.paragraph>
  <@layout.notes>
    <@layout.note>Non serve fare altro: puoi eliminare questa e-mail.</@layout.note>
  </@layout.notes>
</@layout.emailLayout>
