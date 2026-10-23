# Revisione allineamento documentazione e codice Taurus

Oct 22, 2026 · aggiornato Oct 23, 2026 · @Daniele

## Sintesi

Sono stati corretti 16 difetti del codice emersi dal confronto con la documentazione, con test unitari per ciascuno. L'allineamento della documentazione è completo per tutti i 43 documenti, il catalogo è aggiornato e tutte le verifiche passano: governance, test e build del frontend, `./mvnw verify` del backend con i test di integrazione su PostgreSQL.

La revisione ha confrontato con il codice di `main` i 35 documenti di `docs/` (esclusi il generato features.md e `llms-full.md`), i README dei moduli, `AGENTS.md`, il template PR e il catalogo `docs/features.json`. I controlli automatici erano già verdi all'inizio: il contenuto invece era disallineato in quasi tutti i documenti.

Nulla è stato committato: tutte le modifiche sono nella working tree.

## Interventi sul codice

Ogni difetto ha un test che riproduce il caso; i test unitari dei moduli toccati passano. Percorsi Java abbreviati: `be/` = `taurus-be/src/main/java/com/fundaro/zodiac/taurus/`.

| # | Difetto | Correzione | File principali | Test |
| --- | --- | --- | --- | --- |
| 1 | Promemoria personale: `PATCH …/reminder` rispondeva 403 a soci ed esterni (finiva nel `denyAll`), e ad archivisti | Regole esplicite per USER, USER\_EXTERNAL e ARCHIVIST | `be/config/SecurityConfiguration.java` | `SecurityConfigurationTest` (+7 casi) |
| 2 | Il saldo iniziale di un conto veniva cancellato o sovrascritto dal riporto notturno o da «Ricalcola» | Colonna `financial_movement.initial_balance`: il riporto non tocca l'apertura inserita a mano; la creazione del conto ricalcola gli anni successivi | `be/service/impl/FinanceService.java`, `FinancialMovement.java`, `OnboardingDomainApplicationService.java` | `FinanceServiceTest` |
| 3 | Saldo iniziale assente da estratto conto e rendiconto annuale | Il saldo iniziale dei conti aperti nel periodo entra nel saldo d'apertura | `be/service/impl/FinanceService.java` | `FinanceServiceTest` (+2) |
| 4 | Dashboard: con un guasto UNSAFE o LIMITING aperto spariva l'intero dominio Inventario | Percorsi dei due tipi aggiunti all'elenco ammesso | `be/service/OperationalDashboardService.java` | `OperationalDashboardServiceTest` (+2, uno controlla tutti i tipi) |
| 5 | Conservazione dei fogli, eliminazione degli atti, scadenza dei link e pulizia delle conferme solo sui tenant con Inventario attivo | I quattro job girano su tutti i tenant attivi | `InventoryLoanSheetRetentionScheduler`, `SignaturePurgeScheduler`, `SignatureLinkExpiryScheduler`, `SignatureChallengeCleanupScheduler` | — |
| 6 | Il tesoriere non apriva il dettaglio evento dalle notifiche economiche | La rotta padre `calendar` ammette TREASURER; l'elenco resta escluso | `taurus-fe/src/app.routes.ts` | `app.routes.spec.ts` |
| 7 | Onboarding senza conservazione: file e dati personali senza scadenza | Nuovo job notturno: file, righe e problemi eliminati dopo 30 giorni, journal ridotto, job eliminato dopo 365 | `OnboardingRetentionService`, `OnboardingCleanupScheduler`, `MediaRepository`, `OnboardingImportService` | `OnboardingRetentionServiceTest` (3) |
| 8 | Serie ricorrenti: dopo «Salva da questo evento in poi» la data originaria restava vecchia; una seconda modifica raddoppiava lo spostamento | La data originaria segue la nuova regola dalla sorgente in poi; conflitto → 409 | `be/service/impl/CalendarEventSeriesServiceImpl.java` | `CalendarEventSeriesServiceImplTest` (+1) |
| 9 | Le viste personali della preparazione evento esponevano stato e criticità | Valutazione tolta dalla risposta; il frontend la richiede per mostrare la sezione | `EventPreparationService.java`, `detail.component.html` | `EventPreparationServiceTest` |
| 10 | Promemoria push inviati dopo l'inizio dell'evento (es. dopo un fermo) | Chiusi `SKIPPED` con motivo `EVENT_STARTED` | `be/rabbitmq/PushReminderScheduler.java` | `PushReminderSchedulerTest` (+1) |
| 11 | Job PDF bloccato in coda se la pubblicazione dopo il commit falliva | Il job passa a `ERROR` in una transazione propria ed è ritentabile | `TracksServiceImpl`, `QueueUploadFilesService(Impl)` | `TracksServiceImplTest` (+1) |
| 12 | Limiti di 3 feed personali e 10 di tenant superabili con richieste concorrenti | Advisory lock sul limite prima del conteggio | `CalendarFeedManagementService.java` | `CalendarFeedManagementServiceTest` |
| 13 | Token dei feed, codici e dati della firma, UUID dei QR nei log DEBUG del profilo dev | `LoggingAspect` esclude le classi che li trattano | `be/aop/logging/LoggingAspect.java` | — |
| 14 | Immagine decodificata per intero prima del controllo delle dimensioni | Dimensioni lette dall'header prima della decodifica | `ImageTransformationService.java` | `ImageTransformationServiceTest` (+1) |
| 15 | La notifica «Tenant: rimosso» finiva nello schema eliminato e non arrivava mai | Registrata nel tenant in cui lavora il super amministratore | `be/aop/notices/NoticesAspect.java` | `NoticesAspectTest` (+1) |
| 16 | Un promemoria fallito in modo definitivo veniva ripreso ogni minuto | Chiuso `FAILED` con `sent = true`; torna in coda solo dal retry della console | `be/rabbitmq/PushReminderScheduler.java` | `PushReminderSchedulerTest` (+1) |

