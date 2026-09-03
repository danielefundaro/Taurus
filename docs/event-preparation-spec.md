# Preparazione operativa dell'evento

La funzionalità estende il dettaglio calendario esistente e coordina catalogo musicale, spartiti, disponibilità, presenze, inventario e gestione economica. Le regole economiche rimangono quelle di [Gestione economica tenant](financial-management-spec.md), le assegnazioni materiali quelle di [Gestione inventario tenant](inventory-management-spec.md) e la visibilità delle occorrenze quelle di [Eventi ricorrenti del calendario](recurring-calendar-events-spec.md).

## Stato del documento

Specifica funzionale e tecnica proposta. Il documento definisce il comportamento atteso e le decisioni necessarie a una futura implementazione, ma non autorizza ancora lo sviluppo.

## Obiettivo

Trasformare la pagina dell'evento in una cabina di regia unica, capace di rispondere in modo immediato a queste domande:

- l'evento è pianificato e pubblicato correttamente?
- il programma musicale è definito?
- gli spartiti necessari sono disponibili e visibili ai musicisti corretti?
- le disponibilità raccolte sono sufficienti?
- strumenti e materiali necessari sono assegnati e pronti?
- preventivo e consuntivo economico sono aggiornati?
- dopo l'evento, presenze e posizione economica sono stati verificati?

La testata mostra un esito sintetico come **Evento pronto**, **Da verificare — 3 criticità** oppure **Da chiudere — 2 attività**. Ogni criticità è spiegabile e collega direttamente alla sezione o al modulo in cui risolverla.

## Principio fondamentale: nessuno stato “pronto” manuale

Taurus non salva un booleano `ready` e non permette di dichiarare conclusa una criticità lasciando incoerente il dato di dominio.

Lo stato è una proiezione calcolata di:

- dati e visibilità dell'evento;
- programma musicale collegato;
- stato di tracce, spartiti e media;
- risposte di disponibilità;
- materiale pianificato e relative assegnazioni inventario;
- preventivo, movimenti e riconciliazione economica;
- conferma del registro presenze.

Sono persistite soltanto le decisioni che il sistema non può dedurre, per esempio se il programma sia richiesto per quell'evento, il numero minimo di partecipanti, la conferma intenzionale di un preventivo pari a zero o l'avvenuta verifica delle presenze.

Questa distinzione evita una checklist parallela che possa risultare “completa” mentre mancano spartiti, risposte o materiale.

## Relazione con lo stato dell'evento

`calendar_event.state` conserva il significato attuale:

- `DRAFT`: visibilità amministrativa;
- `COMPLETE`: evento completo visibile agli utenti interni autorizzati;
- `PUBLIC`: evento visibile anche agli utenti esterni.

Questi valori non diventano stati di lavorazione. Un evento `PUBLIC` può non essere pronto; un evento `DRAFT` può avere programma e materiali completi ma non essere ancora pubblicato.

## Ambito della prima versione

La prima versione comprende:

1. configurazione delle verifiche applicabili all'evento;
2. programma musicale ordinato, con riuso delle tracce esistenti;
3. controllo di disponibilità e visibilità degli spartiti;
4. riepilogo disponibilità e soglia minima di partecipanti;
5. pianificazione del materiale tramite oggetti e assegnazioni inventario esistenti;
6. conferma di preparazione del materiale;
7. preventivo, consuntivo e conferme esplicite per i casi a zero;
8. conferma del registro presenze dopo l'evento;
9. stato derivato, percentuale di completezza e criticità azionabili;
10. viste differenziate per ruolo nello stesso percorso frontend;
11. supporto alle singole occorrenze di serie ricorrenti;
12. notifiche mirate e integrazione futura con la dashboard operativa.

## Fuori scope

- inviti nominali o convocazioni per evento;
- assegnazione di parti musicali a una specifica persona;
- turni, ruoli di palco, trasporti o pernottamenti;
- prenotazione di quantità inventario non ancora assegnate;
- gestione di fornitori, acquisti o noleggi;
- creazione automatica di movimenti economici;
- modifica massiva della preparazione di tutte le occorrenze di una serie;
- congelamento legale o contabile dell'evento;
- chat, commenti liberi o checklist manuali generiche;
- generazione di un dossier PDF o ZIP dell'evento;
- sincronizzazione offline.

## Profili di preparazione

Alla prima configurazione un admin o super admin sceglie un profilo, che propone valori iniziali modificabili:

| Profilo | Luogo | Programma | Spartiti | Disponibilità | Materiali | Preventivo | Presenze | Chiusura economica |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `PERFORMANCE` | sì | sì | sì | sì | decisione esplicita | sì | sì | sì |
| `REHEARSAL` | decisione esplicita | sì | sì | sì | decisione esplicita | no | facoltativa | no |
| `OTHER` | decisione esplicita | no | no | decisione esplicita | no | no | no | no |

“Decisione esplicita” significa che il dialogo non propone silenziosamente un valore: l'amministratore deve scegliere se l'area è richiesta.

Dopo la creazione del piano, ogni requisito può essere modificato singolarmente. Il profilo resta informativo e non sovrascrive più le personalizzazioni.

Vincoli:

- spartiti richiesti implica programma richiesto;
- materiali richiesti implica almeno una riga materiale prima di poter risultare pronto;
- disponibilità richiesta implica `minimumAvailableParticipants >= 1`;
- la scadenza risposte è espressa in minuti prima dell'inizio, da 0 a 43.200;
- la scadenza preparazione materiali è espressa nello stesso modo;
- presenze e chiusura economica sono verifiche successive all'evento e non riducono la prontezza precedente all'inizio.

## Fasi temporali derivate

| Fase | Regola | Indicatore principale |
| --- | --- | --- |
| `PREPARATION` | istante corrente precedente a `startDate` | prontezza operativa |
| `IN_PROGRESS` | tra `startDate` ed `endDate` | evento in corso e criticità residue |
| `FOLLOW_UP` | successivo a `endDate` | chiusura presenze, materiali ed economia |

`endDate` è già obbligatoria nel modello persistente. Un dato storico corrotto o non valutabile produce `UNKNOWN`: il sistema non inventa una durata convenzionale.

Eventi cancellati logicamente o occorrenze escluse non espongono la preparazione. La timezone è quella già risolta per l'evento e il tenant; tutti i confronti temporali avvengono backend-side.

## Stati derivati

