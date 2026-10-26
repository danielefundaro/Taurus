# Sviluppi in corso e ordine di chiusura

Ricognizione del 2026-10-24 delle voci con `deliveryStatus: in-progress` nel [Catalogo funzionalità](../features.md).

Per ogni voce sono riassunti gli interventi che mancano per arrivare a `implemented`, cioè i «Requisiti aperti» della specifica (per i due runbook, i gap elencati nei rispettivi documenti). L'elenco è ordinato per difficoltà crescente, con una sola eccezione: una voce propedeutica precede sempre quelle che dipendono da essa, anche quando è più difficile. Il caso principale è il [Runbook di test](#7-test-runbook), che introduce la suite end to end e le integrazioni reali richieste dai requisiti aperti di molte funzionalità.

La difficoltà è stimata sul lavoro residuo — ampiezza, moduli toccati e dipendenze esterne — non sulla dimensione della funzionalità già realizzata.

## Quadro d'insieme

| # | ID | Moduli | Requisiti aperti | Difficoltà | Vincoli di ordine |
| ---: | --- | --- | ---: | --- | --- |
| 1 | `web-push-reminders` | BE, FE | 2 | Molto bassa | Propedeutico a `notification-preferences` |
| 2 | `tenant-feature-flags` | BE, FE, Keycloak | 4 | Bassa | — |
| 3 | `track-pdf-manual-editing` | BE, FE | 2 | Bassa | — |
| 4 | `inventory-loan-sheet` | BE, FE | 2 | Bassa (collaudo) | — |
| 5 | `track-score-playback` | BE, FE, OMR | 3 | Media (collaudo) | — |
| 6 | `inventory-electronic-signature` | BE, FE, Keycloak | 6 | Media (dipendenze esterne) | — |
| 7 | `test-runbook` | Repository | 16 gap | Alta | **Propedeutico** a 8–18 |
| 8 | `track-pdf-processing` | BE, FE | 6 | Media | Dipende da 7 |
| 9 | `track-page-image-editing` | BE, FE | 6 | Media | Dipende da 7 |
| 10 | `operational-dashboard` | BE, FE | 6 | Media | Dipende da 7; propedeutico a 15 |
| 11 | `recurring-calendar-events` | BE, FE | 11 | Medio-alta | Dipende da 7 |
| 12 | `track-parts-workspace` | BE, FE | 12 | Medio-alta | Dipende da 7 |
| 13 | `external-calendar-feed` | BE, FE | 14 | Alta | Dipende da 7 |
| 14 | `notification-preferences` | BE, FE | 16 | Alta | Dipende da 1 e 7 |
| 15 | `financial-management` | BE, FE | 15 | Alta | Dipende da 7 |
| 16 | `event-preparation` | BE, FE | 18 | Alta | Dipende da 7 e 10 |
| 17 | `inventory-qr-code` | BE, FE | 19 | Alta | Dipende da 7 |
| 18 | `tenant-onboarding-import` | BE, FE | 23 | Molto alta | Dipende da 7 |
| 19 | `operational-runbook` | Repository | 15 gap | Molto alta | Propedeutico a ogni `released` |

La tabella resta la fotografia del 2026-10-24: i conteggi e le difficoltà sono quelli di allora, e le voci chiuse da quel giorno lo dichiarano in coda alla propria sezione. Al 2026-10-27 restano `in-progress` le voci 6, 7, 8, 9, 10, 12, 13, 16, 17, 18 e 19; sono chiuse le voci 1, 2, 3, 4, 5, 11, 14 e 15.

## 1. `web-push-reminders`

[Promemoria eventi tramite Web Push](../web-push-reminders-spec.md) — difficoltà molto bassa.

- Correggere l'anticipo del promemoria: la preferenza legacy `defaultReminderMinutes` deve essere quella del destinatario, non di chi esegue l'operazione.
- Aggiungere le spec frontend di `push-notification.service` e `app-update.service`.

Due interventi circoscritti, un difetto e una copertura mancante. Va chiuso per primo anche perché la fase 5 di `notification-preferences` rimuove la lettura legacy: correggere prima evita di propagare l'anticipo sbagliato ai job già pianificati.

> Chiuso il 2026-10-25: la voce è passata a `implemented`.