Il difetto 16 non era nell'elenco iniziale: l'ha trovato il revisore delle notifiche durante l'allineamento.

## Migrazioni, configurazione e impatti di rilascio

Il rilascio porta due migrazioni dello schema tenant, una proprietà nuova e due chiavi d'errore; i saldi iniziali già danneggiati non si ripristinano da soli.

| Elemento | Dettaglio | Impatto |
| --- | --- | --- |
| `20261022000000_financial_initial_balance.xml` (tenant) | Colonna `financial_movement.initial_balance`; marca come saldo iniziale le aperture con descrizione esattamente «Saldo iniziale» | Un saldo iniziale già eliminato o sovrascritto dal riporto non viene ripristinato: va controllato e corretto a mano |
| `20261022000001_onboarding_retention.xml` (tenant) | Colonna `onboarding_import_job.source_removed_at`; `onboarding_identity_operation.row_id` facoltativo con `ON DELETE SET NULL` | Modifica di un vincolo esistente; rollback incluso |
| `application.onboarding.cleanup-cron` | Default `0 30 4 * * *`, zona Europe/Rome | Il primo passaggio elimina subito file e righe degli import conclusi da oltre 30 giorni |
| Chiave `onboarding.job.sourceRemoved` (409) | Nuova validazione o nuovo invio delle e-mail dopo la pulizia | Registrata nel catalogo errori e nei messaggi del frontend |
| Chiave `calendar.series.occurrence.conflict` (409) | La nuova regola porta un'occorrenza sulla data di un'altra | Prima era un errore 500 |
| Sicurezza | Nuove regole per `PATCH …/reminder`; `LoggingAspect` esclude feed, firma e scansione QR | Configurazione di sicurezza da rivedere prima del merge |

Le due migrazioni sono state eseguite su PostgreSQL dai test di integrazione di `./mvnw verify`, senza errori.

## Documentazione allineata

