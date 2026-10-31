# Taurus Keycloak Authenticator

Provider Java 17 per Keycloak:

- selezione tenant/ruolo durante l'autenticazione e mapper `tenant-role-mapper` che aggiunge al token il tenant e il ruolo scelti. Il mapper aggiunge anche il claim `role_choice` con valore `true` quando il ruolo è stato scelto tra più ruoli del tenant, e lo omette altrimenti: la topbar lo usa per mostrare il ruolo ([specifica](../docs/tenant-feature-flags-spec.md#organizzazione-e-ruolo-nella-topbar)). I nomi dei claim sono configurabili: per default `tenant`, `tenant_role` e `role_choice`; il realm di Taurus (`taurus-realm.json`) imposta `tenant`, `role` e `role_choice`;
- `taurus-otp`, risorsa del realm (`/realms/{realm}/taurus-otp`) con cui il backend verifica i codici dell'app di autenticazione per la firma elettronica. Accetta solo il service account del client confidenziale `taurus-signature` con il ruolo client `otp-verifier`; il codice è controllato con la policy OTP del realm e non è mai registrato nei log ([specifica](../docs/inventory-electronic-signature-spec.md#app-di-autenticazione)).

## Verifica

```bash
./mvnw verify
```

Su Windows usare `mvnw.cmd verify`. Dopo il pacchetto, `TaurusOtpResourceIT` avvia Keycloak con Testcontainers, il JAR appena prodotto e il realm di prova `src/test/resources/otp-test-realm.json`: serve Docker. Il JAR risultante si trova in `target/` e deve essere installato come provider nell'immagine Keycloak della stessa versione indicata da `keycloak.version`.

Configurazioni realm, client e credenziali non appartengono al codice del provider e non devono essere inserite in questo modulo, salvo il realm di prova dei test.

## Temi

Il tema usato dal realm (`loginTheme` ed `emailTheme` = `taurus-theme`) vive in questo modulo, sotto `src/main/resources/theme/taurus-theme`, e viaggia dentro il JAR del provider: `src/main/resources/META-INF/keycloak-themes.json` lo dichiara con i tipi `login` ed `email`, e Keycloak lo carica dall'archivio. La cartella deve chiamarsi `theme`, al singolare: è l'unica che Keycloak legge dentro un JAR, mentre `themes` è il percorso del file system dell'immagine.

Non esiste nessuna copia montata: installando il JAR si installa anche il tema, e non c'è una seconda cartella da tenere allineata. `TenantRoleFlowIT` chiede la pagina di accesso del realm di prova e verifica che arrivi con le classi e il foglio di stile del tema, cioè che Keycloak lo abbia caricato dal JAR. I modelli di posta, invece, restano provati a mano.