## 2. `tenant-feature-flags`

[Funzionalità configurabili per tenant](../tenant-feature-flags-spec.md) — difficoltà bassa.

- Raggruppare le checkbox in **Moduli principali** ed **Estensioni** nel dettaglio tenant.
- Spiegare, con il kill switch globale spento, che il flag salvato è un'abilitazione pianificata.
- Disattivando una funzionalità di una pagina condivisa, rimuovere la sola sezione interessata invece di navigare a `/`.
- Elencare nel log le funzionalità figlie il cui stato effettivo cambia per dipendenza.

Tre interventi di interfaccia e uno di logging, tutti locali e già descritti in dettaglio nella specifica.

> Chiuso il 2026-10-25: la voce è passata a `implemented`.

## 3. `track-pdf-manual-editing`

[Editor manuale delle pagine PDF](../track-pdf-manual-editing-spec.md) — difficoltà bassa.

- Allineare l'anteprima al backend per contrasto automatico e toni calcolati per zona.
- Validare le annotazioni all'upload, con errore sincrono per JSON malformato o ricetta fuori dai limiti.

Pochi interventi ma non banali: l'allineamento dell'anteprima richiede di replicare nel client la stessa pipeline del backend, quindi va verificato su scansioni reali.

> Chiuso il 2026-10-25: la voce è passata a `implemented`.

## 4. `inventory-loan-sheet`

[Foglio di consegna e affidamento in comodato](../inventory-loan-sheet-spec.md) — difficoltà bassa, è solo collaudo.

- Verifica manuale dell'interfaccia con un tenant di prova: emissione, variazioni, chiusura, definizione e caricamento di un modello modificato.
- Stampa A4 del foglio generato con il modello predefinito, a confronto con la stampa del modello Word.

Nessuno sviluppo residuo: serve una sessione di collaudo su un tenant di prova e una stampa di confronto.

> Chiuso il 2026-10-25: collaudo eseguito, la voce è passata a `implemented`.

## 5. `track-score-playback`

[Ascolto e correzione delle parti](../track-score-playback-spec.md) — difficoltà media, è collaudo con dipendenze.

- Prova a mano del percorso completo su un tenant di prova, ruoli utente ed esterno compresi: attivazione, strumento configurato con **Prova**, MusicXML multi-parte, ascolto, correzione e segnalazione.
- Casi `PLY-009` (telefono di fascia media, con la memoria occupata dal SoundFont) e `PLY-018` (parti reali della banda: digitali, scansioni e foto) del [Runbook di test](../runbook-test.md).
- Verifica con un archivista dei valori del catalogo di riferimento.

Anche qui non manca codice, ma servono un telefono di fascia media, parti reali e il tempo di un archivista: va pianificato, non improvvisato.

> Chiuso il 2026-10-25: collaudo eseguito, la voce è passata a `implemented`.

## 6. `inventory-electronic-signature`

[Firma elettronica del foglio di affidamento](../inventory-electronic-signature-spec.md) — difficoltà media, dominata da dipendenze esterne.

- Firma con una CIE reale (CieSign su Android e iOS, Software CIE su PC) e verifica in Acrobat di firma e sigillo, casi SIG-031–033.
- Configurazione dei certificati reali delle CA della CIE come trust anchor in un'installazione.
- Invio delle e-mail dal server SMTP di una casella reale dell'associazione.
- Stampa A4 del modello predefinito a confronto con il modello dell'associazione.
- Conferma di un legale sui primi tre punti.
- Testo della notifica di emissione che citi anche l'app di autenticazione e CieSign, in linea con i testi dell'interfaccia.

Un solo intervento di prodotto (il testo della notifica); tutto il resto è verifica con strumenti e persone reali, incluso un parere legale. Il tempo di attesa è il vero costo.

> Il 2026-10-26 è stato chiuso l'unico intervento di prodotto, il testo della notifica di emissione. La voce resta `in-progress` per le cinque verifiche esterne, che nessuna modifica al codice può chiudere: CIE reale, trust anchor delle CA, casella SMTP dell'associazione, stampa A4 di confronto e parere legale.

## 7. `test-runbook`