### Prima dell'evento

| Stato | Significato |
| --- | --- |
| `NOT_CONFIGURED` | il piano di preparazione non esiste |
| `BLOCKED` | almeno una verifica obbligatoria bloccante non è soddisfatta |
| `ATTENTION` | nessun blocco, ma esistono avvisi operativi |
| `READY` | tutte le verifiche obbligatorie sono soddisfatte e non ci sono avvisi |
| `UNKNOWN` | la valutazione non è stata completata in modo affidabile |

### Dopo l'evento

| Stato | Significato |
| --- | --- |
| `TO_CLOSE` | almeno una verifica successiva richiesta è aperta |
| `CLOSED_WITH_WARNINGS` | tutte le verifiche richieste sono soddisfatte, ma restano avvisi non bloccanti |
| `CLOSED` | tutte le verifiche successive applicabili sono soddisfatte e non restano avvisi |
| `NOT_REQUIRED` | il piano non richiede verifiche successive |
| `UNKNOWN` | la valutazione non è affidabile |

Lo stato non viene persistito. Qualunque cambiamento ai dati autorevoli si riflette alla lettura successiva.

## Percentuale di completezza

La percentuale è calcolata soltanto sulle verifiche applicabili:

```text
completionPercent = round(verifiche superate / verifiche applicabili × 100)
```

Ogni verifica pesa uno. La percentuale è un aiuto di navigazione, non sostituisce la severità: un evento al 90% con un singolo blocco critico resta `BLOCKED`.

Una sezione complessa non produce artificialmente decine di punti. Le verifiche di primo livello sono:

1. dati e visibilità;
2. programma;
3. spartiti;
4. disponibilità;
5. materiali;
6. preventivo;
7. presenze, solo in `FOLLOW_UP`;
8. chiusura economica, solo in `FOLLOW_UP`.

Le anomalie di dettaglio sono elencate sotto la relativa verifica ma non alterano il denominatore.

## Modello delle criticità

Ogni criticità restituita dal backend contiene:

```json
{
  "code": "SCORE_NOT_READY",
  "area": "SCORES",
  "severity": "BLOCKER",
  "title": "Spartito non disponibile",
  "description": "Marcia n. 1 non dispone di uno spartito pronto.",
  "relatedId": 84,
  "action": {
    "label": "Apri la traccia",
    "path": "/tracks/84"
  }
}
```

Valori di severità:

- `BLOCKER`: impedisce `READY` o `CLOSED`;
- `WARNING`: produce `ATTENTION` ma non blocca operazioni;
- `INFO`: informazione utile, esclusa dal conteggio delle criticità principali.

Il catalogo dei codici è stabile; titolo e descrizione sono presentazione localizzata e non devono essere interpretati dal frontend.

`action.path` appartiene a un'allowlist backend per codice e ruolo, inizia con una sola `/` e non contiene URL esterni. Se il ruolo non può correggere il problema, l'azione è omessa.

## Regole di valutazione

### Dati e visibilità

Verifiche sempre applicabili:

- nome non vuoto;
- `startDate` presente;
- `endDate` successiva a `startDate`, quando presente;
- luogo presente se `locationRequired`;
- stato diverso da `DRAFT` per poter risultare `READY`.

Un evento `DRAFT` produce `WARNING` quando mancano più di sette giorni e `BLOCKER` negli ultimi sette giorni. Date invalide o mancanti sono sempre bloccanti.

### Programma musicale

Se richiesto:

- deve esistere almeno una voce attiva;
- l'ordine deve essere continuo e univoco;
- ogni riferimento deve appartenere al tenant e puntare a una traccia non cancellata;
- una traccia `DRAFT` è bloccante;
- per un evento `PUBLIC`, una traccia non `PUBLIC` è bloccante perché l'utente esterno non potrebbe consultarla;
- per un evento `COMPLETE`, sono ammesse tracce `COMPLETE` o `PUBLIC`;
- la somma delle durate pianificate, quando tutte presenti, produce un avviso se supera la durata dell'evento;
- la stessa traccia può comparire più volte, per esempio come bis, e ogni occorrenza mantiene ordine e note proprie.

### Spartiti

Se richiesti, per ogni voce di programma:

1. deve esistere almeno uno spartito non cancellato;
2. almeno uno spartito deve avere `needsReview = false`;
3. ogni spartito considerato pronto deve avere almeno un `media_asset` in stato `READY`;
4. uno spartito senza strumenti associati non è distribuibile tramite il filtro personale esistente e produce un blocco;
5. media `MIGRATION_PENDING`, `PROCESSING`, `FAILED`, `INVALID` o `DELETED` non soddisfano la verifica;
6. la visibilità della traccia deve essere compatibile con quella dell'evento.

Se una traccia ha almeno uno spartito pronto ma anche alternative `needsReview`, le alternative generano un avviso e non un blocco.

Il controllo di copertura strumentale confronta gli strumenti degli spartiti pronti con gli strumenti degli utenti disponibili. Un'assenza produce un avviso, non una prova di organico insufficiente: senza convocazioni nominali, una persona con più strumenti potrebbe essere conteggiata in più sezioni.

### Disponibilità

Il pubblico atteso usa la stessa regola deterministica della dashboard operativa finché non esisteranno inviti espliciti:

- evento `COMPLETE`: utenti attivi con ruolo partecipante interno;
- evento `PUBLIC`: utenti attivi interni ed esterni;
- un admin o archivista è incluso soltanto se possiede anche un ruolo partecipante;
- `DRAFT` non ha un pubblico atteso valutabile.

Se la disponibilità è richiesta:

- `availableCount` deve raggiungere `minimumAvailableParticipants`;
- prima della scadenza, soglia non raggiunta e risposte mancanti sono `WARNING`;
- dalla scadenza, soglia non raggiunta è `BLOCKER`;
- dalla scadenza, almeno una risposta mancante è `BLOCKER`;
- indisponibilità esplicite non sono criticità autonome, ma concorrono ai conteggi;
- utenti disattivati non appartengono al pubblico atteso;
- la disponibilità dell'utente corrente continua a essere un'operazione immediata separata dal salvataggio pagina.

La scadenza predefinita proposta dai profili è 1.440 minuti prima dell'inizio. Può essere modificata dall'amministratore.

### Materiali

Se richiesti:

- deve esistere almeno una riga materiale;
- ogni riga deve riferirsi a un oggetto inventario attivo;
- `requiredQuantity` deve essere maggiore di zero;
- prima di risultare pronta, la riga deve essere collegata a un'assegnazione inventario attiva o parzialmente riconsegnata;
- assegnazione, oggetto e responsabile devono essere coerenti;
- la quantità residua assegnata deve essere almeno pari a quella richiesta;
- oggetti `TO_REPAIR` o `OUT_OF_SERVICE` sono bloccanti;
- un guasto `UNSAFE` aperto, se la specifica QR/guasti è implementata, è bloccante;
- la preparazione materiale deve essere confermata sullo snapshot corrente.

Una conferma materiale salva l'hash canonico di item, assegnazione, quantità residua, condizione e quantità richiesta. Se uno di questi dati cambia, la conferma non è più corrente e la riga torna da verificare.

Una riga non confermata è `WARNING` prima della relativa scadenza e `BLOCKER` dopo. La scadenza predefinita proposta è 180 minuti prima dell'evento.

La prima versione non riserva stock libero. Un materiale senza assegnazione può essere pianificato, ma resta bloccante: l'amministratore deve creare una normale assegnazione inventario e collegarla. In questo modo quantità, responsabilità, riconsegne e prese visione rimangono governate dall'inventario senza una seconda contabilità di magazzino.

### Preventivo

Se richiesto, il preventivo deve essere confermato da admin, super admin o tesoriere.

La conferma salva un hash canonico di:

- compenso previsto;
- valuta applicabile;
- descrizione, importo e ordine delle voci di costo.

Qualunque modifica successiva invalida la conferma. Un preventivo interamente a zero è valido soltanto se confermato esplicitamente; l'assenza di valori non può essere interpretata automaticamente come “nessun costo previsto”.

La mancata conferma è bloccante per `READY`. La conferma non rende immutabile il preventivo e non limita i permessi economici esistenti.

### Presenze dopo l'evento

Se richieste, le presenze sono complete quando admin o super admin confermano il registro corrente.

La conferma salva un hash canonico di utenti presenti, orari di arrivo, note e ordine. Qualunque modifica successiva invalida la conferma. È possibile confermare esplicitamente un registro con zero presenze.

Il salvataggio delle presenze e la conferma restano due operazioni distinte. La conferma non viene inclusa nel salvataggio generale dell'evento e rispetta le unità di modifica indipendenti definite dallo standard di layout.

### Chiusura economica dopo l'evento

Se richiesta, la verifica è soddisfatta quando:

- lo stato economico derivato è `SETTLED`; oppure
- non sono attesi né presenti movimenti e un utente economico autorizzato conferma esplicitamente “nessun movimento previsto”.

La seconda decisione salva un hash di preventivo e movimenti correnti con motivazione facoltativa. L'aggiunta, modifica o eliminazione di un movimento oppure la modifica del preventivo la invalida.

`OVERPAID_OR_OVERRUN`, `PARTIALLY_SETTLED`, `NO_MOVEMENTS` e `UNPLANNED_MOVEMENTS` mantengono `TO_CLOSE`. `NO_BUDGET` richiede la conferma esplicita se la chiusura economica è applicabile.

La riconciliazione dei movimenti resta informativa secondo la specifica economica; movimenti non riconciliati generano un `WARNING` ma non impediscono `CLOSED` quando la posizione è saldata.

## Programma musicale

### Modello dati

Tabella `calendar_event_program_entry`:

| Campo | Regola |
| --- | --- |
| `id` | chiave primaria |
| `event_id` | evento tenant, `ON DELETE CASCADE` |
| `track_id` | traccia tenant, riferimento mantenuto finché possibile |
| `display_order` | ordine univoco tra righe attive dell'evento |
| `planned_duration_seconds` | facoltativo, da 1 a 86.400 |
| `performance_notes` | facoltative, massimo 2.000 caratteri |
| `encore` | booleano, default `false` |
| snapshot traccia | nome, sottotitolo, compositore e arrangiatore al momento dell'inserimento |
| audit/versione | campi tenant audit e concorrenza ottimistica |

Non esiste un vincolo univoco su `(event_id, track_id)`: una traccia può ricomparire. L'ordine usa un indice univoco parziale sulle righe non cancellate.

Gli snapshot mantengono leggibile il programma storico se la traccia viene rinominata o cancellata logicamente. Prima dell'evento, valutazione e link usano comunque la traccia corrente; dopo l'evento la UI mostra lo snapshot con l'indicazione “catalogo aggiornato” se differisce.

### Gestione

Admin, super admin e archivista possono:

- cercare tracce del tenant;
- aggiungerle al programma;
- riordinarle con drag and drop o comandi accessibili “sposta su/giù”;
- indicare durata, bis e note esecutive;
- aprire la traccia per correggere spartiti o metadati;
- salvare l'intero programma come unità atomica.

Il client invia ID, ordine e campi specifici dell'evento. Titoli, autori, stato e spartiti sono riletti dal backend e non sono accettati come dati autorevoli dal client.

Limite: massimo 100 voci attive per evento.

### Fruizione degli spartiti

- Admin, super admin e archivista vedono tutti gli spartiti autorizzati dal catalogo amministrativo.
- L'utente interno vede soltanto tracce `COMPLETE` o `PUBLIC` e, per ciascuna, gli spartiti associati a uno dei propri strumenti.
- L'utente esterno vede soltanto eventi e tracce `PUBLIC`, applicando lo stesso filtro strumentale.
- Il tesoriere non riceve programma o media salvo possedere un altro ruolo nella sessione.
- Lo streaming continua a usare gli endpoint media esistenti; il DTO preparazione non contiene path filesystem o URL firmati persistenti.

La pagina offre download singolo. Il pacchetto ZIP di tutti gli spartiti è rinviato perché richiede limiti, autorizzazione file-per-file e gestione di documenti voluminosi.

## Materiali dell'evento

### Modello dati

Tabella `calendar_event_material`:

| Campo | Regola |
| --- | --- |
| `id` | chiave primaria |
| `event_id` | evento tenant |
| `inventory_item_id` | oggetto inventario tenant |
| `inventory_assignment_id` | assegnazione facoltativa finché la riga è pianificata |
| `required_quantity` | maggiore di zero |
| `purpose` | facoltativo, massimo 1.000 caratteri |
| `display_order` | ordine univoco tra righe attive |
| `prepared_hash` | hash dello snapshot confermato, facoltativo |
| `prepared_at/by` | data e autore della conferma corrente |
| audit/versione | soft delete e concorrenza ottimistica |

