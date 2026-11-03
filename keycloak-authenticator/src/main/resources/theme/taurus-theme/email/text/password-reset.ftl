<#ftl output_format="plainText">
Reimposta la password

Ciao<#if (user.firstName)?has_content> ${user.firstName}</#if>,

Abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account Taurus. Apri questo link per sceglierne una nuova:
${link}

Il link vale per ${linkExpirationFormatter(linkExpiration)}. Se non hai chiesto tu la reimpostazione, ignora questa e-mail: la password non cambia.

--
Taurus
Hai ricevuto questa e-mail perché è stato chiesto il recupero della password del tuo account.