[Runbook di test e qualificazione](../runbook-test.md) — difficoltà alta. **Propedeutico alle voci 8–18.**

- Introdurre una suite end to end browser full-stack con ruoli e tenant multipli.
- Testare il provider Keycloak contro un Keycloak reale, flow e mapper compresi.
- Coprire sistematicamente l'isolamento tenant con test parametrizzati CRUD/IDOR per risorsa.
- Aggiungere Testcontainers RabbitMQ con casi di redelivery e outage.
- Definire baseline e gate di coverage, security scan in CI (SAST, SCA, segreti, container) e DAST su staging.
- Definire carichi e strumento ripetibile per performance e SLO, automazione di backup/restore, qualificazione degli scheduler multi-replica.
- Gate di accessibilità, matrice browser/device, pipeline che fallisce quando Docker manca, gestione dei test flaky.

Va prima delle funzionalità che seguono perché quasi tutte hanno fra i requisiti aperti test end to end, integrazioni con PostgreSQL e RabbitMQ reali o verifiche di accessibilità e prestazioni: senza questa infrastruttura quei requisiti non sono chiudibili. Non serve completarlo tutto in un colpo: bastano, come primo lotto, il runner end to end, Testcontainers RabbitMQ e i test parametrizzati di isolamento tenant.

> Primo lotto chiuso il 2026-10-25: `TenantIsolationIT`, `RabbitMqBrokerIT` e la suite Playwright in `taurus-fe/e2e`. La voce resta `in-progress` per gli altri gap; la suite end to end attende la prima esecuzione su un'istanza reale.

> Secondo lotto, stesso giorno: `TenantRoleFlowIT` copre browser flow e mapper su un Keycloak reale. Restano soglie di coverage, security scan, performance, backup/restore, scheduler multi-replica e i gap di priorità media.

> Il 2026-10-26 affrontato il resto: soglie di copertura, job `security` in CI, Docker imposto in pipeline, controlli del sito costruito, matrice browser, gate di accessibilità, script k6, prova di ripristino e `UploadJobConcurrencyIT`. La voce resta `in-progress` per le decisioni e le esecuzioni che richiedono ambienti reali, elencate nei gap del runbook.

## 8. `track-pdf-processing`

[Elaborazione asincrona dei PDF](../track-pdf-processing-spec.md) — difficoltà media.

- Rilevare i job `TO_PROCESS` e `IN_PROGRESS` oltre una soglia operativa e definire il recupero (nuovo accodamento o chiusura in `ERROR`), anche dopo l'arresto del worker.
- Metriche Micrometer su anzianità, errori, durata e coda.
- Test del worker fino a `DONE`, del retry rifiutato per stato incompatibile e per accesso cross-tenant.
- Test di integrazione con PostgreSQL e RabbitMQ reali.
- Test frontend delle etichette, del job del `202` e dell'azione «Riprova».

L'integrazione con RabbitMQ reale arriva dal lotto 7; il resto è lavoro contenuto e ben delimitato.

## 9. `track-page-image-editing`

[Modifica e analisi delle immagini delle parti](../track-page-image-editing-spec.md) — difficoltà media.

- Riusare la stessa `Idempotency-Key` nei nuovi tentativi e offrire «Riprova» nel dialogo, così che una risposta persa ottenga il replay.
- Timeout controllato del rendering con risposta `503`.
- Metriche e log di analisi e salvataggio.
- Test backend di concorrenza, replay e conflitto idempotente, compensazione storage, ruoli e isolamento tenant.
- Test di integrazione con input malevolo e timeout controllato.
- Collaudo prestazionale su scansioni reali anonimizzate.

## 10. `operational-dashboard`

[Dashboard operativa trasversale](../operational-dashboard-spec.md) — difficoltà media. **Propedeutica a `event-preparation`.**

- Filtro dell'elenco calendario per `attention=event-preparation` ed `attention=event-follow-up`: oggi `attention` accetta solo i due valori sulle disponibilità.
- Evento fra sette e quattordici giorni con il solo avviso sulle disponibilità, oggi assente sia dalla preparazione sia dalle risposte mancanti.
- Verifica all'avvio che `inventory-warning-days` non superi l'orizzonte inventario.
- Invalidazione dello snapshot al cambio tenant, se il frontend lo consentirà nella sessione.
- Test dei provider Economia, Legale e Notifiche, integration test PostgreSQL, security test e test di cambio tenant.
- Test end to end per ogni profilo, accessibilità, responsive, misura di `p95` e query plan su dataset realistico.

