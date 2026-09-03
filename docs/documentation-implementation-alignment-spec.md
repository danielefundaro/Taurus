# Governance e allineamento tra documentazione e implementazione

## Stato del documento

Progettazione proposta. Il documento definisce il modello di governance, i file da introdurre e i controlli automatici, ma non autorizza ancora l'implementazione né modifica lo stato delle funzionalità esistenti.

## Contesto

Taurus contiene specifiche funzionali e tecniche dettagliate, ma non dispone di un indice autorevole che colleghi ogni iniziativa al relativo stato, al codice, alle migrazioni, ai test e alla release che la contiene.

Oggi lo stato è espresso con testo libero all'interno dei documenti. Questo rende possibili situazioni incoerenti, per esempio una specifica che dichiara non avviato lo sviluppo mentre nel repository sono già presenti API, frontend, migrazione Liquibase e test. Inoltre il `README.md` principale contiene soltanto il nome del progetto e non permette di orientarsi tra i quattro moduli.

La soluzione deve essere leggera: il codice resta la fonte autorevole del comportamento, Git e le release restano la fonte autorevole della cronologia, mentre il catalogo documentale rende queste evidenze rintracciabili e verificabili.

## Obiettivi

1. Fornire un punto unico per sapere se una funzionalità è proposta, pianificata, in sviluppo, implementata o rilasciata.
2. Distinguere l'approvazione della progettazione dall'avanzamento dell'implementazione.
3. Collegare ogni funzionalità a specifica, moduli, codice, migrazioni, test e prima release.
4. Rendere evidenti le informazioni mancanti e i riferimenti a file eliminati o rinominati.
5. Integrare l'aggiornamento documentale nel normale flusso di pull request e release.
6. Generare una matrice leggibile senza duplicare manualmente lo stato in più file.
7. Trasformare il `README.md` principale nel punto di ingresso per sviluppo e documentazione.

## Fuori scope

- generare automaticamente specifiche funzionali a partire dal codice;
- stabilire se il comportamento implementato sia semanticamente corretto senza revisione umana;
- introdurre un portale documentale, un database o un servizio esterno;
- sostituire issue tracker, cronologia Git o note di release;
- documentare ogni classe o ogni endpoint nel catalogo;
- bloccare subito le pull request per ogni possibile indizio di deriva semantica.

## Principi approvati

- **Una sola fonte per gli stati**: gli stati correnti vivono esclusivamente in `docs/features.json`.
- **Viste derivate**: `docs/features.md` è generato dal catalogo e non deve essere modificato a mano.
- **Evidenze, non affermazioni**: lo stato implementato richiede riferimenti a file reali e test pertinenti.
- **Stati indipendenti**: una specifica può essere approvata senza che lo sviluppo sia pianificato; una funzionalità può essere implementata senza essere ancora rilasciata.
- **Percorsi stabili e relativi al repository**: tutti i riferimenti usano `/` e non contengono path assoluti, glob o segreti.
- **Aggiornamento atomico**: quando una pull request cambia uno stato, aggiorna nello stesso insieme di modifiche anche catalogo, evidenze e specifica interessata.
- **Adozione graduale**: i controlli strutturali sono bloccanti da subito; i controlli euristici sulla deriva iniziano come avvisi.

## Modello delle informazioni

### File canonico

Il catalogo è `docs/features.json`. JSON è scelto perché:

- può essere validato senza aggiungere un parser YAML al repository;
- consente un contratto di formato esplicito e versionato;
- è leggibile nativamente da Node.js, Java e Python;
- evita che una sintassi permissiva trasformi valori di stato o date.

Il file contiene una versione di formato e una lista ordinata per `id`.