Tutti i documenti descrivono ora il codice della working tree. Le funzionalità non realizzate restano nelle specifiche, raccolte in «Requisiti aperti»; gli scostamenti degli standard sono in «Scostamenti noti»; le frasi temporali di stato sono state tolte.

| Area | Documenti | Interventi principali di questa sessione |
| --- | --- | --- |
| Calendario, preparazione eventi, dashboard | `recurring-calendar-events-spec.md`, `external-calendar-feed-spec.md`, `event-preparation-spec.md`, `operational-dashboard-spec.md` | Colonna del tesoriere nelle autorizzazioni delle serie; vista archivista della preparazione con valutazione ristretta e conteggi; natura `ORDINARY` e totali per valuta dei movimenti in dashboard; tag `result` delle metriche |
| Economia, tenant, piattaforma | `financial-management-spec.md`, `event-payout-shares-spec.md`, `report-exclusion-spec.md`, `tenant-feature-flags-spec.md`, `tenant-onboarding-import-spec.md`, `postgres-tenant-schemas.md`, `migrazione-opensearch-postgresql.md` | Tolto il contratto inesistente `unavailableAreas`; protezione di `/api/media` descritta come realizzata; tenant attivo nel controllo delle funzionalità; job di onboarding non conclusi esclusi dalla pulizia; tabella dello schema E/R ricomposta |
| Tracce, anteprima, media | `track-pdf-processing-spec.md`, `track-pdf-manual-editing-spec.md`, `track-page-image-editing-spec.md`, `track-parts-workspace-spec.md`, `score-preview-printing-spec.md`, `media-asset-spec.md` | `media-asset-spec.md` riscritto dove superato: categorie reali (`finance`, `score-versions`, `inventory-issue-photos`, `onboarding-imports`), tutte le relazioni, rilevamento del tipo, stati usati, pulizia e migrazione al presente |
| Musica e OMR | `track-score-playback-spec.md`, `omr-text-recognition-spec.md`, `track-instrument-suggestions-spec.md`, `music-writing-editor-spec.md`, `taurus-omr/README.md`, `taurus-omr/THIRD_PARTY_NOTICES.md` | Stato per fasi con date sostituito da «Scelte di sviluppo» e «Requisiti aperti»; etichette del mixer; variabile `TAURUS_OMR_MAX_IMAGE_PIXELS` e restrizioni reali di `omr.yml` nel README |
| Standard e richieste di supporto | `application-errors-standard.md`, `taurus-layout-standard.md`, `support-requests-spec.md` | Standard degli errori al presente, area `scorePlayback`, colonne «Chiave precedente»; Layout Standard con censimento iniziale distinto dallo stato e «Scostamenti noti»; stati HTTP e `errorId` delle richieste di supporto secondo lo standard |
| Runbook, README, governance | `runbook-operativo.md`, `runbook-test.md`, `README.md`, `AGENTS.md`, `taurus-be/README.md`, `taurus-fe/README.md`, `taurus-info/README.md`, `documentation-implementation-alignment-spec.md` | README di backend e frontend riscritti (erano quelli generati da JHipster e Sakai); modulo `taurus-omr`, Mailpit e controlli del catalogo errori nel README principale; due job di conservazione nel runbook; casi `FIN-012`, `REC-006` e `ONB-010` nel runbook di test; versione dei termini nel README del sito; moduli, script e job CI reali nella governance |

### Catalogo

`docs/features.json` è aggiornato e `docs/features.md` rigenerato:

- 13 voci con requisiti aperti passano da `implemented` a `in-progress`: `event-preparation`, `financial-management`, `inventory-qr-code`, `notification-preferences`, `operational-dashboard`, `recurring-calendar-events`, `score-preview-printing`, `tenant-feature-flags`, `tenant-onboarding-import`, `track-page-image-editing`, `track-pdf-manual-editing`, `track-pdf-processing`, `web-push-reminders`;
- note superate corrette: «undici flag», niente più merge da fare per i fogli di affidamento, regressione di `RelationalSoftDeleteIT` già risolta;
- evidenze aggiunte: le due migrazioni del 22 ottobre, quella del 21 ottobre sui flag, l'audit dei feed, il fuso del tenant, i provider della dashboard, il servizio e lo scheduler della conservazione dell'onboarding con il relativo test;
- `lastVerifiedOn` al 2026-10-23 per tutte le voci.