I primi due punti sono gli stessi che `event-preparation` elenca fra i propri requisiti aperti: farli qui li chiude in entrambe le voci.

## 11. `recurring-calendar-events`

[Eventi ricorrenti del calendario](../recurring-calendar-events-spec.md) — difficoltà medio-alta.

- Restituire nel `PATCH` i conteggi delle occorrenze conservate come eccezioni e di quelle ignorate perché passate.
- Segnalare le eccezioni che non appartengono più alla nuova regola e avvisare, prima del salvataggio, di quante non verranno aggiornate.
- Confermare il numero di occorrenze coinvolte prima di una disponibilità bulk che sovrascrive risposte esistenti.
- Idempotenza della creazione: chiave dedicata o conflitto riconoscibile per una richiesta ripetuta dopo timeout.
- Osservabilità di serie, occorrenze, operazioni massive, conflitti di versione e promemoria ripianificati.
- Form: riepilogo testuale della regola, anteprima delle date, evidenza dei mesi saltati, messaggio sul limite di occorrenze.
- Indicatore visivo distinto per le eccezioni in calendario e lista.
- Escludere dalle eccezioni le modifiche di compenso e costi fatte con `PATCH /api/finance/events/{id}/budget`.
- Test di integrazione multi-tenant, autorizzativi e di concorrenza.

> Chiuso il 2026-10-26: la voce è passata a `implemented`. Il `PATCH` riporta le occorrenze conservate, ignorate e non più appartenenti alla regola, le modifiche di compenso e costi non creano più eccezioni e sono arrivati osservabilità, anteprima della regola nel form e i test di integrazione.

## 12. `track-parts-workspace`

[Workspace per la gestione delle parti](../track-parts-workspace-spec.md) — difficoltà medio-alta, quasi tutta frontend.

- Banner «Nuove parti disponibili» con «Ricarica e continua» o «Salva prima»; senza polling va riprogettato.
- Confronto fra bozza e dati server dopo un `409` e conferma prima di refresh o chiusura scheda con bozza modificata.
- Indicatore «Tutte le modifiche salvate» per la bozza sincronizzata.
- Zona «Aggiungi» e drop zone per le parti senza pagine; trascinamento di pagine fra parti con spazi di inserimento e indicatore di posizione.
- Selezione automatica della scheda Parti dopo un'azione di organizzazione.
- Tastiera: frecce sull'elenco parti, Maiusc più frecce per la selezione contigua, `Escape` gestito dal workspace.
- Divisore ridimensionabile fra i pannelli e virtualizzazione dell'elenco per raccolte molto grandi.
- Telemetria UX e gate end to end e prestazionali con 200 parti e 1.000 pagine.

Il drag and drop accessibile fra parti e la gestione della bozza concorrente sono la parte costosa.

## 13. `external-calendar-feed`

[Feed calendario esterno in sola lettura](../external-calendar-feed-spec.md) — difficoltà alta.

- Decidere se mantenere l'esclusione degli ID numerici: l'URL del livello `STANDARD` contiene `calendar_event.id`.
- Revoca proattiva dei feed personali alla cancellazione o disattivazione del proprietario e alla disattivazione del tenant.
- Invito a ruotare il link se esposto, e nell'elenco amministrativo creatore, data di creazione, tipo e proprietario.
- Pulizia periodica delle ricevute di idempotenza e aggiornamento best effort di `last_accessed_at` che non faccia fallire una risposta corretta.
- Audit storico di creazione, rotazione e revoca; metriche, log con correlation ID, alert e controllo delle righe globali orfane.
- Test backend su visibilità per ruolo e stato, `DRAFT`, UID e `SEQUENCE`, tombstone, finestra temporale, ETag e `304`, lifecycle con PostgreSQL reale, revoca concorrente, cancellazione GDPR e query count; test frontend su ruoli, rotazione, assenza del token nello stato persistito e accessibilità.
- Collaudo manuale su Google Calendar, Apple Calendar e Outlook e verifica della redazione del token su reverse proxy, WAF, APM e analytics.
- Rilascio progressivo: pilota con feed personali `MINIMAL`, poi feed tenant e `STANDARD`, infine tutti i tenant, con alert e runbook di revoca.