```json
{
  "schemaVersion": 1,
  "entries": [
    {
      "id": "calendar-recurring-events",
      "title": "Eventi ricorrenti del calendario",
      "kind": "feature",
      "designStatus": "approved",
      "deliveryStatus": "implemented",
      "spec": "docs/recurring-calendar-events-spec.md",
      "modules": ["taurus-be", "taurus-fe"],
      "evidence": {
        "implementation": [
          "taurus-be/src/main/java/com/fundaro/zodiac/taurus/web/rest/CalendarEventSeriesResource.java",
          "taurus-fe/src/app/service/calendar-event-series.service.ts"
        ],
        "migrations": [
          "taurus-be/src/main/resources/config/liquibase/changelog/20260831000003_recurring_calendar_events.xml"
        ],
        "tests": [
          "taurus-be/src/test/java/com/fundaro/zodiac/taurus/service/impl/CalendarEventSeriesServiceImplTest.java"
        ]
      },
      "release": null,
      "lastVerifiedOn": "2026-09-03"
    }
  ]
}
```

L'esempio illustra il formato e non costituisce una certificazione dello stato corrente o della presenza in una release.

### Campi

| Campo | Obbligatorio | Regola |
| --- | --- | --- |
| `id` | sì | kebab case, univoco e immutabile |
| `title` | sì | nome utente della funzionalità o iniziativa |
| `kind` | sì | `feature`, `platform`, `migration` oppure `standard` |
| `designStatus` | sì | stato della specifica, indipendente dalla consegna |
| `deliveryStatus` | sì | stato dell'implementazione e della distribuzione |
| `spec` | sì | un solo documento principale esistente |
| `relatedDocs` | no | documenti secondari, senza duplicare `spec` |
| `modules` | sì | uno o più moduli noti del repository |
| `evidence` | sì | riferimenti rappresentativi a implementazione, migrazioni e test |
| `release` | condizionale | obbligatorio quando `deliveryStatus` è `released` |
| `supersededBy` | condizionale | obbligatorio per una voce sostituita |
| `lastVerifiedOn` | sì | data ISO dell'ultima verifica tra catalogo e repository |
| `notes` | no | eccezione breve; non deve diventare una seconda specifica |

I valori ammessi per `modules` sono `taurus-be`, `taurus-fe`, `keycloak-authenticator`, `taurus-info` e `repository`.

### Stato della progettazione

| Stato | Significato | Criterio di ingresso |
| --- | --- | --- |
| `draft` | decisioni ancora in elaborazione | esiste una prima specifica revisionabile |
| `approved` | decisioni approvate e stabili | revisione esplicita completata |
| `superseded` | specifica sostituita | `supersededBy` indica la nuova voce |
| `archived` | documento solo storico | iniziativa abbandonata o rimossa senza sostituzione |

`approved` non significa automaticamente che l'implementazione sia autorizzata o pianificata.

### Stato della consegna

| Stato | Significato | Evidenze minime |
| --- | --- | --- |
| `not-planned` | nessun lavoro autorizzato | specifica presente |
| `planned` | lavoro approvato, non iniziato | ambito e criteri di accettazione definiti |
| `in-progress` | implementazione in corso | almeno un riferimento a codice o migrazione |
| `implemented` | comportamento completo sul ramo di riferimento | codice, test e migrazioni applicabili presenti; verifiche di modulo superate |
| `released` | incluso in una release Taurus | requisiti di `implemented` più versione, data e tag |
| `deprecated` | ancora presente ma destinato alla rimozione | motivazione e sostituzione, se esiste |
| `removed` | non più disponibile | release di rimozione e documentazione storica |

Il ramo di riferimento è il ramo predefinito del repository. La presenza di file in una working tree locale non è sufficiente per dichiarare `implemented`.

### Release

Per una voce rilasciata il campo assume questa forma:

```json
{
  "version": "v2.4.0",
  "date": "2026-09-15",
  "tag": "v2.4.0"
}
```

La versione deve rispettare il formato dei tag già usato dal workflow Docker, `vMAJOR.MINOR.PATCH`. La prima release che contiene la funzionalità resta immutabile; correzioni successive sono descritte nelle note di release e non sovrascrivono questo valore.

## Matrice generata

Lo script `scripts/docs/generate-feature-index.mjs` legge `docs/features.json` e genera `docs/features.md` con queste colonne:

| ID | Funzionalita | Tipo | Progettazione | Consegna | Moduli | Migrazioni | Test | Release | Verificata |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `calendar-recurring-events` | Eventi ricorrenti del calendario | feature | approved | implemented | BE, FE | 1 | 1 | non rilasciata | 2026-09-03 |

Il titolo collega la specifica; i contatori di migrazioni e test collegano sezioni di dettaglio generate sotto la tabella. Il file inizia con un avviso che ne vieta la modifica manuale e riporta il comando per rigenerarlo.

La matrice mostra informazioni sintetiche. Percorsi completi, note ed eventuali relazioni rimangono nel catalogo o nella specifica, evitando una tabella illeggibile.

## Struttura dei documenti di specifica

Ogni specifica gestita dal catalogo mantiene il proprio contenuto, ma il blocco iniziale di stato libero viene sostituito con un riferimento stabile:

```md
## Stato del documento

ID catalogo: `calendar-recurring-events`.
Lo stato corrente è pubblicato in [Catalogo funzionalità](features.md).
```

Il documento può continuare a distinguere decisioni, vincoli e fasi progettuali, ma non deve ripetere frasi temporali come “non ancora sviluppato”, “implementato” o “rilasciato”. Questo impedisce che il testo narrativo contraddica il catalogo.

Le specifiche nuove devono includere almeno:

1. obiettivo e fuori scope;
2. decisioni funzionali e tecniche;
3. autorizzazioni e isolamento tenant, quando applicabili;
4. modello dati e migrazioni, quando applicabili;
5. API e interfaccia utente, quando applicabili;
6. osservabilità e sicurezza;
7. strategia di test;
8. criteri di accettazione;
9. piano di adozione o compatibilità.

## README principale

Il `README.md` alla radice diventa la pagina di orientamento, senza duplicare i README dei moduli. La struttura prevista è:

1. descrizione di Taurus in due o tre righe;
2. tabella dei moduli con scopo e collegamento al README locale;
3. prerequisiti comuni: Java 17, Node.js 22, pnpm 11 e Docker;
4. avvio rapido di dipendenze, backend e frontend;
5. comandi di verifica per ogni modulo;
6. collegamenti al catalogo funzionalità, alle specifiche e ai documenti architetturali;
7. regole essenziali per pull request, migrazioni e sicurezza;
8. processo di release e significato dei tag.

Il README non espone credenziali, valori di ambiente reali o configurazioni di produzione.

## Flusso di lavoro

### Nuova funzionalità

1. Creare la specifica e la voce di catalogo con `designStatus: draft` e `deliveryStatus: not-planned`.
2. Aggiornare `designStatus` a `approved` dopo la revisione delle decisioni.
3. Passare a `planned` solo quando il lavoro è autorizzato e ha criteri di accettazione.
4. Passare a `in-progress` nella prima pull request di implementazione e aggiungere le prime evidenze.
5. Passare a `implemented` nella pull request che completa il comportamento e le verifiche.
6. Passare a `released` nella preparazione della release, indicando il tag che sara applicato allo stesso commit di release.

### Modifica di una funzionalità esistente

Una modifica compatibile aggiorna specifica, evidenze e `lastVerifiedOn` senza creare un nuovo ID. Una sostituzione sostanziale crea invece una nuova voce; la precedente passa a `superseded` o `deprecated` e la relazione viene esplicitata.

### Correzione di un bug

Una correzione che non cambia il contratto non modifica lo stato della progettazione. Aggiorna `lastVerifiedOn` solo se tocca una delle evidenze registrate o dimostra che la specifica era inesatta. La release della correzione resta nelle note di release, non nel campo della prima release della funzionalità.

### Migrazioni Liquibase

Una migrazione elencata come evidenza deve:

- esistere nel repository;
- essere inclusa dal master changelog corretto;
- essere associata a una voce il cui modulo comprende `taurus-be`;
- avere almeno un test di integrazione o una motivazione esplicita in `notes`;
- non essere rimossa dopo il rilascio; eventuali correzioni usano una nuova migration.

## Pull request

