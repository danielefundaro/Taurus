# Taurus Keycloak Authenticator

Provider Java 17 per Keycloak e temi personalizzati Taurus per login ed e-mail Keycloak:

- selezione tenant/ruolo durante l'autenticazione e mapper dei claim `tenant` e `role`;
- `taurus-otp`, risorsa del realm (`/realms/{realm}/taurus-otp`) con cui il backend verifica i codici dell'app di autenticazione per la firma elettronica. Accetta solo il service account del client confidenziale `taurus-signature` con il ruolo client `otp-verifier`; il codice è controllato con la policy OTP del realm e non è mai registrato nei log ([specifica](../docs/inventory-electronic-signature-spec.md#app-di-autenticazione)).

## Verifica

```bash
./mvnw verify
```

Su Windows usare `mvnw.cmd verify`. Dopo il pacchetto, `TaurusOtpResourceIT` avvia Keycloak con Testcontainers, il JAR appena prodotto e il realm di prova `src/test/resources/otp-test-realm.json`: serve Docker. Il JAR risultante si trova in `target/` e deve essere installato come provider nell'immagine Keycloak della stessa versione indicata da `keycloak.version`.

Configurazioni realm, client e credenziali non appartengono al codice del provider e non devono essere inserite in questo modulo, salvo il realm di prova dei test.