Quando l'assegnazione è valorizzata, il responsabile deriva dall'assegnatario e non viene duplicato. La foreign key usa `RESTRICT`; le cancellazioni applicative restano logiche.

La stessa assegnazione può servire più eventi. Questa relazione non modifica quantità o scadenza e non equivale a una prenotazione esclusiva.

### Flusso

1. L'amministratore aggiunge un oggetto e la quantità richiesta.
2. Può collegare subito un'assegnazione compatibile oppure lasciare la riga da assegnare.
3. Il deep link apre il dettaglio inventario per creare o correggere l'assegnazione.
4. Dopo la verifica fisica, l'amministratore conferma “Materiale pronto”.
5. Taurus salva l'hash dello snapshot corrente.
6. Variazioni a quantità, assegnazione, residuo o condizione rendono la conferma obsoleta.

Non vengono create automaticamente assegnazioni o riconsegne. Questo preserva conferme, notifiche, foto e regole di concorrenza già presenti nell'inventario.

## Configurazione persistita

Tabella `calendar_event_preparation` in relazione uno-a-uno con `calendar_event`:

| Campo | Tipo/Regola |
| --- | --- |
| `event_id` | PK e FK all'evento |
| `profile` | `PERFORMANCE`, `REHEARSAL`, `OTHER` |
| `location_required` | booleano |
| `program_required` | booleano |
| `scores_required` | booleano |
| `availability_required` | booleano |
| `minimum_available_participants` | obbligatorio e positivo se richiesto |
| `availability_deadline_minutes` | 0–43.200 |
| `materials_required` | booleano |
| `materials_deadline_minutes` | 0–43.200 |
| `budget_required` | booleano |
| `presence_closure_required` | booleano |
| `financial_closure_required` | booleano |
| `budget_confirmation_hash/at/by` | conferma preventivo corrente |
| `presence_confirmation_hash/at/by` | conferma registro presenze corrente |
| `no_movements_confirmation_hash/at/by` | conferma economica a zero |
| audit/versione | tenant audit e lock ottimistico |

Gli hash sono SHA-256 di JSON canonico con schema versionato. Non sono firme elettroniche e non attestano identità oltre all'audit autenticato già presente.

Il piano viene creato soltanto per eventi già persistiti. Un evento nuovo viene prima salvato; la pagina propone subito dopo la configurazione guidata.

## Eventi ricorrenti

Preparazione, programma, materiali e conferme appartengono alla singola occorrenza. Non fanno parte del template di serie perché disponibilità, organico, materiale, presenze e consuntivo possono cambiare per data.

Regole:

- configurare la preparazione non imposta `series_exception`;
- una modifica del template di serie non sovrascrive il piano dell'occorrenza;
- la cancellazione di un'occorrenza elimina logicamente o rende non accessibili i relativi dati di preparazione secondo le normali regole di audit;
- le nuove occorrenze non ereditano automaticamente il piano nella prima versione;
- copia verso occorrenze future e template di preparazione sono evoluzioni rinviate.

## Ruoli e autorizzazioni

| Operazione | Super Admin | Admin | Tesoriere | Archivista | Utente | Utente esterno |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Vedere indicatore completo | sì | sì | solo economia | programma e catalogo | vista personale | solo evento `PUBLIC` |
| Configurare requisiti | sì | sì | no | no | no | no |
| Gestire programma | sì | sì | no | sì | no | no |
| Vedere tutti gli spartiti | sì | sì | no | sì | no | no |
| Vedere spartiti per i propri strumenti | se partecipante | se partecipante | no | se partecipante | sì | sì, su tracce `PUBLIC` |
| Vedere risposte nominative | sì | sì | no | no | no | no |
| Modificare la propria disponibilità | secondo ruolo partecipante | secondo ruolo partecipante | no | sì | sì | sì su evento `PUBLIC` |
| Gestire materiali | sì | sì | no | no | no | no |
| Gestire preventivo e consuntivo | sì | sì | sì | no | no | no |
| Gestire e confermare presenze | sì | sì | no | no | no | no |

La sessione corrente continua a usare un solo ruolo effettivo. I dati restituiti dipendono dal ruolo nel token; la pagina non carica sezioni non autorizzate per poi nasconderle con CSS.

## API REST

### Vista amministrativa

```text
GET /api/calendar-events/{eventId}/preparation
PUT /api/calendar-events/{eventId}/preparation/configuration
PUT /api/calendar-events/{eventId}/preparation/program
PUT /api/calendar-events/{eventId}/preparation/materials
POST /api/calendar-events/{eventId}/preparation/materials/{materialId}/confirm
POST /api/calendar-events/{eventId}/preparation/presence-confirmation
```

La lettura completa e materiali/presenze sono riservati ad admin e super admin. Il `PUT` del programma è autorizzato anche per l'archivista tramite matcher esplicito posizionato prima della regola generale del calendario.

### Vista archivista

```text
GET /api/calendar-events/{eventId}/preparation/catalogue
PUT /api/calendar-events/{eventId}/preparation/program
```

La risposta contiene dati evento minimi, programma e diagnostica spartiti. Non contiene nominativi, materiali assegnati o importi.

### Vista personale

```text
GET /api/user/calendar-events/{eventId}/preparation
GET /api/external/calendar-events/{eventId}/preparation
```

Le risposte applicano rispettivamente visibilità `COMPLETE/PUBLIC` e `PUBLIC`, filtrano spartiti per gli strumenti dell'utente e contengono soltanto la propria disponibilità. Non espongono punteggi economici, altri utenti o assegnazioni inventario.

### Vista economica

```text
GET /api/finance/events/{eventId}/preparation
POST /api/finance/events/{eventId}/budget-confirmation
POST /api/finance/events/{eventId}/no-movements-confirmation
```

È disponibile ad admin, super admin e tesoriere. Riusa il riepilogo economico esistente e non concede accesso alle API generiche di modifica evento.

### Ricerca per selettori

Programma e materiali usano endpoint paginati esistenti. Non caricano liste complete nel browser. Le query rispettano tenant, soft delete, stato e ruolo.

## Contratto della vista completa

Risposta esemplificativa abbreviata:

```json
{
  "event": {
    "id": 42,
    "name": "Concerto d'autunno",
    "state": "COMPLETE",
    "startDate": "2026-10-10T20:30:00+02:00",
    "endDate": "2026-10-10T22:30:00+02:00",
    "location": "Teatro comunale"
  },
  "evaluation": {
    "evaluatedAt": "2026-10-08T10:00:00+02:00",
    "phase": "PREPARATION",
    "preparationStatus": "BLOCKED",
    "closureStatus": "NOT_REQUIRED",
    "completionPercent": 67,
    "passedChecks": 4,
    "applicableChecks": 6,
    "blockerCount": 1,
    "warningCount": 2,
    "issues": [
      {
        "code": "SCORE_NOT_READY",
        "area": "SCORES",
        "severity": "BLOCKER"
      }
    ]
  },
  "configuration": {},
  "program": [],
  "availability": {
    "expected": 45,
    "available": 30,
    "unavailable": 5,
    "missing": 10,
    "minimumRequired": 28,
    "deadline": "2026-10-09T20:30:00+02:00"
  },
  "materials": [],
  "economics": {},
  "presence": {},
  "unavailableAreas": []
}
```

Le collezioni vuote e i conteggi a zero sono distinti da un'area non disponibile. Se una feature opzionale non è installata o abilitata, l'area compare in `unavailableAreas`, la valutazione diventa `UNKNOWN` soltanto quando quell'area è obbligatoria.

L'endpoint usa `Cache-Control: no-store`. Non accetta tenant, ruolo, user ID o timezone dal client.

## Comandi e concorrenza

Ogni area è una propria unità di salvataggio:

- configurazione;
- programma;
- materiali;
- dati generali evento;
- presenze;
- preventivo;
- singola disponibilità personale.

I comandi includono la versione dell'aggregato modificato e restituiscono `409 Conflict` in caso di modifica concorrente. Il salvataggio di una sezione non ricarica o scarta modifiche pendenti nelle altre.

Il programma viene sostituito atomicamente in una transazione, dopo validazione di tutte le tracce. I materiali seguono la stessa regola. Una riga non valida fa fallire l'intero comando.

Le conferme acquisiscono lo snapshot e calcolano l'hash dentro la stessa transazione di lettura bloccante necessaria; il client non invia l'hash da confermare.

## Architettura backend

### Aggregatore

```text
EventPreparationResource
          |
EventPreparationService
          |
          +-- CorePreparationEvaluator
          +-- ProgramPreparationEvaluator
          +-- AvailabilityPreparationEvaluator
          +-- InventoryPreparationEvaluator
          +-- FinancePreparationEvaluator
          +-- FollowUpPreparationEvaluator
```

Ogni evaluator restituisce verifiche e criticità tipizzate. `EventPreparationService` applica fase, severità, conteggi e percentuale. Gli evaluator non salvano stato derivato.

Non vengono eseguite chiamate HTTP interne tra moduli. Tutti i dati appartengono allo stesso schema tenant e vengono letti tramite repository o servizi di dominio mirati. Le regole autorevoli di finanza e inventario restano nei relativi service; l'aggregatore usa proiezioni read-only e non ne ricopia le formule.

### Componenti previsti

- entità `CalendarEventPreparation`, `CalendarEventProgramEntry` e `CalendarEventMaterial`;
- repository con query aggregate e fetch mirati;
- `EventPreparationHashService` per JSON canonico versionato;
- evaluator stateless per area;
- DTO distinti per amministratore, archivista, utente, esterno e tesoriere;
- mapper che costruiscono per allowlist, senza serializzare un DTO completo per poi cancellare campi;
- estensione dei servizi evento, inventario e finanza per invalidare semanticamente le conferme tramite hash corrente.

### Errori e disponibilità parziale

Errori di database, tenant o autorizzazione fanno fallire l'intera richiesta con RFC 7807; non vengono mascherati come zero criticità.

`unavailableAreas` è usato solo quando una feature è esplicitamente disabilitata o non ancora installata in un rollout compatibile. Non cattura eccezioni impreviste. Se l'area è richiesta, lo stato è `UNKNOWN` e non `READY`.

## Migrazione Liquibase

Una migration tenant crea:

1. `calendar_event_preparation` con PK/FK su evento e vincoli dei requisiti;
2. `calendar_event_program_entry` con snapshot e indice univoco parziale sull'ordine attivo;
3. `calendar_event_material` con riferimenti a evento, item e assegnazione;
4. indici su evento, traccia, item, assegnazione e righe attive;
5. check constraint per durate, quantità, scadenze e coerenza minima dei flag;
6. campi audit e `entity_version` coerenti con le tabelle tenant;
7. inclusione nel `tenant-master.xml`.

Non viene effettuato backfill dei piani: gli eventi esistenti rimangono `NOT_CONFIGURED`. Non vengono creati programma, materiali o conferme deducendoli da descrizioni libere.

Le nuove tabelle non sono indicizzate in OpenSearch. Il dettaglio e la valutazione usano PostgreSQL; la ricerca di tracce continua a usare i meccanismi catalogo esistenti.

## Frontend Angular

### Struttura della pagina

La rotta canonica resta `/calendar/:id`, preservando link, dashboard e notifiche esistenti. `DetailComponent` diventa un contenitore leggero che orchestra componenti per area:

```text
event-detail/
  event-detail.component
  preparation-summary/
  event-core-section/
  event-program-section/
  event-availability-section/
  event-materials-section/
  event-economics-section/
  event-presence-section/
```

La pagina non viene trasformata in un unico form. Ogni componente registra la propria unità sporca tramite `DetailPageBase`, mantenendo la protezione da perdita dati già introdotta nello standard di layout.

### Testata e navigazione

La testata mostra:

- nome, data, luogo e indicazione di serie;
- tag di fase;
- stato `Evento pronto`, `Da verificare`, `Bloccato`, `Evento in corso`, `Da chiudere`, `Chiuso con avvisi` o `Chiuso`;
- percentuale e conteggio criticità;
- azione “Mostra criticità”.

Sotto la testata una navigazione ad ancore elenca le sole sezioni visibili al ruolo. Ogni voce mostra stato testuale e conteggio. Su mobile diventa un selettore compatto; nessuna sezione viene nascosta soltanto tramite CSS dopo averne caricato i dati.

### Riepilogo criticità

Il riepilogo usa `InlineAlertComponent`, `ListRowComponent`, `p-tag` e `p-button`. Ordine:

1. blocker;
2. warning;
3. area secondo il flusso operativo;
4. codice stabile.

Una criticità collega alla sezione nella pagina o a un percorso autorizzato come traccia, inventario o finanza. Il click sposta il focus sul titolo della destinazione interna.

### Programma

- elenco ordinato con titolo, autore, durata, bis e stato spartiti;
- selettore paginato delle tracce;
- riordinamento accessibile anche senza drag and drop;
- espansione di una riga per spartiti e strumenti;
- download singolo autorizzato;
- salvataggio esplicito dell'intero programma;
- messaggio chiaro quando una traccia è diventata non visibile o cancellata.

### Disponibilità

Admin e super admin vedono conteggi, soglia, scadenza e tabella nominativa già disponibile, con filtri per risposta. Gli altri utenti vedono soltanto la propria risposta e il proprio promemoria.

L'archivista vede la propria risposta e conteggi aggregati necessari alla diagnostica spartiti; non riceve l'elenco nominativo dal nuovo endpoint.

### Materiali

La sezione amministrativa mostra oggetto, numero inventariale, quantità richiesta, assegnatario, quantità residua, condizione e stato della conferma. Le azioni aprono:

- selettore oggetto;
- selettore assegnazione compatibile;
- dettaglio inventario in nuova navigazione interna;
- conferma materiale pronto.

La pagina non replica modifica oggetto, presa visione o riconsegna.

### Economia

Admin, super admin e tesoriere vedono preventivo, incassato, pagato, residui, risultato e stato economico. I comandi riusano i dialoghi economici esistenti e i deep link a `/finance` con `eventId`.

Conferma preventivo e conferma “nessun movimento previsto” mostrano lo snapshot economico che verrà attestato. Non bloccano future correzioni.

### Presenze

La griglia esistente rimane un'unità autonoma. Dopo il salvataggio, un secondo comando conferma il registro corrente. Una modifica successiva mostra “Registro modificato dopo l'ultima conferma”.

## Esperienza per ruolo

### Admin e super admin

Vedono l'intero stato di preparazione e possono intervenire su tutte le aree, salvo le normali separazioni tra salvataggi.

### Archivista

Usa la pagina come workspace musicale: programma, stato tracce, spartiti e copertura strumentale. Non vede finanza, assegnatari inventario o nomi nelle disponibilità aggregate.

### Utente e utente esterno

Vedono informazioni evento consentite, programma pubblicato, propri spartiti, disponibilità personale e promemoria. Non vedono la percentuale amministrativa completa, perché rivelerebbe indirettamente problemi economici o materiali; ricevono soltanto messaggi pertinenti come “Il programma non è ancora disponibile”.

### Tesoriere

La rotta `/calendar/:id` viene ammessa per `ROLE_TREASURER`, ma carica soltanto intestazione minima e sezione economica tramite `/api/finance/**`. Non può leggere o modificare programma, disponibilità, presenze, materiali o dati generali dell'evento.

## Accessibilità

- stato e percentuale hanno testo, non dipendono dal colore;
- la progress bar espone nome, valore e significato accessibile;
- le criticità sono un elenco semantico con titoli correttamente gerarchizzati;
- le ancore aggiornano focus e URL fragment senza perdere modifiche;
- drag and drop dispone sempre di pulsanti alternativi;
- tabelle hanno intestazioni e descrizioni;
- salvataggi e conferme aggiornano una live region `polite`;
- errori bloccanti usano `role="alert"` soltanto dopo un'azione o un caricamento fallito;
- `prefers-reduced-motion` disabilita scorrimenti animati;
- la pagina resta usabile a 320 px senza scorrimento orizzontale globale.

## Notifiche

Le notifiche sono prodotte tramite l'outbox generalizzata e non dal frontend.

| Evento | Destinatari | Regola |
| --- | --- | --- |
| programma pubblicato o modificato | utenti disponibili autorizzati | una notifica per salvataggio, solo evento visibile e futuro |
| materiale collegato a un'assegnazione | assegnatario e admin | include oggetto, quantità ed evento |
| conferma materiale invalidata | admin e super admin | soltanto se evento entro 7 giorni |
| soglia partecipanti non raggiunta | admin e super admin | una volta alla scadenza disponibilità |
| presenze da confermare | admin e super admin | una volta dopo la fine, se richieste |
| chiusura economica aperta | admin, super admin e tesoriere | una volta dopo la fine, se richiesta |

Per evitare spam, il salvataggio atomico del programma genera un solo evento anche se contiene molti riordini. I promemoria a scadenza usano chiavi evento deterministiche composte da tipo, evento e istante di scadenza; l'outbox impedisce duplicazioni senza rendere persistito lo stato di prontezza autorevole.

I messaggi utente non contengono importi, risposte altrui o materiale assegnato ad altre persone.

## Integrazione con dashboard operativa

Quando la dashboard operativa è disponibile, il provider calendario aggiunge:

- `EVENT_PREPARATION_BLOCKED` per eventi entro 14 giorni con almeno un blocker;
- `EVENT_PREPARATION_ATTENTION` per eventi entro 7 giorni con warning;
- `EVENT_FOLLOW_UP_REQUIRED` per eventi terminati con presenze o economia da chiudere.

La dashboard mostra conteggio eventi, evento più vicino e deep link `/calendar/{id}#preparation`. Non duplica tutte le criticità.

Le righe esistenti sulle disponibilità mancanti vengono deduplicate: se lo stesso evento è già `BLOCKED` per disponibilità, compare soltanto nella riga più generale di preparazione, mentre il conteggio delle risposte rimane nella descrizione.

## Sicurezza e privacy

### Isolamento tenant

- Ogni query usa il tenant derivato dal token e lo schema già selezionato.
- Tutte le nuove tabelle includono `tenant_code` anche quando hanno una FK a un'entità tenant-scoped.
- ID inviati dal client vengono verificati nel tenant prima di creare relazioni.
- Track, item e assignment di tenant diversi producono `404`, non dettagli di autorizzazione.
- Nessun dato preparazione viene aggiunto agli indici OpenSearch condividendo informazioni sensibili.

### Minimizzazione per ruolo

- utenti ed esterni non ricevono nomi o risposte di altri partecipanti;
- archivista riceve conteggi aggregati, non nominativi;
- tesoriere riceve soltanto dati evento minimi e dati economici;
- importi, movimenti e controparti non compaiono nei DTO musicali o personali;
- materiali amministrativi non sono esposti agli assegnatari salvo le normali informazioni della propria assegnazione;
- URL media e deep link sono costruiti da percorsi interni consentiti.