Viene introdotto `.github/pull_request_template.md` con una sezione obbligatoria:

```md
## Documentazione e tracciabilità

- Feature ID interessati: <!-- nessuno oppure elenco -->
- [ ] Specifica e catalogo sono aggiornati oppure la modifica non cambia il contratto
- [ ] Migrazioni/configurazione/compatibilità sono dichiarate
- [ ] I comandi di verifica eseguiti sono elencati
- [ ] La matrice generata è allineata al catalogo
```

La dicitura “nessuno” è valida per manutenzione non funzionale, ma deve essere una scelta esplicita.

## Controlli automatici

### Validatore

Lo script `scripts/docs/validate-feature-catalog.mjs`, eseguito con Node.js 22, non richiede dipendenze esterne e verifica:

1. JSON valido e `schemaVersion` supportata;
2. campi obbligatori, enum e formato di ID, date e versioni;
3. ID univoci e ordinamento deterministico;
4. esistenza di specifiche, documenti correlati ed evidenze;
5. moduli ammessi e coerenti con i prefissi dei percorsi;
6. assenza di path assoluti, `..`, URL con credenziali e valori sensibili;
7. requisiti minimi di evidenza per ciascuno stato;
8. presenza della migrazione nel master changelog Liquibase corretto;
9. presenza dei dati di release quando richiesti;
10. validità delle relazioni `supersededBy` e assenza di cicli.

Il validatore non considera il nome di un file come prova semantica che la funzionalità sia completa.

### Generatore

Il comando:

```text
node scripts/docs/generate-feature-index.mjs
```

produce sempre lo stesso output a parità di catalogo. In CI il generatore viene eseguito e la pipeline fallisce se `docs/features.md` cambia, segnalando che la vista versionata non è stata rigenerata.

### Workflow CI

Si aggiunge `.github/workflows/verify.yml`, attivo su pull request e push al ramo predefinito, con job separati:

- `documentation`: valida catalogo e matrice;
- `backend`: esegue `./mvnw verify` in `taurus-be` quando cambia il modulo o una sua specifica;
- `frontend`: esegue `npm ci`, `npm test -- --watch=false --browsers=ChromeHeadless` e `npm run build` in `taurus-fe` quando cambia il modulo o una sua specifica;
- `keycloak`: esegue `./mvnw verify` in `keycloak-authenticator` quando necessario;
- `info-site`: esegue `pnpm install --frozen-lockfile`, `pnpm check` e `pnpm build` in `taurus-info` quando necessario.

I filtri per percorso sono un'ottimizzazione: il job `documentation` viene sempre eseguito.

### Segnalazione della possibile deriva

Un controllo aggiuntivo confronta i file modificati dalla pull request con le evidenze registrate. Se cambia un'evidenza ma non la relativa voce di catalogo, il job pubblica inizialmente un avviso nel riepilogo CI. Non blocca la pull request perché un refactoring o un bug fix possono non cambiare lo stato.

Dopo un periodo di adozione, l'avviso può diventare bloccante solo per questi casi oggettivi:

- evidenza eliminata o rinominata senza aggiornare il catalogo;
- modifica di una migrazione già associata a una release;
- specifica eliminata senza stato `superseded` o `archived`;
- voce `implemented` priva di test;
- voce `released` priva di tag valido.

## Integrazione con le release

Il workflow Docker esistente continua ad attivarsi sui tag `v*.*.*`. Prima della build delle immagini viene aggiunto un controllo documentale che:

1. valida il catalogo;
2. verifica che il tag corrente sia semanticamente valido;
3. controlla che le voci preparate per quella release indichino lo stesso tag;
4. produce nel riepilogo del job l'elenco delle funzionalità che hanno quella versione come prima release.

La preparazione della release aggiorna il catalogo prima di creare il tag; il tag viene applicato al medesimo commit. Se il controllo fallisce, le immagini non vengono pubblicate.

Le correzioni, le modifiche infrastrutturali e le altre informazioni non rappresentate come funzionalità continuano a essere riportate nelle release Git. Il catalogo non sostituisce le note di release.