C'è anche una decisione di progetto da sciogliere (il primo punto) prima di toccare il formato degli URL.

## 14. `notification-preferences`

[Preferenze notifiche granulari](../notification-preferences-spec.md) — difficoltà alta. **Dipende dalla voce 1.**

- Ripianificazione: annullare e ricreare solo i job che ereditano il default, senza toccare override personali o di evento né i `FAILED`; con `eventRemindersEnabled = false` non ricreare i job `PERSONAL` ed `EVENT`; `schedule_revision` monotono ed elaborazione a batch.
- Migrazione: ricostruire l'origine dei job `push_reminders` esistenti e marcare `SKIPPED` i pendenti già scaduti invece di inviarli in ritardo.
- Testi: congiunzione «e» prima dell'ultima categoria nel riepilogo giornaliero e singolare con un solo aggiornamento; con privacy `FULL` il promemoria riporta nome e anticipo dell'evento; **Domani mattina** calcolato nella timezone del profilo.
- Dispatcher push unico, con lock per job anche su `push_reminders` e transazioni per job.
- Nessun endpoint push e nessuno user ID nei log; lettura delle tre retention dei job push dalla configurazione.
- Etichette in `OPERATION_LABELS` per atti di firma, rotazione QR, segnalazioni inventario, preparazione evento e `ASSIGNMENT_EXPIRATION_*`.
- Test frontend della pagina profilo e di `notification-preferences.service`.
- Fase 5: rimozione della lettura legacy `defaultReminderMinutes` e del booleano `push_reminders.sent`, soft delete delle copie in `preferences`, verifica finale GDPR, retention e assenza di segreti nei log.

La fase 5 chiude anche il debito legacy toccato dalla voce 1, quindi va dopo.

> Chiuso il 2026-10-26: la voce è passata a `implemented`. Ripianificazione selettiva con `schedule_revision` monotono, migrazione dell'origine dei job e chiusura `SKIPPED` dei pendenti scaduti, dispatcher push unico con lock per job, log senza endpoint e identificativi, testi del riepilogo e dei promemoria, spec della pagina profilo e fase 5 compresa la rimozione del legacy.

## 15. `financial-management`

[Gestione economica, cassa e conti correnti](../financial-management-spec.md) — difficoltà alta.

- Sicurezza allegati: verifica del tipo reale del contenuto oltre al MIME dichiarato e download consentito solo se il movimento proprietario è attivo.
- Concorrenza: `requestKey` anche per i trasferimenti, con invio dalla UI, e versione inviata dal client nelle modifiche.
- Filtri dei movimenti per esercizio, natura, controparte ed evento, e filtri economici su `GET /api/finance/events`.
- Segnalazione informativa di trasferimenti incoerenti e anomalie annuali; `Aggiorna riporto` efficace anche su un esercizio già `ROLLED_OVER`.
- Dashboard Economia con posizioni evento da incassare o pagare, stato dell'ultimo riporto e scelta del periodo.
- Scheda Eventi con dettaglio movimenti e azioni «Registra incasso» e «Registra spesa»; pagina evento con compenso e costi previsti, margine, risultato effettivo ed elenco movimenti.
- Avvisi su conto sotto zero, ricalcolo delle aperture successive e allegati mancanti; ordinamento di conti e categorie dall'interfaccia; saldo iniziale con descrizione e allegato propri.
- Registro cassa per conto, esportazione dell'intero risultato filtrato e rendiconto per evento con scostamenti.
- Log e metriche del modulo; test di integrazione PostgreSQL, sicurezza, frontend e riporto annuale, multi-tenant, concorrenza e retry compresi.

Il grosso è interfaccia e rendicontazione, ma tocca denaro: i controlli di concorrenza e sicurezza degli allegati vanno fatti prima della parte visibile.