### Testo libero e log

Note esecutive e finalità del materiale sono testo non fidato: vengono validate, escaped e mai interpretate come HTML. Log e metriche non contengono note, descrizioni economiche, nomi utente o titoli dei file.

## Osservabilità

Metriche Micrometer a cardinalità limitata:

- `taurus_event_preparation_evaluation_total{status,phase}`;
- `taurus_event_preparation_evaluation_seconds{role_group}`;
- `taurus_event_preparation_issue_total{area,severity,code}`;
- `taurus_event_preparation_command_total{area,outcome}`;
- `taurus_event_preparation_confirmation_invalidated_total{area}`.

`eventId`, tenant, user ID, track ID e item ID non sono tag metrici.

Log strutturati:

- valutazione fallita con event ID interno, area e classe errore;
- comando con event ID, area, attore, versione e risultato;
- conferma creata o invalidata con area e hash troncato;
- tentativo cross-tenant o riferimento incoerente senza dati della risorsa estranea.

## Prestazioni

Obiettivi su ambiente di staging:

- p95 della vista amministrativa sotto 700 ms con 100 programmi, 1.000 utenti e 100 materiali;
- p95 delle viste personali sotto 400 ms;
- massimo 100 voci programma e 100 righe materiale;
- massimo 50 criticità dettagliate in risposta, con conteggio totale separato;
- nessun caricamento binario nel DTO;
- nessuna query per riga.

Le query usano proiezioni e aggregazioni:

- disponibilità aggregate in una singola query;
- programma con tracce e conteggi spartiti/media;
- materiali con item e assegnazioni tramite fetch mirato;
- finanza tramite la proiezione `EventSummaryDTO` esistente;
- presenze aggregate e hash calcolato su ordine deterministico.

La prima versione non usa cache: le mutazioni attraversano domini diversi e una cache aumenterebbe il rischio di mostrare “pronto” su dati obsoleti. La pagina ricarica la valutazione dopo ogni comando riuscito e offre un aggiornamento manuale.

## Gestione errori

| Caso | Risposta/comportamento |
| --- | --- |
| evento non visibile o altro tenant | `404` |
| ruolo non autorizzato a un comando | `403` |
| versione superata | `409` con invito a ricaricare |
| traccia/item/assegnazione incoerente | `400` oppure `404` senza dettagli cross-tenant |
| programma o materiali oltre limite | `400` |
| hash di conferma diventato obsoleto | verifica non superata, non errore di lettura |
| area obbligatoria disabilitata | stato `UNKNOWN` e area non disponibile |
| errore database o tenant routing | RFC 7807, nessuna risposta parziale ingannevole |
| salvataggio sezione fallito | modifiche locali conservate e messaggio con azione riprova |

## Test backend

### Valutazione

- piano assente e stato `NOT_CONFIGURED`;
- separazione tra `state` evento e prontezza;
- calcolo di fase con e senza `endDate`;
- denominatore composto soltanto da verifiche applicabili;
- priorità blocker rispetto alla percentuale;
- limite e ordinamento deterministico delle criticità;
- area obbligatoria disabilitata produce `UNKNOWN`;
- nessuno stato derivato salvato nel database.

### Programma e spartiti

- programma vuoto quando richiesto;
- duplicazione consentita della stessa traccia;
- ordine univoco e sostituzione atomica;
- traccia `DRAFT`, cancellata o con visibilità incompatibile;
- spartito assente, in revisione, senza strumento o senza media `READY`;
- alternativa in revisione con almeno uno spartito pronto;
- durata totale superiore alla durata evento;
- filtro spartiti per strumenti e ruolo;
- snapshot storico dopo rinomina o cancellazione logica della traccia;
- isolamento tenant.

### Disponibilità

- pubblico atteso per `COMPLETE` e `PUBLIC`;
- esclusione utenti inattivi;
- soglia prima e dopo la scadenza;
- risposte mancanti e indisponibilità;
- copertura strumentale soltanto informativa;
- nessun nominativo nei DTO archivista, utente o esterno.

### Materiali

- riga pianificata senza assegnazione;
- coerenza item/assegnazione/responsabile;
- quantità residua insufficiente;
- condizione non utilizzabile;
- hash conferma valido e invalidato da ogni variazione rilevante;
- guasto `UNSAFE` quando la feature è disponibile;
- nessuna modifica automatica a quantità, assegnazioni o riconsegne;
- autorizzazione admin e isolamento tenant.

### Economia e presenze

- preventivo zero non confermato e confermato;
- invalidazione dopo modifica di compenso o costi;
- chiusura con `SETTLED`;
- conferma nessun movimento e invalidazione successiva;
- warning per movimenti non riconciliati senza blocco della chiusura;
- registro presenze vuoto confermabile;
- invalidazione dopo modifica delle presenze;
- tesoriere senza accesso ai dati operativi.

### Notifiche

- un solo evento outbox per salvataggio programma;
- destinatari filtrati per visibilità e tenant;
- deduplicazione soglie e follow-up;
- nessun importo o nominativo improprio nei payload;
- mancata consegna non annulla la modifica di dominio.

## Test frontend

- composizione delle sezioni per ogni ruolo;
- testata per tutti gli stati e fasi;
- percentuale, conteggi e ordinamento criticità;
- navigazione ad ancora e gestione focus;
- salvataggi indipendenti senza perdita di modifiche nelle altre sezioni;
- conflitto `409` e ricaricamento controllato;
- programma con aggiunta, duplicazione, riordino e rimozione;
- fallback accessibile al drag and drop;
- spartiti filtrati e download autorizzato;
- soglia e scadenza disponibilità;
- materiali pianificati, collegati e confermati;
- conferma preventivo e presenze;
- vista economica isolata del tesoriere;
- assenza di dati non autorizzati anche nelle chiamate HTTP;
- layout mobile a 320 px e navigazione tastiera;
- guardia modifiche non salvate con elenco delle unità sporche.

## Test end-to-end