## Verifiche

| Verifica | Esito |
| --- | --- |
| `node scripts/docs/validate-feature-catalog.mjs` | valido, 35 voci |
| `node scripts/docs/generate-feature-index.mjs` | matrice rigenerata |
| `node --test scripts/docs/feature-catalog.test.mjs` e `error-catalog.test.mjs` | 24 test superati |
| `node scripts/docs/check-error-catalog.mjs` | 0 errori, 12 avvisi già noti sui parametri non verificabili |
| Frontend: `npm test -- --watch=false --browsers=ChromeHeadless` | 338 test superati |
| Frontend: `npm run build` | riuscita; avvisi di budget di `preview` e `pdf-manipulator-dialog` |
| Frontend: Prettier sui file modificati | nessuna nuova differenza; i due file del dettaglio evento erano già fuori formato prima delle modifiche |
| Backend: `./mvnw verify` | riuscito in 10 minuti: 659 test unitari e 146 di integrazione superati, nessuno saltato; le due migrazioni nuove eseguite su PostgreSQL |

Questo documento sta in `docs/reports/`, insieme al [report degli interventi](interventi-allineamento-documentazione-codice.md): il validatore controlla solo i file al primo livello di `docs/`, quindi non richiede una voce nel catalogo e non compare in `features.md`.

## Decisioni prese e scelte da confermare

Criteri adottati:

- **Requisiti non realizzati**: restano nella specifica, raccolti in «Requisiti aperti»; le voci di catalogo che ne hanno sono passate a `in-progress`.
- **Standard violati dal codice**: la regola si aggiorna dove il codice l'ha superata di proposito; le violazioni vanno in «Scostamenti noti», senza correggere ora il codice.
- **Frasi temporali di stato**: tolte dalle specifiche; lo stato vive solo nel catalogo.

Scelte da confermare:

- **Serie ricorrenti**: «Salva da questo evento in poi» aggiorna anche le occorrenze passate dalla sorgente in poi, e la sorgente perde il contrassegno di eccezione. È il comportamento esistente, mantenuto e documentato.
- **Tesoriere**: apre il dettaglio evento ma non vede il calendario né la voce di menu; la specifica economica è allineata a questo.
- **«Tenant: rimosso»**: la notifica va al tenant di lavoro del super amministratore; se il super amministratore rimuove proprio quel tenant, la notifica non viene emessa.
- **Conservazione dell'onboarding**: i job `UPLOADED` o `READY` abbandonati non vengono puliti; è registrato come requisito aperto da decidere.
- **Budget degli stili**: `angular.json` porta l'avviso di `anyComponentStyle` a 7 kB, contro i 2 kB del Layout Standard; registrato tra gli scostamenti noti. Va deciso se aggiornare la regola o riportare l'avviso a 2 kB.
- **Dialoghi a piena finestra**: `image-editor-dialog`, `score-editor-dialog` e `score-player-dialog` non usano `dialog-shell`, come `pdf-manipulator-dialog`, già escluso; registrato tra gli scostamenti noti.

Nuovo rilievo sul codice, registrato come requisito aperto e non corretto:

- **Budget del tesoriere sulle occorrenze**: `PATCH /api/finance/events/{id}/budget` non marca l'occorrenza come eccezione, quindi una modifica successiva della serie sovrascrive compenso e costi decisi dal tesoriere.

## Prossimi passi

1. Rivedere il diff complessivo, in particolare migrazioni e configurazione di sicurezza.
2. Verificare a mano i saldi iniziali dei conti creati prima della correzione.
3. Decidere le scelte da confermare e il rilievo sul budget del tesoriere.
4. Commit separati per modulo, su tua richiesta.
