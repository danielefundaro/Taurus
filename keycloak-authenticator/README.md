# Taurus Keycloak Authenticator

Provider Java 17 per la selezione tenant/ruolo durante l'autenticazione e temi personalizzati Taurus per login ed e-mail Keycloak.

## Verifica

```bash
./mvnw verify
```

Su Windows usare `mvnw.cmd verify`. Il JAR risultante si trova in `target/` e deve essere installato come provider nell'immagine Keycloak prevista dall'ambiente di distribuzione.

Configurazioni realm, client e credenziali non appartengono al codice del provider e non devono essere inserite in questo modulo.