1. Admin crea un evento, configura profilo performance e vede le criticità iniziali.
2. Archivista compone il programma e corregge uno spartito non pronto.
3. Utente vede il programma e soltanto gli spartiti del proprio strumento, quindi registra disponibilità.
4. Utente esterno non vede un evento o una traccia non `PUBLIC`.
5. Admin raggiunge la soglia partecipanti, collega materiale assegnato e lo conferma.
6. Tesoriere conferma un preventivo, senza poter modificare dati evento.
7. L'evento passa a `READY` senza salvataggio manuale dello stato.
8. Una modifica allo spartito o all'assegnazione fa riapparire la criticità pertinente.
9. Dopo l'evento admin salva e conferma le presenze.
10. Tesoriere registra i movimenti fino a `SETTLED` e la chiusura passa a `CLOSED`.
11. Ogni tentativo con ID di un altro tenant fallisce senza rivelare dati.

## Migrazione e rilascio

La funzionalità è protetta da `application.event-preparation.enabled`, inizialmente `false` negli ambienti esistenti.

Prima dell'abilitazione:

1. backup PostgreSQL;
2. applicazione Liquibase a tutti gli schemi tenant;
3. verifica vincoli e indici;
4. test dei DTO per ogni ruolo;
5. prova con evento `DRAFT`, `COMPLETE`, `PUBLIC` e ricorrente;
6. collaudo con dati economici e inventario reali di staging;
7. controllo prestazioni con volumi limite;
8. verifica notifiche, deep link e metriche;
9. smoke test mobile e accessibilità.

Gli eventi esistenti restano utilizzabili nella pagina attuale e mostrano un invito amministrativo non bloccante a configurare la preparazione. Utenti ed esterni non vedono `NOT_CONFIGURED` come errore.

## Rollback

Disabilitare la feature flag ripristina il dettaglio evento senza workspace di preparazione. Programma, configurazione, materiali e conferme restano nelle nuove tabelle e non vengono eliminati.

Il rollback non modifica eventi, tracce, spartiti, assegnazioni, presenze o movimenti. Alla riattivazione, gli hash vengono rivalutati sui dati correnti e possono risultare obsoleti; Taurus non forza una vecchia conferma a tornare valida.

## Piano di implementazione

### Fase 1 — Fondazioni e valutatore

1. Migration del piano e contratti DTO.
2. Configurazione profili e vincoli.
3. Aggregatore con dati evento, disponibilità e fasi.
4. Stato derivato, percentuale e criticità.
5. Test tenant, ruoli e date.

### Fase 2 — Programma e spartiti

1. Tabella programma e snapshot.
2. API atomiche e permessi archivista.
3. Evaluator spartiti/media/visibilità.
4. Componenti programma e download autorizzato.
5. Test accessibilità e notifiche programma.

### Fase 3 — Materiali

1. Tabella materiali.
2. Collegamento a item e assegnazioni.
3. Conferma con hash e invalidazione.
4. Sezione frontend e deep link inventario.
5. Integrazione opzionale con guasti `UNSAFE`.

### Fase 4 — Economia e follow-up

1. Conferma preventivo con hash.
2. Conferma presenze.
3. Chiusura automatica `SETTLED` e caso nessun movimento.
4. Vista isolata del tesoriere.
5. Promemoria follow-up tramite outbox.

### Fase 5 — Consolidamento

1. Scomposizione completa del dettaglio Angular.
2. Integrazione dashboard e deduplicazione.
3. Test end-to-end e prestazionali.
4. Feature flag e rollout.
5. Aggiornamento delle specifiche correlate e del catalogo documentale.

## Criteri di accettazione

1. `DRAFT`, `COMPLETE` e `PUBLIC` mantengono il significato di visibilità e non vengono riusati come stato operativo.
2. Prontezza e chiusura sono sempre derivate e non esiste un booleano manuale `ready`.
3. Ogni verifica non deducibile ha una configurazione o conferma esplicita e auditata.
4. La percentuale considera soltanto aree applicabili e non nasconde blocker.
5. Ogni criticità ha codice stabile, area, severità e azione autorizzata.
6. Il programma è ordinato, atomico, tenant-scoped e consente ripetizioni della stessa traccia.
7. Una traccia o uno spartito non visibile al pubblico dell'evento impedisce `READY`.
8. Solo media `READY` soddisfano il controllo spartiti.
9. Gli utenti ricevono soltanto spartiti compatibili con i propri strumenti.
10. La soglia disponibilità e le risposte mancanti cambiano severità alla scadenza configurata.
11. La copertura strumentale è presentata come euristica e non come assegnazione di organico.
12. I materiali riusano assegnazioni inventario e non introducono una seconda quantità di magazzino.
13. Una conferma materiale diventa obsoleta quando cambia lo snapshot rilevante.
14. Un materiale non assegnato o non utilizzabile impedisce `READY` quando richiesto.
15. Preventivo zero e registro presenze vuoto possono essere confermati intenzionalmente.
16. Modifiche successive invalidano conferme economiche o presenze senza impedire nuove correzioni.
17. `SETTLED` chiude automaticamente l'area economica; gli altri stati seguono le regole documentate.
18. Il tesoriere usa soltanto API economiche e non riceve programma, disponibilità, presenze o materiali.
19. Archivista, utente ed esterno ricevono DTO costruiti per allowlist senza dati nascosti lato client.
20. Programma e materiali di una serie appartengono all'occorrenza e non creano `series_exception`.
21. Ogni area mantiene salvataggio, dirty state e concorrenza indipendenti.
22. Nessuna query o relazione può attraversare il tenant corrente.
23. OpenSearch non diventa fonte autorevole della preparazione.
24. Il caricamento non esegue query per riga e rispetta gli obiettivi prestazionali.
25. La dashboard deduplica disponibilità e preparazione per lo stesso evento.
26. Le notifiche sono aggregate per comando e non espongono informazioni non autorizzate.
27. Un'area obbligatoria indisponibile produce `UNKNOWN`, mai un falso `READY`.
28. La rotta `/calendar/:id` e i collegamenti esistenti restano validi.
29. La pagina è utilizzabile a 320 px, da tastiera e con screen reader.
30. Feature flag e rollback preservano tutti i dati applicativi e di preparazione.

## Decisioni rinviate

- convocazioni nominali e organico per parte;
- copia del piano verso occorrenze future;
- template riutilizzabili di programma e preparazione;
- prenotazioni esclusive di stock libero;
- pacchetto ZIP/PDF dell'evento;
- logistica di trasporto, palco, divise e pernottamenti;
- workflow di approvazione del programma;
- firme o congelamento formale di presenze e consuntivo;
- sincronizzazione offline.
