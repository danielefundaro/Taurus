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

I temi usati dal realm (`loginTheme` ed `emailTheme` = `taurus-theme`) sono in `taurus-be/src/main/docker/realm-config/taurus-theme`, montati da `keycloak.yml` in `/opt/keycloak/themes/taurus-theme`. La cartella `src/main/resources/themes/taurus` di questo modulo è una copia non allineata a quei temi: senza `META-INF/keycloak-themes.json` Keycloak non la carica dal JAR.