> Chiuso il 2026-10-27: la voce è passata a `implemented`. Nell'ordine previsto, prima le difese — firma del contenuto degli allegati, download negato per un movimento eliminato, versione dal client e `requestKey` anche sui trasferimenti — poi la parte visibile: filtri dei movimenti e degli eventi, registro cassa per conto con progressivo, rendiconto del singolo evento, segnalazioni dell'esercizio, riporto rigenerabile, riordino di conti e categorie, dashboard per periodo e avvisi dell'interfaccia. Aggiunti `FinanceMetrics`, `FinanceServiceGuardsTest`, `FinanceIT` su PostgreSQL e le spec di pagina e dialoghi del frontend.

## 16. `event-preparation`

[Preparazione operativa dell'evento](../event-preparation-spec.md) — difficoltà alta. **Dipende dalla voce 10.**

- Criticità: collegamento alla sezione che le risolve, con azione filtrata per ruolo, `relatedId` e dettaglio per riga; limite delle criticità restituite.
- Profili: scelta esplicita senza valore proposto per le aree a decisione esplicita; va inoltre deciso se un'area richiesta ma disabilitata per il tenant debba produrre `UNKNOWN` (criterio 27).
- Spartiti: avviso sulle alternative in revisione, controllo di copertura strumentale e filtro per gli strumenti dell'utente nelle viste personali.
- Materiali: `BLOCKER` immediato per materiale senza assegnazione o con oggetto `TO_REPAIR`/`OUT_OF_SERVICE`, blocco per guasti `UNSAFE` aperti, collegamento al dettaglio inventario con numero inventariale e quantità residua.
- Preventivo: valuta nell'hash e hash su JSON canonico con versione di schema; chiusura economica basata sullo stato `SETTLED` della gestione economica invece del calcolo locale.
- Programma: indicatore di bis, snapshot storico della traccia, selettore paginato delle singole tracce, drag and drop accessibile, apertura traccia, espansione per spartiti e strumenti, download autorizzato e messaggio per tracce non più visibili.
- Concorrenza: versione e `409` anche per programma e materiali, oggi previsti solo per la configurazione; limiti di 100 voci programma e 100 righe materiale.
- Notifiche: conferma materiale invalidata e notifica materiali con oggetto e quantità.
- Frontend: scomposizione del dettaglio (oggi un monolite da circa 970 righe) in componenti per area, con navigazione ad ancore, azione «Mostra criticità» e gestione del focus.
- Dashboard: i due punti già elencati nella voce 10.
- Osservabilità: cinque metriche Micrometer a cardinalità limitata e log strutturati; oggi lo scheduler registra solo l'errore di elaborazione di un tenant.
- Test backend, frontend ed end to end mancanti, accessibilità e prestazioni comprese.

Sono almeno quattro lotti indipendenti: dominio e valutazione backend, concorrenza e limiti, refactor e interfaccia del dettaglio Angular, osservabilità e test.

## 17. `inventory-qr-code`

[QR code ed etichette per l'inventario](../inventory-qr-code-spec.md) — difficoltà alta.

- Schermata di scansione: azioni amministrative (assegna, gestisci riconsegna, fotografia dell'oggetto, rotazione del QR), stato di conservazione e avvisi di scadenza e presa visione, azioni personali (presa visione, richiesta di riconsegna, foto di riconsegna, segnalazione).
- Apertura diretta della consegna quando l'assegnazione pertinente è una sola e uso di `allowedActions` per tutte le azioni, non solo `REPORT_ISSUE`.
- Foto dei guasti lato utente, con anteprima e rimozione prima dell'invio, e rimozione o oscuramento delle foto con dati personali nella procedura di erasure.
- Notifiche: quantità nella creazione del guasto, esclusione dell'attore dai destinatari, presa in carico al solo segnalante, motivazione nella rotazione, `eventKey` comprensivi del destinatario.
- Sette metriche Micrometer a cardinalità limitata e log strutturati di stampa, rotazione, guasti e risoluzioni fallite; timeout di generazione PDF a 30 secondi con `503` controllato.
- Test backend mancanti (inclusa la decodifica automatica di un QR estratto dal PDF), test frontend di scansione, etichette, rotazione e guasti, test end to end e di sicurezza, documentazione operativa per stampa e sostituzione delle etichette.

## 18. `tenant-onboarding-import`

[Onboarding guidato e importazione iniziale](../tenant-onboarding-import-spec.md) — difficoltà molto alta.

- Robustezza del file: lettura XLSX in streaming SAX con limiti per cella e sul numero di celle, rilevamento del MIME reale, rifiuto di relazioni esterne e DDE, codice `SOURCE_INTEGRITY_FAILURE` per file non corrispondente al digest.
- Validazione: preflight completo all'avvio con verifica dell'hash e ripetizione dei controlli dinamici, `OPENING_BALANCE_NOT_ALLOWED` per i conti con movimenti, colonna valorizzata per ogni problema di formato, `VALUE_INVALID_ENUM` per tutti gli enum, avviso `KEYCLOAK_IDENTITY_WILL_BE_LINKED`.
- Limiti: `max-issues` con problema globale `TOO_MANY_ISSUES` e massimo di 3 job per utente in caricamento o validazione.
- Concorrenza della saga: selezione dei job con `FOR UPDATE SKIP LOCKED`, ripresa dei soli job fermi da un tempo minimo, advisory lock PostgreSQL, identità Keycloak elaborate con concorrenza configurabile.
- Notifica unica di riepilogo nell'outbox con `BulkOperationContext`; metriche `taurus.onboarding.*` e alert su `COMPENSATION_REQUIRED`, job bloccati e fallimenti.
- Frontend: template con righe di esempio e legenda, invito a cambiare istanza, **Ultima importazione** nella checklist, esito per sezione con durata e collegamenti condizionati ai moduli attivi, elenco problemi paginato e accessibile, vista compatta sotto 768 pixel, polling adattivo con `ETag`, riuso della stessa `Idempotency-Key` nei tentativi.
- Va inoltre decisa una scadenza per i job `UPLOADED` e `READY` abbandonati, che oggi conservano file e dati personali senza limite.
- Test di sicurezza, frontend ed end to end, carico da 5.000 righe e simulazione di riavvio in ogni fase della saga.

È la voce più costosa: tocca parsing di file ostili, una saga distribuita con compensazione e Keycloak, e una procedura di prova non banale.

## 19. `operational-runbook`

[Runbook operativo](../runbook-operativo.md) — difficoltà molto alta, è lavoro infrastrutturale.

Bloccanti: manifest di produzione validato, storage persistente e backup per PostgreSQL, `environment.prod.ts` utilizzabile da un browser esterno, client OIDC `web-app`/`web_app` uniformato fra realm, FE e BE, Keycloak in production mode con hostname, proxy, TLS e tuning del database.

Alta priorità: sostituire `app.yml` obsoleto, estendere CI/CD con pubblicazione, firma e provenienza degli artefatti, governare gli scheduler multi-replica, definire e provare RPO, RTO e retention.

Media priorità: logging di produzione strutturato e correlato, Actuator ristretto, porte RabbitMQ non esposte, smoke test automatici non distruttivi, servizio OMR nei manifest di produzione con chiave dal secret manager e immagine per digest, copia controllata del SoundFont oggi scaricato da `ftp.osuosl.org`.

Non blocca il passaggio a `implemented` delle altre voci, ma blocca ogni `released`: senza questi punti non esiste un ambiente di produzione qualificato in cui rilasciarle.

## Note di metodo

- I conteggi della tabella sono il numero di voci elencate sotto «Requisiti aperti» di ogni specifica, o di righe della tabella dei gap per i due runbook; non misurano l'effort, che è stimato nella colonna «Difficoltà».
- La propedeuticità del [Runbook di test](#7-test-runbook) riguarda il primo lotto (runner end to end, Testcontainers RabbitMQ, test parametrizzati di isolamento tenant): il resto dei suoi gap può procedere in parallelo alle funzionalità.
- Alcune voci contengono decisioni ancora da prendere, non solo sviluppo: criterio 27 in `event-preparation`, ID numerici negli URL in `external-calendar-feed`, scadenza dei job abbandonati in `tenant-onboarding-import`. Vanno sciolte prima di aprire il lotto che le riguarda.
- Il passaggio a `implemented` richiede, per ogni voce, codice, test e migrazioni applicabili sul ramo di riferimento e le verifiche di modulo superate, come stabilito dalla [governance di allineamento](../documentation-implementation-alignment-spec.md).