## Riconciliazione iniziale

La prima adozione non deve dedurre automaticamente `released` dalla sola presenza del codice. La riconciliazione avviene in questo ordine:

1. censire le specifiche in `docs/`, escludendo documentazione di terze parti come `llms-full.md`;
2. assegnare ID stabili e tipo a ogni iniziativa Taurus;
3. verificare separatamente stato della progettazione e stato della consegna;
4. raccogliere poche evidenze rappresentative per backend, frontend, migrazioni e test;
5. dichiarare al massimo `implemented` quando non esiste una prova affidabile della prima release;
6. usare `released` solo quando un tag o una release permette di dimostrarlo;
7. sostituire nei documenti i blocchi di stato temporali con il collegamento al catalogo;
8. generare la prima matrice e revisionarla manualmente;
9. aggiornare il README principale;
10. attivare prima i controlli strutturali e successivamente gli avvisi di deriva.

La riconciliazione iniziale deve includere almeno gestione economica, inventario, media, notifiche, eventi ricorrenti, promemoria push, standard di layout, migrazione PostgreSQL e le nuove specifiche ancora non pianificate.

## Struttura prevista del repository

```text
README.md
docs/
  features.json
  features.md                    # generato
  documentation-implementation-alignment-spec.md
  ...specifiche esistenti...
scripts/
  docs/
    generate-feature-index.mjs
    validate-feature-catalog.mjs
    feature-catalog-lib.mjs
    feature-catalog.test.mjs
.github/
  pull_request_template.md
  workflows/
    verify.yml
    docker-publish.yml            # esteso con il controllo pre-release
```

La logica condivisa tra validatore e generatore vive in `feature-catalog-lib.mjs`; non viene duplicata nei workflow.

## Gestione degli errori

Gli script terminano con codice diverso da zero e messaggi nel formato:

```text
docs/features.json: entries[3].evidence.tests: "...Test.java" does not exist
```

Ogni errore indica file, posizione logica, valore problematico e correzione attesa. Gli errori vengono raccolti e mostrati insieme, con un limite ragionevole, invece di interrompersi al primo problema.

Il generatore scrive su un file temporaneo e sostituisce `docs/features.md` solo dopo una generazione completa, evitando output parziali.

## Sicurezza

- Il catalogo non contiene credenziali, hostname privati, token o path assoluti di deployment.
- Le evidenze puntano soltanto a file versionati nel repository.
- I link esterni ammessi nelle note devono usare HTTPS e non sono considerati evidenze di implementazione.
- Il validatore non esegue comandi o importa moduli indicati dal catalogo; tratta ogni valore come dato non fidato.
- La generazione Markdown applica escaping a pipe, markup HTML e link per impedire contenuto iniettato nella vista.
- Le modifiche a documentazione di autenticazione, tenant, keystore e migrazioni restano soggette a revisione di sicurezza.

## Prestazioni e manutenibilità

Il catalogo previsto contiene decine, non migliaia, di voci. Validazione e generazione devono completarsi in meno di due secondi su una workstation ordinaria e leggere ogni file una sola volta.

Le evidenze sono rappresentative: per una funzionalità non si elenca ogni DTO o componente. Si preferiscono endpoint o service principale, entry point frontend, migrazione e test ad alto valore. Questo mantiene il catalogo leggibile e riduce il costo dei refactoring.

## Strategia di test

### Test unitari degli script

- catalogo minimo valido;
- ID duplicato o non conforme;
- stato sconosciuto;
- file mancante e path non sicuro;
- requisiti diversi per `not-planned`, `implemented` e `released`;
- release con versione o data invalida;
- relazione a ID inesistente e ciclo di sostituzione;
- migrazione non inclusa nel master changelog;
- escaping del contenuto Markdown;
- ordinamento e output deterministico.

### Test di integrazione CI

- modifica del catalogo senza rigenerare la matrice;
- rinomina di un'evidenza senza aggiornare il catalogo;
- pull request che tocca codice senza feature ID, gestita come avviso;
- tag di release non coerente con il catalogo;
- esecuzione selettiva delle verifiche dei quattro moduli.

