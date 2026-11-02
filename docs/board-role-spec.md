# Ruolo direttivo

Il documento progetta il ruolo **direttivo** (`ROLE_BOARD`). Il direttivo gestisce l'associazione come l'amministratore: catalogo, economia, inventario, calendario ed eventi, utenti. Può inoltre firmare per l'associazione i fogli di affidamento. Restano solo all'amministratore la configurazione del tenant, gli strumenti tecnici e le azioni sugli amministratori.

Riguarda i ruoli già descritti nella [Gestione dell'inventario](inventory-management-spec.md), nella [Firma elettronica dei fogli di affidamento](inventory-electronic-signature-spec.md), nella [Preparazione degli eventi](event-preparation-spec.md), nella [Gestione economica](financial-management-spec.md), nella [Dashboard operativa](operational-dashboard-spec.md) e nell'[Importazione iniziale del tenant](tenant-onboarding-import-spec.md). Gli errori seguono lo [Standard degli errori applicativi](application-errors-standard.md), le notifiche lo [Stile editoriale delle notifiche interne](notification-editorial-style.md) e l'interfaccia il [Taurus Layout Standard](taurus-layout-standard.md).

## Stato del documento

ID catalogo: `board-role`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

Le scelte sono fissate in [Decisioni prese](#decisioni-prese).

## Problema

- Nelle associazioni il consiglio direttivo gestisce archivio, cassa, materiale, eventi e soci. In Taurus questo richiede il ruolo di amministratore, che però dà anche la configurazione del tenant, gli strumenti tecnici e il potere su altri amministratori.
- Archivista e tesoriere coprono solo una parte del lavoro. Non gestiscono utenti, inventario ed eventi, e non possono firmare i fogli di affidamento.
- Chi firma per l'associazione deve oggi essere un amministratore attivo. Per far firmare un membro del direttivo bisogna farlo amministratore.

## Obiettivo

- Un ruolo con tutti i permessi di amministratore sulle aree di gestione: catalogo, economia, inventario, calendario ed eventi, utenti.
- Il direttivo può essere scelto come firmatario dei fogli di affidamento e firmare per l'associazione.
- Il direttivo non può creare, modificare né eliminare amministratori e super amministratori, né dare a qualcuno il ruolo di amministratore, compreso sé stesso.
- Restano dell'amministratore configurazione iniziale, consegne notifiche, feed calendario di tutti, documentazione API e metriche.

## Fuori scope

- Cambiare il modello di sessione: resta un solo ruolo per sessione, scelto al login quando il tenant ne offre più di uno.
- Permessi configurabili per tenant o ruoli personalizzati.
- Le funzioni del super amministratore: tenant, documenti legali, funzionalità del tenant.
- Limiti tra direttivi: un direttivo può creare, modificare e disattivare altri direttivi.
- L'account Keycloak è unico per persona e condiviso fra i tenant, mentre la gestione utenti lo tratta come se fosse del tenant. Il problema esiste già per l'amministratore e va trattato a parte:
  - alla creazione con un'email già registrata, nome, cognome e stato di attivazione dell'account esistente vengono sovrascritti;
  - se poi il salvataggio nel tenant fallisce, l'account esistente viene eliminato da Keycloak;
  - la modifica di un utente cambia nome, email e stato di attivazione per tutti i tenant, e disattivarlo in un tenant gli impedisce l'accesso anche agli altri.

  Qui si chiude solo il varco verso amministratori e super amministratori.

## Contesto attuale

- I ruoli sono definiti in `AuthoritiesConstants` e `RoleEnum` (backend) e in `RoleEnums` (frontend). Ogni controllo elenca i ruoli ammessi: non esiste una gerarchia né un gruppo di ruoli condiviso.
- L'authenticator di Keycloak (`TenantRoleSelectorAuthenticator`) legge i ruoli del tenant dall'attributo `<TENANT>_roles` e ne mette **uno solo** nel token, scelto al login se sono più di uno. Il ruolo direttivo quindi non eredita i permessi di archivista e tesoriere: va aggiunto in ogni controllo.
- `UsersServiceImpl.setUserRolesOnKeycloak` scrive solo i ruoli che esistono come ruoli client in Keycloak e scarta gli altri senza errore. `KeycloakRoleProvisioner` crea oggi solo `ROLE_TREASURER`.
- I ruoli degli utenti sono salvati come testo (`@Enumerated(EnumType.STRING)`), senza vincoli nel database.
- I permessi stanno in quattro posti:
  1. le regole per percorso di `SecurityConfiguration`;
  2. le annotazioni `@PreAuthorize` dei controller;
  3. i controlli scritti nei servizi;
  4. i destinatari delle notifiche, indicati per ruolo e risolti dai ruoli salvati nel database.
- Il firmatario per l'associazione si sceglie tra gli utenti attivi con ruolo `ROLE_ADMIN` o `ROLE_SUPER_ADMIN` (`LoanSheetSignatureService.ADMIN_ROLES`). Il controllo si ripete al momento della firma.
- `UsersServiceImpl` vieta solo il ruolo di super amministratore. Chi gestisce gli utenti può oggi assegnare il ruolo di amministratore. Alla creazione, se l'email esiste già in Keycloak, l'account esistente viene aggiornato e collegato al tenant.

## Decisioni prese

Le scelte discusse e fissate il 02/11/2026.

### 1. Codice ed etichetta

Codice `ROLE_BOARD`, etichetta «Direttivo» (in inglese «Board»), codice di importazione `BOARD`. Un codice inglese come gli altri ruoli; l'etichetta segue il nome usato nelle associazioni.

### 2. Permessi pari all'amministratore, con cinque eccezioni

Il direttivo ha ovunque gli stessi permessi dell'amministratore, tranne:

| Area | Amministratore | Direttivo |
| --- | --- | --- |
| Catalogo: album, tracce, strumenti, media, segnalazioni di errore | sì | sì |
| Economia, compresa la preparazione economica degli eventi | sì | sì |
| Inventario: articoli, assegnazioni, fogli e modello, QR, segnalazioni, richieste di cancellazione | sì | sì |
| Calendario: eventi, serie, disponibilità, presenze, promemoria, preparazione completa | sì | sì |
| Utenti, compresa la cancellazione GDPR | sì | sì, tranne sugli amministratori |
| Firma per l'associazione sui fogli di affidamento | sì | sì |
| Configurazione iniziale (onboarding) | sì | no |
| Consegne notifiche (`/api/admin/notification-delivery`) | sì | no |
| Feed calendario di tutti (`/api/admin/calendar-feeds`) | sì | no |
| Documentazione API e metriche (`/v3/api-docs`, `/management/**`) | sì | no |
| Azioni sugli amministratori e assegnazione del ruolo di amministratore | sì | no |

Il feed calendario **personale** del direttivo segue le regole di oggi. Si crea dalla sessione con ruolo utente o utente esterno, come per l'amministratore.

### 3. Gruppi di ruoli condivisi

Il backend introduce una classe `RoleGroups` che dà un nome ai ruoli ammessi:

| Gruppo | Ruoli | Uso |
| --- | --- | --- |
| `TENANT_MANAGERS` | super amministratore, amministratore, direttivo | inventario, calendario, eventi, utenti |
| `TENANT_ADMINS` | super amministratore, amministratore | le cinque eccezioni |
| `CATALOGUE_MANAGERS` | `TENANT_MANAGERS` più archivista | catalogo |
| `FINANCE_MANAGERS` | `TENANT_MANAGERS` più tesoriere | economia |
| `ASSOCIATION_SIGNERS` | amministratore, direttivo | firmatari dei fogli |

Ogni gruppo esiste in due forme:
- come elenco di authority, per `SecurityConfiguration` e i controlli sul token;
- come insieme di `RoleEnum`, per i destinatari delle notifiche e le query sugli utenti.

Nelle annotazioni `@PreAuthorize` restano i ruoli scritti per esteso. La matrice dei test della [strategia di test](#strategia-di-test) verifica che coincidano con i gruppi. Il frontend ha gli stessi gruppi in `role.enums.ts` e il getter `KeycloakService.isTenantManager`. `isAdmin` resta per le eccezioni.

Aggiungere il ruolo in ogni elenco esistente, senza gruppi, lascerebbe gli stessi punti da ritoccare a ogni nuovo ruolo. In più, un punto dimenticato non produce errori.

### 4. Amministratori protetti

Per il direttivo, un utente è **protetto** quando vale almeno una di queste condizioni:
- ha `ROLE_ADMIN` tra i ruoli salvati nel tenant;
- il suo account Keycloak ha il ruolo client `ROLE_SUPER_ADMIN`;
- alla creazione, l'email appartiene a un account Keycloak esistente che è super amministratore, oppure amministratore di un tenant qualsiasi (`ROLE_ADMIN` in un attributo `<TENANT>_roles`).

Il direttivo può vedere gli utenti protetti, ma non può eseguire su di loro queste azioni, né assegnare `ROLE_ADMIN` a qualsiasi utente:

| Azione | Endpoint |
| --- | --- |
| Creazione | `POST /api/users` |
| Modifica, anche parziale: dati, strumenti, ruoli, stato attivo | `PUT` e `PATCH /api/users/{id}` |
| Eliminazione | `DELETE /api/users/{id}` |
| Cancellazione GDPR | `DELETE /api/users/{id}/gdpr` |
| Invio della mail di configurazione dell'account | `PUT /api/users/{id}/send-setup-email` |

Il controllo sta in `UsersServiceImpl`, non solo nelle regole di sicurezza. Copre così ogni percorso che porta a Keycloak. Vale quando il ruolo della sessione è `ROLE_BOARD`; amministratore e super amministratore non hanno limiti nuovi.

Esito: `403` con chiave `user.admin.forbidden` e messaggio «Solo un amministratore può gestire gli amministratori». `user.role.forbidden` resta per il ruolo di super amministratore.

Un utente che è insieme amministratore e direttivo, quando entra come direttivo, non può modificare sé stesso. Per farlo entra come amministratore.

### 5. Firmatario per l'associazione

`ASSOCIATION_SIGNERS` sostituisce `ADMIN_ROLES` in `LoanSheetSignatureService`, e `activeAdministrator` diventa `activeAssociationSigner`. Il cambio vale in quattro punti:
- l'elenco dei firmatari proposti all'emissione (`associationSigners`);
- chi registra un'operazione diventa firmatario (`operationSigner`);
- il controllo al momento della firma (`LoanSheetSigning`);
- la dichiarazione cartacea (`PaperDeclarationSignatureHandler`).

Il super amministratore non firma per l'associazione: gestisce la piattaforma, non fa parte dell'associazione. Oggi `ADMIN_ROLES` lo comprende, ma compare tra i firmatari solo se ha una riga utente attiva nel tenant con quel ruolo, cosa che l'applicazione e l'importazione non permettono di creare.

Chi ha in carico il foglio resta escluso dai firmatari, come oggi. Un direttivo che perde il ruolo o viene disattivato smette di poter firmare: valgono le regole attuali di sostituzione del firmatario. La chiave `signature.associationSigner.notAdmin` resta invariata per non rompere il contratto; cambia il messaggio: «Chi firma per l'Associazione deve essere un amministratore o un membro del direttivo attivo: designare un altro firmatario».

### 6. Eventi in ogni aspetto

Il direttivo apre la preparazione dell'evento con la vista dell'amministratore (`Reader.ADMIN`):
- dati e visibilità;
- disponibilità e presenze;
- materiali;
- programma;
- budget e conferma senza movimenti;
- riconferme.

Può inoltre creare, modificare ed eliminare eventi e serie. Usa gli endpoint di `EventPreparationResource`. Anche `FinanceEventPreparationResource` gli è aperto, perché le regole restino coerenti.

### 7. Notifiche

Il direttivo riceve le notifiche oggi destinate agli amministratori per utenti, calendario, preparazione eventi, inventario, firme, scadenze ed economia. Riceve anche quelle del catalogo destinate ad amministratori e archivisti. Non riceve quelle della configurazione iniziale (`OnboardingNotifier`). Le notifiche sulle consegne fallite restano al super amministratore, come oggi.

### 8. Keycloak e importazione

- `KeycloakRoleProvisioner` crea **tutti** i ruoli di `RoleEnum`, non solo `ROLE_TREASURER`. Così l'elenco dei ruoli sta in un posto solo e un ruolo nuovo arriva in Keycloak senza altri interventi. Senza il ruolo client, `setUserRolesOnKeycloak` scarterebbe l'assegnazione in silenzio.
  - Le descrizioni stanno in una mappa accanto a `RoleEnum`, con una voce per ogni valore:

    | Ruolo | Descrizione |
    | --- | --- |
    | `ROLE_SUPER_ADMIN` | Platform super administrator |
    | `ROLE_ADMIN` | Tenant administrator |
    | `ROLE_BOARD` | Tenant board member |
    | `ROLE_TREASURER` | Tenant finance manager |
    | `ROLE_ARCHIVIST` | Tenant catalogue manager |
    | `ROLE_USER` | Tenant member |
    | `ROLE_USER_EXTERNAL` | Tenant external member |

  - Comprende il super amministratore: creare un ruolo non lo assegna a nessuno, e `getUsersByClientRoles(ROLE_SUPER_ADMIN)` fallisce se il ruolo manca.
  - Il provisioner legge una volta i ruoli del client e crea solo quelli mancanti. Non aggiorna le descrizioni dei ruoli esistenti e non cancella ruoli assenti da `RoleEnum`: cancellare un ruolo assegnato è un'operazione manuale.
  - Gira all'avvio quando `application.keycloak.provision-roles` è attivo, come oggi. Se fallisce, lo registra nel log e riprova all'avvio successivo.
- Il realm di sviluppo (`taurus-realm.json`) riceve `ROLE_BOARD` e le stesse descrizioni della mappa al posto del generico «Client role».
- I temi del login hanno l'etichetta del ruolo (`role.ROLE_BOARD`) per la scelta del ruolo.
- L'importazione iniziale accetta `BOARD` tra i ruoli. La configurazione iniziale resta dell'amministratore, quindi un direttivo non può usarla per creare amministratori.

## Autorizzazioni e isolamento tenant

Il ruolo vale per tenant, come gli altri: sta nell'attributo `<TENANT>_roles` e nei ruoli salvati nello schema del tenant. Un direttivo di un tenant non ha permessi in un altro. Il controllo sugli amministratori protetti guarda anche gli altri tenant solo alla creazione, quando l'account Keycloak è condiviso.

## Modello dati e migrazioni

Nessuna migrazione Liquibase: `ROLE_BOARD` è un nuovo valore di `RoleEnum`, salvato come testo.

## Modifiche per modulo

### Backend (`taurus-be`)

**Ruolo e gruppi**
- `AuthoritiesConstants.BOARD`, `RoleEnum.ROLE_BOARD` (anche in `mapToString`), nuova classe `RoleGroups`.
- `KeycloakRoleProvisioner`: creazione di tutti i ruoli di `RoleEnum` con le descrizioni della mappa ([decisione 8](#8-keycloak-e-importazione)), leggendo una volta i ruoli del client.
- `KeycloakServiceImpl.ensureClientRole`: accetta l'elenco dei ruoli esistenti, così non lo rilegge a ogni ruolo.

**Regole per percorso (`SecurityConfiguration`)**

Ricevono `ROLE_BOARD`:
- `/api/inventory/**`, `/api/finance/**`, `/api/users/**` compreso `DELETE /api/users/{id}/gdpr`;
- `/api/albums/**`, `/api/tracks/**`, `/api/instruments/**`;
- le scritture su `/api/media/**`;
- tutte le regole di `/api/calendar-events/**` e `/api/calendar-event-series/**`;
- `GET /api/dashboard/operations/calendar-events`.

Restano senza: `/api/onboarding/**`, `/api/admin/**`, `/v3/api-docs/**`, `/management/**`, e il ruolo `ROLE_ADMIN` per i dettagli di salute in `application.yml`.

**Annotazioni `@PreAuthorize`**

Aggiungono `ROLE_BOARD`:
- tutte quelle di `EventPreparationResource` e `FinanceEventPreparationResource`;
- quelle di catalogo ed economia con `ROLE_ARCHIVIST` o `ROLE_TREASURER`.

Restano senza: `AdminCalendarFeedResource` e `NotificationDeliveryAdminResource`.

**Controlli nei servizi**

| Punto | Modifica |
| --- | --- |
| `UsersServiceImpl` | regola degli amministratori protetti in `save`, `update`, `partialUpdate`, `delete`, `deleteForGdpr`, `sendSetupEmail` |
| `LoanSheetSignatureService`, `LoanSheetSigning`, `PaperDeclarationSignatureHandler` | `ASSOCIATION_SIGNERS` e `activeAssociationSigner` |
| `InventoryScanService.isAdministrator` | `TENANT_MANAGERS`: la scansione del QR apre la vista di gestione |
| `EventPreparationService` (controllo sulle authority e vista `Reader.ADMIN`) | `TENANT_MANAGERS` |
| `CalendarOperationProvider`, `InventoryOperationProvider` | `TENANT_MANAGERS` |
| `FinanceOperationProvider` | `FINANCE_MANAGERS` |
| `NotificationOperationProvider` | invariato: consegne notifiche |
| `CalendarFeedManagementService` (stati visibili a chi chiama) | aggiunge `ROLE_BOARD` |
| `MediaServiceImpl`, `TrackPageImageService`, `ScorePlaybackSupport` | `CATALOGUE_MANAGERS` |

**Destinatari delle notifiche**
- `NoticesAspect`: `publishAdmins`, `publishEditorial`, `publishPublished`, `publishSystemAdmins`, le notifiche degli atti di firma e `FINANCE_NOTIFICATION_ROLES`.
- `EventPreparationNotificationScheduler` (`ADMIN_AUDIENCE`) ed `EventPreparationService`.
- `InventoryIssueService`, `InventoryQrCodeService`, `InventoryNoticeDataService`, `ScoreErrorReportService`.
- `OnboardingNotifier` resta invariato.

**Importazione**
- `OnboardingValidationService`: `BOARD` tra i ruoli ammessi.
- `OnboardingTemplateService`: etichetta «direttivo, gestisce l'associazione tranne gli amministratori».

### Frontend (`taurus-fe`)

**Costanti e servizi**
- `role.enums.ts`: `RoleEnums.BOARD`, etichetta «Direttivo» in `RoleLabelsMap`, gruppi `TENANT_MANAGER_ROLES`, `CATALOGUE_MANAGER_ROLES`, `FINANCE_MANAGER_ROLES`.
- `KeycloakService.isTenantManager`, usato al posto di `isAdmin` in calendario, dettaglio evento, inventario e widget dell'inventario. `isAdmin` resta per le eccezioni.

**Menu, route e template**
- Menu: aggiungono il direttivo Utenti, Economia, Inventario, Album, Tracce, Strumenti e Calendario. Restano senza Configurazione iniziale, Consegne notifiche e Feed calendario.
- Route: album, tracce, strumenti, calendario, economia, inventario (tutte, compresi modello del foglio e dettagli) e utenti. L'onboarding resta senza.
- Template con `*hasRoles`: album, dettaglio album, tracce, dettaglio traccia, calendario, dettaglio evento, dashboard.
- Controlli scritti nei componenti: `albums/detail`, `tracks/detail`, `score-playback.service`, `event-preparation.service`, `preparation-confirmation`, `calendar-events.component`.
- Dashboard: il direttivo carica i dati dell'amministratore (`adminMethods`) e vede il widget Utenti.

**Gestione utenti e messaggi**
- In sessione direttivo, `add-users-dialog` e il dettaglio utente non propongono il ruolo di amministratore.
- Per gli utenti protetti, modifica, eliminazione, cancellazione GDPR e invio della mail sono disattivati, con il motivo nel suggerimento. Il controllo del backend resta quello che decide.
- `api-error-messages.ts`: chiave `user.admin.forbidden` e nuovo testo di `signature.associationSigner.notAdmin`.

### Keycloak (`keycloak-authenticator` e realm)

- `messages_it.properties` e `messages_en.properties`: `role.ROLE_BOARD=Direttivo` / `Board`.
- `taurus-be/src/main/docker/realm-config/taurus-realm.json`: ruolo client `ROLE_BOARD` e descrizioni allineate alla mappa dei ruoli.
- Realm di test dell'authenticator, se serve alle prove di scelta del ruolo.

### Documenti da allineare

- [Standard degli errori applicativi](application-errors-standard.md): chiave `user.admin.forbidden` e testo aggiornato del firmatario.
- Firma elettronica, fogli di affidamento, preparazione eventi, dashboard operativa e importazione iniziale: i passaggi che oggi indicano solo l'amministratore.
- `taurus-fe/e2e/README.md`: utente di prova `E2E_FIRST_BOARD_*` con `ROLE_BOARD`.
- [Runbook dei test](runbook-test.md): nuove prove e baseline di copertura.

## Sicurezza e osservabilità

- Il rischio principale è che il direttivo si promuova da solo. Lo chiudono tre misure:
  - la regola degli amministratori protetti nel servizio;
  - l'esclusione dalla configurazione iniziale;
  - il controllo sull'account esistente alla creazione.
- I rifiuti `user.admin.forbidden` passano dal normale registro degli errori applicativi: non servono metriche nuove.
- Le notifiche «Utente: creato / aggiornato / rimosso» raggiungono anche gli amministratori. Così un'azione del direttivo sugli utenti resta visibile a chi ha il ruolo più alto.

## Strategia di test

### Backend

- **Matrice di parità** (`*IT`): per ogni regola di `SecurityConfiguration` e ogni `@PreAuthorize`, una richiesta come direttivo ha lo stesso esito di una come amministratore. Fanno eccezione le cinque esclusioni, dove il direttivo riceve `403`. La matrice è la protezione contro i punti dimenticati.
- **Utenti**: in sessione direttivo, ogni azione dell'elenco su un amministratore, su un super amministratore e su un'email Keycloak protetta restituisce `user.admin.forbidden`. Lo stesso per l'assegnazione di `ROLE_ADMIN`. Le stesse azioni su utenti e direttivi riescono; da amministratore nulla cambia.
- **Firma**: il direttivo compare tra i firmatari, firma per l'associazione, registra le operazioni e la dichiarazione cartacea. Chi ha in carico il foglio è escluso; un direttivo disattivato non può firmare. Il super amministratore non compare tra i firmatari e non può firmare per l'associazione.
- **Notifiche e dashboard**: i destinatari includono `ROLE_BOARD` dove previsto e lo escludono dall'onboarding. Le operazioni della dashboard per il direttivo coincidono con quelle dell'amministratore, senza le consegne notifiche.
- **Provisioning**:
  - ogni valore di `RoleEnum` ha una descrizione nella mappa;
  - il provisioner crea solo i ruoli mancanti, leggendo una volta i ruoli del client, e non tocca quelli esistenti;
  - un errore di Keycloak non blocca l'avvio;
  - `taurus-realm.json` contiene tutti i ruoli di `RoleEnum`.
- **Importazione**: accetta `BOARD`.

### Frontend

- Specifiche per menu, route, `isTenantManager`, opzioni dei ruoli e azioni disattivate sugli utenti protetti.

### End-to-end

- `roles-and-tenants.spec.ts` (menu del direttivo), `event-preparation.spec.ts` (preparazione completa) e la dashboard per ruolo, con l'utente di prova del direttivo.

Le baseline di copertura dei due moduli si alzano se la copertura cresce.

## Criteri di accettazione

1. Un utente con `ROLE_BOARD` entra nel tenant e vede il menu di gestione senza Configurazione iniziale, Consegne notifiche e Feed calendario.
2. Il direttivo gestisce catalogo, economia, inventario, calendario, eventi e preparazione completa con gli stessi esiti dell'amministratore.
3. Il direttivo è proposto come firmatario all'emissione di un foglio e può firmare per l'associazione, anche nella dichiarazione cartacea.
4. Il direttivo gestisce utenti e altri direttivi, ma ogni azione su un amministratore o un super amministratore, e ogni assegnazione del ruolo di amministratore, finisce con `user.admin.forbidden`.
5. Il direttivo riceve le notifiche delle aree che gestisce e non quelle della configurazione iniziale.
6. Onboarding, consegne notifiche, feed calendario di tutti, documentazione API e metriche rispondono `403` al direttivo.
7. Dopo l'avvio del backend tutti i ruoli di `RoleEnum` esistono in Keycloak, e il direttivo è selezionabile al login con l'etichetta «Direttivo».
8. Le verifiche di `taurus-be`, `taurus-fe` e `keycloak-authenticator` passano.

## Piano di adozione e compatibilità

1. Rilascio del backend con il provisioner: all'avvio vengono creati in Keycloak i ruoli che mancano, `ROLE_BOARD` compreso. I ruoli esistenti restano come sono. Dove `application.keycloak.provision-roles` è disattivato, i ruoli mancanti si creano a mano prima di assegnarli.
2. Rilascio di authenticator e temi per l'etichetta nella scelta del ruolo.
3. Rilascio del frontend.
4. Gli amministratori assegnano il ruolo dalla gestione utenti o con l'importazione. Il nuovo ruolo vale dal login successivo.

Utenti e ruoli esistenti non cambiano. Nessun permesso dell'amministratore viene ridotto. L'unico permesso tolto è la firma per l'associazione del super amministratore: un atto in corso che lo indica come firmatario richiede di designarne un altro, con la sostituzione del firmatario già prevista.
