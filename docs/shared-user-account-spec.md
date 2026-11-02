# Account di accesso condiviso fra tenant

Ogni persona ha un solo account Keycloak, comune a tutti i tenant di cui fa parte. La gestione utenti di Taurus (`UsersServiceImpl`) lo trattava come se appartenesse solo al tenant corrente: l'amministratore di un tenant poteva cambiare il profilo, disattivare o cancellare l'account anche per gli altri tenant. Il documento fissa che cosa dell'account è globale, che cosa è del tenant, e come creazione e modifica di un utente toccano soltanto la parte del tenant.

Collegato a: [Configurazione iniziale del tenant](tenant-onboarding-import-spec.md#utenti), che applica già le stesse regole all'import; [Standard degli errori applicativi](application-errors-standard.md#utenti-e-tenant) per la chiave nuova.

## Stato del documento

ID catalogo: `shared-user-account`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Le decisioni sono approvate, compresa la regola su nome, cognome ed e-mail di un account condiviso: vedi [Profilo di un account condiviso](#profilo-di-un-account-condiviso).

## Modello

L'authenticator `TenantRoleSelectorAuthenticator` offre al login i tenant dei gruppi dell'utente che hanno l'attributo `<TENANT>_roles` non vuoto; senza nessuno, il login fallisce.

| Dato | Dove vive | Di chi è |
| --- | --- | --- |
| Nome, cognome, e-mail (che è anche lo username), e-mail verificata, password | Account Keycloak | Della persona, comune a tutti i tenant |
| `enabled` | Account Keycloak | Comune: un account disabilitato non entra in nessun tenant |
| Gruppo del tenant | Account Keycloak | Del tenant: l'appartenenza |
| Attributo `<TENANT>_roles` | Account Keycloak | Del tenant: i ruoli con cui si entra |
| Riga `app_user`: nome, cognome, e-mail, ruoli, stato attivo, strumenti, data di nascita | Schema del tenant | Del tenant |

Due definizioni usate sotto:

- **account condiviso**: account membro del gruppo di almeno un altro tenant, attivo o no;
- **tenant che offre l'accesso**: gruppo dell'account con `<TENANT>_roles` non vuoto, cioè un tenant che l'authenticator propone al login.

## Obiettivo e fuori scope

Obiettivo: creazione, modifica e attivazione di un utente cambiano solo gruppo, attributo `<TENANT>_roles` e riga `app_user` del tenant corrente, salvo i casi di questo documento in cui toccano l'account con una regola esplicita.

Fuori scope:

- ruolo direttivo e casi propri di amministratori e super amministratori, progettati a parte;
- il profilo modificato dalla persona (`PATCH /users/me`), che resta globale;
- eliminazione dell'utente e cancellazione per GDPR;
- l'import della configurazione iniziale, che collega già un account esistente senza toccarne il profilo;
- modifiche all'authenticator.

## Decisioni

### Creazione (`POST /users`)

1. L'account si cerca per e-mail, come oggi.
2. **Account assente**: viene creato con nome, cognome, e-mail e `enabled` uguale allo stato attivo richiesto. Solo il tenant corrente lo conosce, quindi lo stato del tenant e quello dell'account coincidono.
3. **Account esistente**: il profilo non viene scritto. Nome, cognome, e-mail ed `enabled` restano quelli dell'account; nome e cognome digitati dall'amministratore restano nella riga `app_user` del tenant, come per l'import.
4. In entrambi i casi l'account viene aggiunto al gruppo del tenant, se non c'è già, e `<TENANT>_roles` riceve i ruoli richiesti. Un utente creato non attivo entra nel gruppo senza attributo: il tenant non gli offre l'accesso, gli altri sì.

### Annullamento della creazione

Se un passo successivo alla modifica di Keycloak fallisce, per esempio il salvataggio della riga `app_user` o dell'appartenenza, la creazione si annulla soltanto per ciò che ha fatto:

- account creato in questa richiesta: viene eliminato;
- account esistente: `<TENANT>_roles` torna al valore precedente, o sparisce se non c'era, e l'account esce dal gruppo solo se la richiesta ve l'aveva aggiunto.

Un errore dell'annullamento finisce nel log, con l'identificativo dell'account; la risposta resta quella dell'errore originale.

### Stato attivo per tenant

Lo stato attivo è del tenant. Si esprime con l'attributo, non con `enabled`:

- **attivo**: `<TENANT>_roles` contiene i ruoli dell'utente;
- **non attivo**: l'attributo viene tolto e il gruppo resta, così l'appartenenza al tenant non si perde. Il login non propone più il tenant.

`enabled` cambia solo in due casi, entrambi nella modifica di un utente:

| Caso | Effetto sull'account |
| --- | --- |
| L'utente risulta non attivo e nessun altro tenant gli offre l'accesso | Disabilitato, come oggi per l'utente di un solo tenant |
| L'utente passa da non attivo ad attivo e l'account è disabilitato | Riabilitato |

In ogni altro caso `enabled` resta com'è: una modifica che non cambia lo stato non riabilita un account disabilitato per altri motivi, per esempio dalla console di Keycloak.

Quando un utente diventa non attivo e l'account resta abilitato perché altri tenant gli offrono l'accesso, Taurus chiude le sessioni dell'account (`POST /users/{id}/logout` dell'API di amministrazione). Senza, una sessione aperta nel tenant continuerebbe fino alla scadenza della sessione SSO, mentre un account disabilitato smette di rinnovare il token. La chiusura vale anche per le sessioni negli altri tenant, perché Keycloak non associa la sessione a un tenant nell'API di amministrazione: la persona accede di nuovo e sceglie fra i tenant rimasti. È fatta al meglio: se non riesce resta un avviso nel log e la modifica è comunque salvata.

### Profilo di un account condiviso

Regola approvata il 2026-11-02.

- **Account di un solo tenant**: l'amministratore modifica nome, cognome ed e-mail anche nell'account, come oggi. L'e-mail resta anche lo username.
- **Account condiviso**: se la richiesta cambia nome, cognome o e-mail rispetto alla riga `app_user` del tenant, viene rifiutata con `user.profile.locked` (409) prima di qualunque scrittura. Il resto si modifica come sempre, inviando nome, cognome ed e-mail invariati.

Motivazione:

- l'e-mail è l'indirizzo di recupero della password: l'amministratore di un tenant che la cambia può prendere il controllo dell'account negli altri tenant;
- nome e cognome sono quelli con cui gli altri tenant conoscono la persona;
- la persona può cambiare tutti e tre dal proprio profilo.

Il confronto avviene con la riga del tenant, non con l'account: le due copie possono differire, per esempio dopo una creazione su un account esistente, e una richiesta che non cambia quei campi non deve essere rifiutata.

Alternative scartate:

- **modifica libera**, il comportamento precedente: lascia aperto il caso dell'e-mail;
- **modifica solo locale**, ignorando i campi verso Keycloak: la riga del tenant diverge dall'account senza che l'amministratore lo sappia;
- **conferma degli altri tenant**: richiede un flusso fra amministratori che non esiste.

### Modifica parziale (`PATCH /users/{id}`)

I ruoli assenti dalla richiesta restano quelli attuali; la modifica scrive l'account una volta sola. Prima l'account veniva aggiornato due volte e una richiesta senza ruoli era rifiutata dopo avere già scritto su Keycloak.

## Errori

| Chiave | HTTP | Quando | Messaggio |
| --- | --- | --- | --- |
| `user.profile.locked` | 409 | Modifica di nome, cognome o e-mail di un utente il cui account appartiene anche ad altri tenant | «Nome, cognome ed e-mail sono condivisi con altri tenant: può modificarli solo la persona dal proprio profilo» |

Gli errori di Keycloak restano quelli del [catalogo](application-errors-standard.md#servizio-di-accesso-keycloak).

## Autorizzazioni e isolamento tenant

Gli endpoint restano riservati agli stessi ruoli. Le scritture sull'account partono dal tenant della sessione: gruppo e attributo di altri tenant si leggono, per decidere `enabled` e per riconoscere un account condiviso, ma non si modificano. Le sole scritture globali sono la creazione di un account nuovo, `enabled` nei due casi della tabella, il profilo di un account di un solo tenant e la chiusura delle sessioni.

## Osservabilità

Gli annullamenti non riusciti e le chiusure di sessione non riuscite si registrano nel log con l'identificativo Keycloak dell'account, senza e-mail né nomi.

## Test

- `UsersServiceImplTest`: creazione su account esistente senza scrittura del profilo; utente non attivo senza attributo; annullamento per account creato ed esistente; disattivazione con e senza altri tenant che offrono l'accesso; riattivazione; profilo di un account condiviso rifiutato e di un account di un solo tenant modificato; `PATCH` senza ruoli.
- `SharedUserAccountKeycloakIT`, con Keycloak reale avviato tramite `DockerRequirement.required()` e un account presente in due tenant: creazione nel secondo tenant, annullamento dopo un errore del salvataggio, disattivazione nel secondo tenant con l'account che resta abilitato e il primo tenant intatto, profilo bloccato.

## Criteri di accettazione

1. La creazione su un account esistente non cambia nome, cognome, e-mail ed `enabled` dell'account.
2. Un errore dopo il collegamento non elimina mai un account esistente e lascia gruppo e attributi degli altri tenant come prima.
3. La disattivazione in un tenant non disabilita un account a cui un altro tenant offre l'accesso; il login non propone più il tenant.
4. La modifica di nome, cognome o e-mail di un account condiviso è rifiutata senza scritture.
5. `./mvnw verify` in `taurus-be` è verde.

## Compatibilità

Nessuna migrazione e nessuna modifica di API. Effetti sui dati esistenti:

- un utente reso non attivo prima di questa modifica ha ancora `<TENANT>_roles`; finché l'account è disabilitato non entra, e alla prossima modifica l'attributo viene tolto;
- un account disabilitato in passato dalla disattivazione in un tenant resta disabilitato anche se altri tenant lo considerano attivo: una modifica che non cambia lo stato non lo riabilita. Lo riabilita la console di Keycloak, oppure un amministratore di quei tenant che rende l'utente non attivo e poi di nuovo attivo.

Il frontend non sa ancora se un account è condiviso: mostra il messaggio della chiave dopo il salvataggio. Bloccare i campi nel dettaglio utente richiede un'informazione in più nella risposta ed è un intervento successivo.