### Verifica manuale iniziale

Per ogni voce una revisione umana controlla a campione che:

- la specifica descriva il comportamento osservabile;
- le evidenze conducano realmente al flusso principale;
- i test coprano il contratto dichiarato;
- lo stato non sia dedotto soltanto dal nome dei file;
- la release indicata contenga effettivamente l'implementazione.

## Piano di implementazione

### Fase 1 — Fondazioni

1. Introdurre libreria, validatore, generatore e relativi test.
2. Definire `docs/features.json` e generare `docs/features.md`.
3. Censire le specifiche senza modificare ancora i loro contenuti.

### Fase 2 — Riconciliazione

1. Verificare le evidenze voce per voce.
2. Correggere gli stati contraddittori.
3. Sostituire i blocchi temporali nelle specifiche con il riferimento al catalogo.
4. Segnalare come `implemented`, non `released`, i casi privi di prova della release.

### Fase 3 — Accesso e processo

1. Completare il README principale.
2. Aggiungere il template di pull request.
3. Documentare i comandi di validazione nei README dei moduli solo dove necessario.

### Fase 4 — CI e release

1. Aggiungere `verify.yml` con il job documentale sempre attivo.
2. Collegare le verifiche di modulo ai percorsi interessati.
3. Inserire il preflight documentale nel workflow Docker.
4. Pubblicare gli avvisi non bloccanti di possibile deriva.

### Fase 5 — Irrigidimento controllato

Dopo almeno due cicli di release, misurare falsi positivi e costo di manutenzione. Rendere bloccanti soltanto i casi oggettivi elencati nella sezione CI; mantenere gli indizi semantici come avvisi.

## Compatibilità e rollback

L'introduzione del catalogo non modifica API, database, immagini Docker o comportamento utente. Può essere distribuita indipendentemente dai moduli applicativi.

In caso di problemi con la CI, i controlli euristici possono essere disattivati lasciando attivi validazione strutturale e generazione. Il catalogo e la matrice non devono essere rimossi: restano utili anche senza gating automatico.

Un errore di stato viene corretto con una normale pull request; non si riscrive la cronologia Git e non si modifica retroattivamente un tag.

## Criteri di accettazione

La progettazione è considerata implementata quando:

1. esiste un catalogo JSON valido con tutte le iniziative Taurus censite;
2. ogni ID è univoco, stabile e collegato a una specifica esistente;
3. progettazione e consegna usano stati separati e il significato è documentato;
4. ogni voce `implemented` contiene codice e test verificabili;
5. ogni migrazione elencata è inclusa dal corretto master changelog;
6. ogni voce `released` indica versione, data e tag coerenti;
7. la matrice Markdown è generata deterministicamente dal catalogo;
8. la CI fallisce se catalogo o matrice sono strutturalmente incoerenti;
9. la CI segnala, senza bloccare nella prima fase, le evidenze cambiate senza aggiornamento del catalogo;
10. il workflow di release esegue il controllo prima di pubblicare le immagini;
11. le specifiche non contengono più dichiarazioni temporali di stato in conflitto con il catalogo;
12. il README principale permette a un nuovo collaboratore di individuare moduli, comandi e documentazione;
13. il template di pull request richiede feature ID, impatti e verifiche;
14. nessun file di governance contiene segreti o path dipendenti dall'ambiente;
15. validatore, generatore e casi di errore principali dispongono di test automatici.

## Decisioni finali

- `docs/features.json` è la fonte autorevole degli stati.
- `docs/features.md` è una vista generata e versionata.
- stato della progettazione e stato della consegna rimangono separati.
- l'evidenza di release è un tag semantico; la presenza del codice non basta.
- Node.js 22 è usato per gli script senza dipendenze esterne.
- i controlli strutturali sono bloccanti; gli indizi semantici partono come avvisi.
- il catalogo collega poche evidenze rappresentative e non tenta di inventariare tutto il codice.
- il README principale diventa l'indice del repository, senza duplicare i dettagli dei moduli.
