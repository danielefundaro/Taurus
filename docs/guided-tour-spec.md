# Guida introduttiva per ruolo

La funzionalità accompagna l'utente al primo ingresso in Taurus con un percorso visivo breve, costruito sul ruolo scelto all'accesso e sulle funzionalità attive nel tenant. Si appoggia al [Catalogo delle funzionalità del tenant](tenant-feature-flags-spec.md), rispetta lo [Standard di layout Taurus](taurus-layout-standard.md) e usa le chiavi dello [Standard degli errori applicativi](application-errors-standard.md).

Non va confusa con due elementi già presenti:

- l'[Onboarding guidato e importazione iniziale del tenant](tenant-onboarding-import-spec.md), voce di menu **Configurazione iniziale**, che carica dati in massa;
- la modalità **Primo accesso** descritta in [PostgreSQL schema per tenant](postgres-tenant-schemas.md), in cui il super amministratore crea il primo tenant su un ambiente vuoto.

Per evitare ambiguità l'interfaccia chiama la nuova funzionalità **Guida**, il codice usa il prefisso `guided-tour` e i testi non usano le parole «onboarding», «wizard» o «primo accesso».

## Stato del documento

ID catalogo: `guided-tour`.
Lo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).

## Contesto

Oggi un utente che entra per la prima volta riceve l'e-mail di impostazione della password, sceglie tenant e ruolo nella schermata Keycloak quando ne ha più di uno, accetta i documenti legali e arriva sulla dashboard. Da quel momento nessun elemento spiega cosa può fare con il ruolo scelto, dove si trovano le funzioni e quali dipendono dalle funzionalità del tenant.

Fatti del codice che condizionano la progettazione:

| Fatto | Fonte | Conseguenza |
| --- | --- | --- |
| Una persona può avere più tenant e più ruoli per tenant; ne sceglie una coppia a ogni accesso | `TenantRoleSelectorAuthenticator`, claim `tenant` e `role` | la guida dipende dal ruolo della sessione, non dalla sola persona |
| La sessione ha un solo ruolo effettivo | `KeycloakService.currentUserRole` | nessuna unione di ruoli nella stessa sessione |
| Menu, rotte e widget dipendono da ruolo e flag del tenant | `MenuComponent`, `app.routes.ts`, `tenantFeatureGuard` | ogni tappa dichiara ruoli e funzionalità richieste |
| Non esiste un indicatore di primo accesso | nessuna colonna `first_login` o simile | lo stato della guida è un dato nuovo |
| `localStorage` viene svuotato alla chiusura della finestra e le chiavi non includono utente o tenant | `AppComponent.unloadHandler`, `LocalStorageService` | lo stato non può vivere nel browser |
| Le preferenze chiave-valore sono per utente e per schema tenant | `preferences`, `CommonServiceImpl` | una chiave libera ripeterebbe la guida in ogni tenant; le specifiche recenti preferiscono tabelle tipizzate |
| Su schermi fino a 991 px il menu laterale è a scomparsa e le azioni della topbar sono raccolte nel pulsante a tre punti | `LayoutService.isDesktop`, `topbar.component.html` | le evidenziazioni devono avere un'alternativa su mobile |
| PrimeNG 19.0.10 offre `p-popover` ancorabile a un elemento, `p-stepper` e la direttiva `pFocusTrap`, ma non un componente tour | `node_modules/primeng` | la guida si costruisce con componenti esistenti, senza nuove dipendenze |
| La specifica [Richieste di assistenza](support-requests-spec.md), in bozza, prevede un'azione `pi-question-circle` nella topbar | sezione interfaccia della specifica | il punto d'ingresso dell'aiuto va condiviso |

## Obiettivo

Permettere a ogni utente di capire in circa due minuti cosa può fare con il proprio ruolo, dove si trova ogni funzione e come tornare alla guida, senza leggere manuali e senza essere interrotto più del necessario.

La prima versione deve:

- avviare automaticamente la guida al primo ingresso con un ruolo;
- mostrare soltanto le tappe pertinenti al ruolo della sessione e alle funzionalità attive del tenant;
- evidenziare graficamente gli elementi dell'interfaccia di cui parla, con un'alternativa centrata quando l'elemento non è visibile;
- permettere di interrompere, riprendere, saltare e ricominciare la guida;
- permettere a ogni utente di disattivare e riattivare l'avvio automatico;
- restare disponibile su richiesta in ogni momento;
- funzionare su desktop e mobile, da tastiera e con lettore di schermo;
- non aggiungere peso al caricamento iniziale dell'applicazione.

## Decisioni principali

| Aspetto | Decisione proposta |
| --- | --- |
| Nome nell'interfaccia | **Guida** |
| Unità di stato | persona e ruolo; stato condiviso tra i tenant della stessa persona |
| Persistenza | due tabelle tipizzate nello schema `public` |
| Avvio automatico | interruttore unico per persona, attivo per default |
| Avvio manuale | sempre disponibile dal pulsante di aiuto della topbar, anche con avvio automatico disattivato |
| Momento dell'avvio | solo sulla dashboard, dopo accettazione dei documenti legali e caricamento delle funzionalità del tenant |
| Struttura | una scheda di benvenuto e da 5 a 10 tappe per ruolo |
| Presentazione | scheda ancorata all'elemento evidenziato, oppure scheda centrata |
| Interazione con la pagina | la guida mostra e non agisce: durante una tappa la pagina non riceve clic |
| Contenuti | catalogo statico nel frontend, in italiano, con identificativi stabili |
| Dipendenze | nessuna nuova libreria; componenti PrimeNG e Tailwind già presenti |
| Caricamento | componente e catalogo caricati in modo differito, solo quando servono |
| Utenti esistenti al rilascio | invito discreto sulla dashboard invece dell'avvio automatico |
| Modalità bootstrap | esclusa: resta l'avviso «Primo accesso» già presente nella pagina Tenant |
| Flag del tenant | nessun nuovo flag; un interruttore applicativo di emergenza |
| Lingua | solo italiano, catalogo predisposto per altre lingue |

## Ambito della prima versione

### Compreso

- percorso principale per i sei ruoli: Super amministratore, Amministratore, Tesoriere, Archivista, Utente, Utente esterno;
- filtro delle tappe per ruolo e per funzionalità del tenant;
- scheda di benvenuto con nome, organizzazione e ruolo;
- evidenziazione degli elementi con ripiego sulla scheda centrata;
- navigazione automatica verso la pagina di una tappa;
- comandi Avanti, Indietro, Salta la guida, Chiudi e Fine;
- ripresa dall'ultima tappa vista;
- interruttore personale di avvio automatico e comando per ricominciare;
- pulsante di aiuto nella topbar;
- sezione **Guida** nella pagina profilo;
- invito una tantum agli utenti esistenti al rilascio;
- API, migrazione, test e criteri di accettazione.

### Fuori ambito

- guide di pagina su richiesta, previste nella fase 2;
- segnalazione delle novità dopo un rilascio, prevista nella fase 3;
- tappe che chiedono di compiere un'azione reale, per esempio indicare una disponibilità;
- video, animazioni complesse, immagini o illustrazioni dedicate;
- testi modificabili da amministratori o tenant;
- obbligo di completare la guida prima di usare Taurus;
- statistiche individuali sull'uso della guida;
- avvio della guida da parte di un amministratore per conto di un altro utente;
- guida nella modalità bootstrap, nella pagina pubblica di firma e nelle pagine fuori dal layout.

## Terminologia

- **Guida**: l'insieme di benvenuto e tappe per il ruolo della sessione.
- **Tappa**: un passo della guida con titolo, testo breve, eventuale pagina di destinazione ed eventuale elemento da evidenziare.
- **Ancora**: identificativo stabile assegnato a un elemento dell'interfaccia che una tappa può evidenziare.
- **Scheda ancorata**: presentazione accanto all'elemento evidenziato, con il resto della pagina oscurato.
- **Scheda centrata**: presentazione al centro dello schermo, senza evidenziazione.
- **Avvio automatico**: apertura della guida senza richiesta dell'utente.
- **Proposta automatica**: ogni volta che la guida si apre da sola per un ruolo non ancora concluso.
- **Invito**: banner sulla dashboard che propone la guida agli utenti già presenti al rilascio.

## Esperienza utente

### Sequenza del primo ingresso

```text
E-mail di impostazione password
    -> login Keycloak
    -> scelta tenant e ruolo (solo se più di uno)
    -> accettazione documenti legali (se richiesta)
    -> dashboard
    -> scheda di benvenuto della Guida
```

La guida non parte su altre pagine. Un utente che entra da un collegamento diretto, per esempio da una notifica push, da un'e-mail o dalla scansione di un codice QR, arriva a destinazione senza interruzioni; la guida viene proposta alla prima visita successiva della dashboard.

### Scheda di benvenuto

```text
┌──────────────────────────────────────────────┐
│                  (icona ruolo)               │
│          Benvenuto in Taurus, Maria          │
│                                              │
│  Sei in Banda Musicale Santa Cecilia come    │
│  Utente. In circa due minuti ti mostriamo    │
│  cosa puoi fare.                             │
│                                              │
│        [ Inizia la guida ]   Più tardi       │
│                                              │
│  Non avviare più la guida automaticamente    │
└──────────────────────────────────────────────┘
```

- **Inizia la guida** passa alla prima tappa.
- **Più tardi** chiude la scheda per la sessione corrente; la guida sarà riproposta al prossimo accesso.
- **Non avviare più la guida automaticamente** spegne l'interruttore personale; un toast conferma e indica dove riattivarlo.

L'organizzazione è il nome del tenant mostrato nella topbar. Per i ruoli diversi dal Super amministratore i testi dicono «organizzazione», mai «tenant».

### Tappa con evidenziazione

```text
 ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 ░┌────────────┐░░┌────────────────────────────────┐
 ░│ Calendario │◄─┤ Eventi e disponibilità       × │
 ░└────────────┘░░│                                │
 ░░░░░░░░░░░░░░░░░│ Nel calendario trovi concerti  │
 ░░░░░░░░░░░░░░░░░│ e prove. Apri un evento e      │
 ░░░░░░░░░░░░░░░░░│ indica se ci sarai.            │
 ░░░░░░░░░░░░░░░░░│ • Riceverai un promemoria      │
 ░░░░░░░░░░░░░░░░░│                                │
 ░░░░░░░░░░░░░░░░░│ ● ● ● ○ ○ ○ ○ ○        3 di 8  │
 ░░░░░░░░░░░░░░░░░│ Salta la guida  Indietro Avanti│
 ░░░░░░░░░░░░░░░░░└────────────────────────────────┘
```

La pagina viene oscurata con un velo semitrasparente che lascia libero un riquadro attorno all'elemento evidenziato. La scheda si posiziona accanto al riquadro, preferendo destra, sotto, sopra e sinistra in quest'ordine; se non c'è spazio sufficiente diventa centrata.

### Composizione della scheda

| Zona | Contenuto | Limite |
| --- | --- | --- |
| Intestazione | icona PrimeIcons della tappa e titolo | titolo entro 40 caratteri |
| Corpo | una o due frasi | entro 180 caratteri |
| Punti | dettagli che dipendono dalle funzionalità del tenant | massimo 3, entro 60 caratteri ciascuno |
| Avanzamento | pallini e testo «3 di 7» | sempre visibile |
| Piede | **Salta la guida** come testo a sinistra; **Indietro** secondario e **Avanti** primario a destra | all'ultima tappa **Avanti** diventa **Fine** |
| Chiusura | icona di chiusura nell'intestazione | equivale a una pausa |

La scheda è larga al massimo 22rem, usa i token di colore PrimeNG per tema chiaro e scuro, non contiene immagini e non usa animazioni oltre a una dissolvenza di 150 ms, disattivata con `prefers-reduced-motion`.

### Comandi e loro effetto

| Comando | Effetto sullo stato del ruolo | Effetto sull'avvio automatico |
| --- | --- | --- |
| Inizia la guida | `IN_PROGRESS` | nessuno |
| Avanti, Indietro | aggiorna ultima tappa e tappe viste | nessuno |
| Chiudi o `Esc` | resta `IN_PROGRESS`, conserva l'ultima tappa | conta come proposta automatica se la guida era partita da sola |
| Più tardi | invariato | rinvio per la sola sessione corrente |
| Salta la guida | `SKIPPED` | resta acceso per gli altri ruoli |
| Fine | `COMPLETED` | nessuno |
| Non avviare più la guida automaticamente | invariato | spento per tutti i ruoli |
| Ricomincia la guida, dal profilo | stato del ruolo azzerato | invariato |

**Salta la guida** non chiede conferma: è reversibile dal pulsante di aiuto. Il toast di esito dice «Guida chiusa. Puoi riaprirla dal pulsante ? in alto».

### Mobile

Sotto i 992 px:

- le tappe sul menu evidenziano il pulsante che apre il menu;
- le tappe sulle azioni della topbar evidenziano il pulsante a tre punti;
- le tappe con una pagina di destinazione evidenziano l'intestazione della pagina;
- se nessuna ancora candidata è visibile, la tappa usa la scheda centrata;
- la scheda occupa la larghezza disponibile meno 1rem per lato e si posiziona sotto o sopra l'elemento.

### Accessibilità

- La scheda ha `role="dialog"`, `aria-modal="true"`, `aria-labelledby` sul titolo e `aria-describedby` sul corpo.
- Il fuoco entra sul comando primario, resta nella scheda con `pFocusTrap` e torna all'elemento che lo aveva prima dell'apertura.
- `Esc` equivale a **Chiudi**.
- Una regione `aria-live="polite"` annuncia «Passo 3 di 7: Eventi e disponibilità».
- Ogni testo è comprensibile senza vedere l'evidenziazione: indica dove si trova l'elemento, per esempio «nel menu a sinistra, voce Calendario».
- Con zoom al 200% o altezza inferiore a 480 px la scheda è sempre centrata e scorrevole.

## Contenuti per ruolo

### Regole di composizione

Ogni tappa del catalogo dichiara:

| Campo | Significato |
| --- | --- |
| `id` | identificativo stabile, formato `{area}.{oggetto}` in camelCase |
| `roles` | ruoli a cui si applica |
| `requires` | funzionalità del tenant tutte necessarie; tappa esclusa se una manca |
| `excludes` | funzionalità che, se attive, escludono la tappa |
| `route` | pagina da aprire prima di mostrare la tappa, facoltativa |
| `anchors` | ancore candidate in ordine di preferenza, facoltative |
| `icon` | icona PrimeIcons |
| `title`, `body` | testi, con varianti per ruolo quando servono |
| `points` | punti facoltativi, ciascuno con le proprie `requires` |
| `introducedIn` | revisione del catalogo che ha introdotto la tappa |

La guida di un ruolo è l'elenco ordinato delle tappe applicabili. Benvenuto e tappa finale sono sempre presenti. Il limite è di 10 tappe incluso il benvenuto; l'obiettivo è da 6 a 8. Le funzioni secondarie degli amministratori sono raccolte in una sola tappa e approfondite dalle guide di pagina della fase 2.

### Tappe comuni

| ID | Titolo | Pagina e ancora | Testo e punti |
| --- | --- | --- | --- |
| `common.welcome` | Benvenuto in Taurus | scheda centrata | nome, organizzazione, ruolo e durata stimata |
| `common.navigation` | Il menu | `layout.menu`; su mobile `layout.menuButton` | «Dal menu raggiungi le sezioni del tuo ruolo: vedi solo ciò che puoi usare.» |
| `common.notifications` | Le notifiche | `/`, `dashboard.notifications` | «Qui arrivano le notifiche: aprile per andare al dettaglio o segnale come lette.» Punti: «Rimandale con le azioni di posticipo», sempre; «Scegli cosa ricevere dal profilo» e «Attiva le notifiche su questo dispositivo» con `NOTIFICATION_PREFERENCES`, da cui dipendono preferenze e registrazione push |
| `common.profile` | Il tuo profilo | `topbar.profile`; su mobile `topbar.more` | «Nel profilo aggiorni i tuoi dati, la password e la verifica in due passaggi.» Punto per Utente e Utente esterno con `EXTERNAL_CALENDAR_FEED`: «Collega Taurus al calendario del telefono» |
| `common.help` | Quando ti serve | `topbar.help` | «Riapri la guida quando vuoi da qui. Se preferisci che non parta da sola, disattivala dal profilo.» |

### Utente

| Ordine | ID | Pagina e ancora | Condizioni | Testo e punti |
| ---: | --- | --- | --- | --- |
| 1 | `common.welcome` | — | — | — |
| 2 | `common.navigation` | — | — | — |
| 3 | `calendar.availability` | `/calendar`, `calendar.header` | — | «Nel calendario trovi concerti e prove. Apri un evento e indica se ci sarai: chi organizza vede subito la risposta.» Punti: «Nel dettaglio trovi luogo, orario e scaletta»; «Riceverai un promemoria prima dell'inizio» con `WEB_PUSH_REMINDERS` |
| 4 | `catalog.tracks` | `/tracks`, `tracks.header` | — | «In Tracce trovi il repertorio con gli spartiti dei tuoi strumenti; in Album i brani sono raccolti per programma.» Punto: «Ascolta le parti mentre leggi» con `SCORE_PLAYBACK` |
| 5 | `inventory.mine` | `/inventory`, `inventory.header` | `INVENTORY` | «Qui trovi strumenti e materiali che ti sono stati affidati: prendine visione o avvia la riconsegna.» Punti: «Scarica i fogli di affidamento» con `INVENTORY_LOAN_SHEET`; «Firma gli atti quando ti viene chiesto» con `INVENTORY_SIGNATURE`; «Inquadra il QR dell'oggetto per segnalare un guasto» con `INVENTORY_QR` |
| 6 | `common.notifications` | — | — | — |
| 7 | `common.profile` | — | — | — |
| 8 | `common.help` | — | — | — |

### Utente esterno

Stesse tappe dell'Utente, con varianti di testo che non promettono ciò che il ruolo non vede:

- `calendar.availability`: «Nel calendario trovi gli eventi pubblici a cui puoi partecipare…»;
- `catalog.tracks`: «In Tracce trovi i brani pubblici con gli spartiti dei tuoi strumenti…».

### Archivista

| Ordine | ID | Pagina e ancora | Condizioni | Testo e punti |
| ---: | --- | --- | --- | --- |
| 1–2 | benvenuto e menu | — | — | — |
| 3 | `catalog.manage` | `/tracks`, `tracks.create` | — | «Crea album e tracce e carica gli spartiti in PDF, associando ogni parte al suo strumento.» |
| 4 | `catalog.instruments` | `/instruments`, `instruments.header` | — | «L'elenco degli strumenti dell'organizzazione serve ad abbinare parti e musicisti.» |
| 5 | `calendar.program` | `/calendar`, `calendar.header` | — | «Apri un evento e collega un album: le sue tracce diventano la scaletta. Puoi indicare anche la tua disponibilità.» Punto: «Nel riepilogo vedi cosa manca a programma e spartiti» con `EVENT_PREPARATION` |
| 6 | `inventory.mine` | come per l'Utente | `INVENTORY` | come per l'Utente |
| 7–9 | notifiche, profilo, aiuto | — | — | — |

### Tesoriere

| Ordine | ID | Pagina e ancora | Condizioni | Testo e punti |
| ---: | --- | --- | --- | --- |
| 1–2 | benvenuto e menu | — | — | — |
| 3 | `finance.overview` | `/finance`, `finance.summary` | `FINANCE` | «In Economia trovi conti, saldi e movimenti dell'organizzazione.» |
| 4 | `finance.movements` | `/finance`, `finance.create` | `FINANCE` | «Registra entrate, uscite e trasferimenti, allega le ricevute e segna i movimenti riconciliati.» |
| 5 | `finance.events` | scheda centrata | `FINANCE` | «Le notifiche economiche ti portano al dettaglio dell'evento, dove gestisci preventivo e consuntivo.» |
| 3 | `finance.unavailable` | scheda centrata | esclusa con `FINANCE` | «La sezione Economia non è ancora attiva per questa organizzazione. Chiedi all'amministratore di Taurus di attivarla.» |
| 6–8 | notifiche, profilo, aiuto | — | — | — |

### Amministratore

| Ordine | ID | Pagina e ancora | Condizioni | Testo e punti |
| ---: | --- | --- | --- | --- |
| 1–2 | benvenuto e menu | — | — | — |
| 3 | `admin.users` | `/users`, `users.create` | — | «Aggiungi i membri dell'organizzazione e assegna il ruolo: riceveranno un'e-mail per impostare la password.» |
| 4 | `admin.calendar` | `/calendar`, `calendar.create` | — | «Crea prove e concerti, anche ricorrenti, e segui chi ha risposto.» Punti: «Controlla materiali, programma e costi nella preparazione» con `EVENT_PREPARATION`; «Conferma le presenze dopo l'evento» |
| 5 | `admin.inventory` | `/inventory`, `inventory.create` | `INVENTORY` | «Registra strumenti e materiali, affidali ai membri e segui riconsegne e scadenze.» Punti: «Stampa le etichette con il QR» con `INVENTORY_QR`; «Prepara il modello del foglio di affidamento» con `INVENTORY_LOAN_SHEET` |
| 6 | `finance.overview` | come per il Tesoriere | `FINANCE` | variante: «…e prepara preventivi e consuntivi degli eventi.» |
| 7 | `admin.tools` | `layout.menu.admin`; su mobile `layout.menuButton` | — | «Nel menu trovi anche gli strumenti di amministrazione.» Punti: «Configurazione iniziale: importa dati da un file» con `ONBOARDING_IMPORT`; «Consegne notifiche: controlla quelle non arrivate»; «Feed calendario: pubblica il calendario» con `EXTERNAL_CALENDAR_FEED` |
| 8–10 | notifiche, profilo, aiuto | — | — | — |

Album, tracce e strumenti, disponibili anche all'Amministratore, sono lasciati alle guide di pagina per rispettare il limite di 10 tappe.

### Super amministratore

| Ordine | ID | Pagina e ancora | Condizioni | Testo e punti |
| ---: | --- | --- | --- | --- |
| 1–2 | benvenuto e menu | — | — | — |
| 3 | `superAdmin.tenants` | `/tenants`, `tenants.header` | — | «Crea e gestisci i tenant e scegli quali funzionalità attivare per ciascuno.» |
| 4 | `superAdmin.legalDocuments` | `/legal-documents`, `legalDocuments.header` | — | «Pubblica privacy e condizioni d'uso: gli utenti le accettano al primo ingresso e a ogni nuova versione.» |
| 5 | `admin.users` | come per l'Amministratore | — | — |
| 6 | `admin.tools` | come per l'Amministratore | — | — |
| 7–9 | notifiche, profilo, aiuto | — | — | — |

### Variazioni delle funzionalità

Le funzionalità sono valutate quando la guida si apre, con i valori già caricati da `TenantFeatureService`. Se un flag cambia mentre la guida è aperta, la composizione resta quella dell'apertura; il conteggio si aggiorna alla riapertura. Una tappa vista resta vista anche se la funzionalità viene poi disattivata.

## Disattivazione e riattivazione

La proposta separa due concetti, così ogni scelta ha un effetto prevedibile:

1. **Avvio automatico**, personale e valido per tutti i ruoli e i tenant della persona.
2. **Stato del ruolo**, cioè a che punto è la guida del ruolo corrente.

### Dove si disattiva

- dalla scheda di benvenuto, con **Non avviare più la guida automaticamente**;
- dal profilo, sezione **Guida**, con l'interruttore **Avvia la guida automaticamente**.

### Dove si riattiva

- dal profilo, con lo stesso interruttore;
- implicitamente mai: nessuna azione di sistema riaccende l'avvio automatico.

Riattivato l'interruttore, la guida si riapre da sola alla visita successiva della dashboard se il ruolo corrente non è `COMPLETED` o `SKIPPED`. Per vederla subito il profilo offre **Apri la guida**.

### Avvio manuale sempre disponibile

Il pulsante di aiuto nella topbar, con icona `pi-question-circle` e tooltip «Aiuto», apre un piccolo menu:

- **Guida introduttiva**: riprende dall'ultima tappa se il ruolo è `IN_PROGRESS`, altrimenti riparte dall'inizio;
- **Guida di questa pagina**, dalla fase 2;
- **Novità**, dalla fase 3, solo quando ce ne sono;
- **Assistenza e segnalazioni**, quando la relativa funzionalità sarà implementata.

Il pulsante resta visibile anche sotto i 992 px, accanto ai comandi del tema, perché è l'unico modo per ritrovare la guida.

### Sezione Guida del profilo

```text
Guida
Ti mostra cosa puoi fare con il tuo ruolo.

Avvia la guida automaticamente              [ ●  ]
Stato per il ruolo Utente: completata il 24/10/2026

[ Apri la guida ]   [ Ricomincia la guida ]
```

- L'interruttore si salva subito, come il consenso push del dispositivo, e non entra nel comando **Salva preferenze** delle notifiche.
- **Ricomincia la guida** azzera lo stato del solo ruolo corrente, senza conferma, e mostra un toast.
- Lo stato mostrato riguarda il ruolo della sessione; gli altri ruoli della persona non sono elencati.

## Regole di avvio automatico

La guida si apre da sola quando sono vere tutte le condizioni:

1. il tenant della sessione è provisionato;
2. la rotta corrente è la dashboard;
3. le funzionalità del tenant sono caricate;
4. l'interruttore personale è acceso;
5. il ruolo corrente non ha stato, oppure è `IN_PROGRESS` con meno di 3 proposte automatiche;
6. la persona non ha un invito in sospeso;
7. la guida non è stata rinviata con **Più tardi** nella sessione corrente;
8. nessun dialogo, conferma o strumento a schermo intero è aperto;
9. l'interruttore applicativo `application.guided-tour.enabled` è attivo.

Dopo 3 proposte automatiche chiuse senza concludere, la guida smette di aprirsi da sola per quel ruolo e il pulsante di aiuto mostra un punto informativo finché l'utente non la apre. Il rinvio **Più tardi** vive in `sessionStorage` con chiave che include utente, tenant e ruolo; non serve conservarlo oltre la sessione.

L'apertura attende 600 ms dopo il primo rendering della dashboard, per non sovrapporsi al caricamento dei widget.

### Utenti esistenti al rilascio

Una migrazione crea il profilo della guida per ogni identità attiva in `public.user_identity` con `legacy_invitation = true`. Per queste persone, finché il ruolo corrente non ha stato, la dashboard mostra un banner inline invece della scheda di benvenuto:

```text
ⓘ  Novità: una guida ti mostra in due minuti cosa puoi fare come Archivista.
   [ Inizia la guida ]   No, grazie
```

**Inizia la guida** apre la guida dal benvenuto; **No, grazie** porta il ruolo a `SKIPPED`. L'invito segue le stesse condizioni dell'avvio automatico, esclusa la 6: con l'interruttore spento non compare. Le persone create dopo il rilascio, o prive di identità come può accadere al Super amministratore creato direttamente in Keycloak, ricevono la normale scheda di benvenuto.

## Modello dati

Le tabelle sono globali, nello schema `public`, perché la guida descrive Taurus e non i dati di un tenant. Non contengono il codice del tenant né contenuti dei tenant.

### `guided_tour_profile`

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| `user_keycloak_id` | `VARCHAR(255)` | subject Keycloak, univoco |
| `auto_start_enabled` | `BOOLEAN` | default `true` |
| `legacy_invitation` | `BOOLEAN` | default `false`; `true` solo dalla migrazione |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | non nulli |
| `version` | `INTEGER` | lock ottimistico |

### `guided_tour_progress`

| Campo | Tipo indicativo | Regola |
| --- | --- | --- |
| `id` | `BIGINT` | PK |
| `profile_id` | `BIGINT` | FK con `ON DELETE CASCADE` |
| `role` | `VARCHAR(32)` | valore di `RoleEnum` |
| `status` | `VARCHAR(16)` | `IN_PROGRESS`, `COMPLETED`, `SKIPPED` |
| `seen_steps` | `JSONB` | array di ID tappa, massimo 100 elementi |
| `last_step_id` | `VARCHAR(64)` | facoltativo |
| `auto_proposals` | `SMALLINT` | da 0 a 3 |
| `catalog_revision` | `INTEGER` | revisione del catalogo all'ultima decisione |
| `started_at`, `completed_at`, `updated_at` | `TIMESTAMPTZ` | `completed_at` solo per `COMPLETED` |
| `version` | `INTEGER` | lock ottimistico |

Vincolo univoco `(profile_id, role)`. L'assenza della riga equivale allo stato «mai iniziata». `JSONB` segue la mappatura già usata da `OnboardingImportRow` con `@JdbcTypeCode(SqlTypes.JSON)`.

### Migrazione

Un changelog incluso in `master.xml`, non in `tenant-master.xml`:

1. crea le due tabelle e i vincoli;
2. inserisce un profilo con `legacy_invitation = true` per ogni `user_identity` con `deleted_at` nullo;
3. non crea righe di avanzamento.

La migrazione è idempotente rispetto ai profili già esistenti e non richiede dati Keycloak. Copre tutti gli utenti dei tenant, perché `app_user.user_identity_id` è obbligatorio e univoco: ogni profilo locale ha un'identità globale.

## API

Tutti gli endpoint richiedono autenticazione, ricavano persona e ruolo dal token e sono annotati con `@TenantSchemaNotRequired`, perché usano solo lo schema `public`. Il client non invia mai utente o ruolo.

```http
GET    /api/guided-tour
PUT    /api/guided-tour/settings
PUT    /api/guided-tour/progress
DELETE /api/guided-tour/progress
```

`GET` restituisce sempre un aggregato completo e non scrive nel database:

```json
{
  "enabled": true,
  "settings": {
    "autoStartEnabled": true,
    "legacyInvitation": false,
    "version": 2
  },
  "role": "ROLE_USER",
  "progress": {
    "status": "IN_PROGRESS",
    "seenSteps": ["common.welcome", "common.navigation", "calendar.availability"],
    "lastStepId": "calendar.availability",
    "autoProposals": 1,
    "catalogRevision": 1,
    "version": 4
  }
}
```

`progress` è `null` quando il ruolo non ha stato; `settings.version` è `null` finché il profilo non è materializzato. `enabled` riporta l'interruttore applicativo.

`PUT /settings` riceve `autoStartEnabled` e `version`; materializza il profilo alla prima scrittura. Spegnere o accendere l'interruttore non modifica `legacyInvitation`.

`PUT /progress` riceve `status`, `seenSteps`, `lastStepId`, `autoProposals`, `catalogRevision` e `version`; crea o aggiorna la riga del ruolo del token e, se serve, materializza il profilo. Qualunque scrittura dell'avanzamento azzera di fatto l'invito per quel ruolo, perché la riga esiste.

`DELETE /progress` elimina la riga del ruolo del token: è il comando **Ricomincia la guida**.

### Validazione ed errori

| Chiave proposta | HTTP | Quando |
| --- | --- | --- |
| `validation` | 400 | stato sconosciuto, ID tappa fuori formato, `autoProposals` fuori da 0–3 |
| `guidedTour.step.limit` | 400 | più di 100 tappe viste |
| `guidedTour.versionConflict` | 409 | `version` non coincide con quella salvata |
| `guidedTour.disabled` | 404 | interruttore applicativo spento, per le scritture |
| `auth.forbidden` | 403 | token senza ruolo riconosciuto |

Il formato degli ID è `^[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+$`, massimo 64 caratteri. Il backend non conosce il catalogo: valida il formato, non l'esistenza della tappa, così un rilascio frontend non richiede un rilascio backend. Le nuove chiavi e l'area `guidedTour` entrano nello standard degli errori e in `api-error-messages.ts` insieme all'implementazione, perché `check-error-catalog.mjs` respinge chiavi documentate ma non ancora lanciate.

## Frontend

### Componenti

| Elemento | Percorso proposto | Ruolo |
| --- | --- | --- |
| Catalogo | `src/app/constants/guided-tour-steps.ts` | tappe, testi, `GUIDED_TOUR_CATALOG_REVISION` |
| Modelli | `src/app/module/guided-tour.module.ts` | DTO e tipi della tappa |
| Servizio | `src/app/service/guided-tour.service.ts` | stato con signal, API, regole di avvio, composizione delle tappe |
| Ancora | `src/app/directive/tour-anchor.directive.ts` | `[appTourAnchor]="'calendar.header'"` scrive `data-tour-anchor` |
| Guida | `src/app/components/guided-tour/` | velo, riquadro, scheda, avanzamento |
| Invito | stesso componente, presentazione banner con `app-inline-alert` | utenti esistenti |
| Aiuto | `topbar.component` | pulsante e menu |
| Profilo | `profile.component` | sezione **Guida** con `app-detail-section` |

Il componente è montato una sola volta in `LayoutComponent` dentro `@defer (when guidedTour.active())`: catalogo e componente formano un chunk separato e non entrano nel bundle iniziale. Il servizio legge `GET /api/guided-tour` una volta, dopo che `TenantStatusService` conferma un tenant provisionato.

### Ancore

Le ancore sono identificativi stabili, non selettori CSS. Le voci del menu ricevono l'ancora tramite un campo aggiuntivo del modello, reso come attributo da `app-menu-item`; le altre pagine usano la direttiva. Un'ancora assente, nascosta o fuori schermo non è un errore: la tappa passa all'ancora candidata successiva e infine alla scheda centrata.

Prima di mostrare una tappa con `route`, il servizio naviga con il router, quindi attende l'ancora per al massimo 2 secondi. Se la navigazione viene annullata, per esempio da `canDeactivateUnsavedChanges`, la guida va in pausa sulla tappa corrente senza perdere l'avanzamento.

### Evidenziazione

Il riquadro è un elemento a posizione fissa allineato al `getBoundingClientRect()` dell'ancora, con un'ombra estesa che oscura il resto della pagina. Si riallinea con `ResizeObserver`, scroll e resize, limitati a un aggiornamento per frame. L'ancora viene portata in vista con `scrollIntoView({ block: 'center' })` prima di misurarla.

La scheda è un componente proprio, non `p-dialog`, perché `p-dialog` ammette solo posizioni fisse rispetto alla finestra. `p-popover` ancorato con `show(event, target)` è un'alternativa valutata, ma non gestisce il trattenimento del fuoco e chiude la scheda al clic esterno.

### Salvataggio

- Ogni cambio di tappa aggiorna lo stato locale e invia `PUT /progress` con un ritardo di 1 secondo, accorpando i passaggi rapidi.
- Chiusura, salto e fine inviano subito.
- Un `guidedTour.versionConflict` provoca una nuova lettura, l'unione delle tappe viste e un solo nuovo invio, senza messaggi all'utente: capita con due schede aperte.
- Un errore di rete non interrompe la guida e non mostra toast; il salvataggio viene ritentato alla tappa successiva.
- Se `GET` fallisce la guida non parte; il pulsante di aiuto ritenta la lettura quando viene usato.
- Solo i comandi del profilo seguono la normale gestione degli errori di `ApiErrorService`, perché sono azioni esplicite.

### Standard di layout

La guida è un sesto canale rispetto ai cinque della sezione «Quale canale, quando» dello [Standard di layout Taurus](taurus-layout-standard.md): bloccante ma sempre rinunciabile e senza dati da raccogliere. All'implementazione lo standard riceve una sezione dedicata con i limiti della scheda descritti sopra, così che il componente non diventi un'eccezione non documentata.

## Sicurezza e privacy

- Persona e ruolo derivano sempre dal token; nessun endpoint accetta un utente o un ruolo dal client.
- Le tabelle non contengono tenant, testi liberi o dati dei moduli: solo interruttore, stato e ID delle tappe.
- Gli ID delle tappe sono validati per formato e numero; non vengono mai interpretati come HTML o percorsi.
- I testi del catalogo sono statici; nome della persona e dell'organizzazione sono inseriti con l'interpolazione Angular, mai con `innerHTML`.
- La guida non rivela funzioni non autorizzate: una tappa per un ruolo diverso non viene composta, e le rotte restano comunque protette da `canActivateAuthRole` e `tenantFeatureGuard`.
- Profilo e avanzamento vengono eliminati quando la persona non ha più appartenenze attive in `public.tenant_user_membership`. La verifica avviene dopo la rimozione da un tenant (`UsersServiceImpl.removeUserFromTenant`, che disattiva l'appartenenza) e dopo la cancellazione GDPR nel tenant (`DataErasureService.eraseUserData`).
- La cancellazione di un tenant non tocca le tabelle della guida.
- I log non contengono subject o ID tappa.

## Osservabilità

Contatori a bassa cardinalità, senza tenant né utente tra i tag:

- `taurus.guided_tour.decisions` con `role` e `outcome` tra `started`, `completed`, `skipped`, `restarted`, `auto_start_disabled`, `auto_start_enabled`;
- `taurus.guided_tour.invitations` con `outcome` tra `accepted` e `declined`.

I contatori servono a capire se la guida viene completata o saltata per ruolo. Non esiste un tracciamento per tappa né per persona.

## Prestazioni e leggerezza

- Nessuna nuova dipendenza npm o Maven.
- Bundle iniziale: solo servizio e direttiva, entro 3 kB compressi.
- Chunk differito di catalogo e componente: entro 20 kB compressi.
- Una sola lettura all'avvio della sessione e scritture accorpate.
- Nessuna immagine: icone PrimeIcons già caricate.
- Il riallineamento del riquadro non deve causare più di un ricalcolo di layout per frame.

## Strategia di test

### Backend

- `GET` senza profilo né avanzamento: default completi, nessuna scrittura;
- persona e ruolo ricavati dal token, campi omologhi del body ignorati o rifiutati;
- stessa persona con due ruoli: avanzamenti distinti;
- stessa persona in due tenant con lo stesso ruolo: avanzamento condiviso;
- validazione di stato, formato e numero delle tappe e di `autoProposals`;
- conflitto di versione su profilo e avanzamento;
- `DELETE /progress` limitato al ruolo del token;
- migrazione: profili `legacy_invitation` per le identità attive, nessun profilo per quelle cancellate, ripetibilità;
- endpoint raggiungibili anche con tenant non provisionato, grazie a `@TenantSchemaNotRequired`;
- cancellazione GDPR con e senza altre appartenenze attive;
- interruttore applicativo spento.

### Frontend

- composizione delle tappe per ognuno dei sei ruoli con tutti i flag accesi e tutti spenti, confrontata con un elenco atteso di ID;
- Tesoriere con e senza `FINANCE`;
- punti condizionati dai flag;
- regole di avvio: rotta, interruttore, stato, tre proposte, rinvio di sessione, dialogo aperto, invito;
- ripresa dall'ultima tappa;
- ancora mancante, nascosta o tardiva con ripiego sulla scheda centrata;
- navigazione annullata da modifiche non salvate;
- mobile sotto i 992 px con le ancore alternative;
- tastiera, trattenimento e ritorno del fuoco, `Esc`, annunci della regione live;
- `prefers-reduced-motion`, tema scuro, zoom al 200%;
- conflitto di versione con unione delle tappe viste;
- errore di `GET` che lascia l'applicazione utilizzabile;
- sezione del profilo: interruttore, apertura e ricomincio.

### Coerenza delle ancore

Uno script Node 22 senza dipendenze, `taurus-fe/scripts/check-tour-anchors.mjs`, confronta le ancore citate dal catalogo con quelle dichiarate nei template e nel modello del menu e fallisce se una manca. Viene eseguito nel job frontend di `verify.yml`. Protegge dalla deriva più probabile: una pagina rifatta che perde l'ancora della guida.

### Verifica manuale

- un giro completo per ruolo su desktop e su un telefono Android reale;
- lettura dei testi con una persona per ruolo dell'associazione pilota;
- verifica che nessuna tappa citi funzioni non visibili a quel ruolo.

## Criteri di accettazione

1. Al primo ingresso con un ruolo, dopo i documenti legali, la dashboard mostra la scheda di benvenuto.
2. La guida non si apre da sola su pagine diverse dalla dashboard.
3. Ogni ruolo vede soltanto tappe pertinenti al ruolo e alle funzionalità attive del tenant.
4. Nessuna guida supera 10 tappe, benvenuto compreso.
5. Ogni tappa evidenzia la propria ancora se visibile, altrimenti usa la scheda centrata senza errori.
6. Su schermi sotto i 992 px ogni tappa resta leggibile e utilizzabile.
7. Chiudere la guida conserva l'ultima tappa e la successiva apertura riprende da lì.
8. Dopo 3 proposte automatiche chiuse la guida non si apre più da sola per quel ruolo.
9. **Salta la guida** chiude la guida del solo ruolo corrente.
10. **Non avviare più la guida automaticamente** impedisce ogni avvio automatico, per tutti i ruoli e i tenant della persona.
11. L'interruttore del profilo riattiva l'avvio automatico.
12. Il pulsante di aiuto apre la guida in ogni momento, anche con avvio automatico spento.
13. **Ricomincia la guida** azzera solo lo stato del ruolo corrente.
14. La stessa persona non rivede la guida dello stesso ruolo entrando in un altro tenant.
15. Gli utenti esistenti al rilascio vedono un invito e non la guida automatica.
16. Persona e ruolo provengono sempre dal token.
17. Un errore delle API della guida non impedisce l'uso di Taurus.
18. La guida è utilizzabile da tastiera e con lettore di schermo.
19. Il bundle iniziale cresce al massimo di 3 kB compressi.
20. Lo script delle ancore fallisce se il catalogo cita un'ancora inesistente.
21. La cancellazione GDPR rimuove i dati della guida quando la persona non ha più appartenenze attive.

## Piano di rilascio

### Fase 1 — Guida introduttiva

- tabelle, migrazione con invito, API e interruttore applicativo;
- servizio, catalogo, direttiva, componente e pulsante di aiuto;
- tappe dei sei ruoli e sezione del profilo;
- script delle ancore, test e aggiornamento dello standard di layout e del catalogo errori.

### Fase 2 — Guide di pagina

- tappe con ambito di pagina, aperte solo da **Guida di questa pagina**;
- prime pagine: dashboard, calendario e dettaglio evento, tracce e dettaglio, album, inventario e assegnazione, economia, utenti, profilo;
- approfondimento delle funzioni escluse dal percorso principale dell'Amministratore e del Super amministratore.

### Fase 3 — Novità

- dopo un rilascio, punto informativo sul pulsante di aiuto e voce **Novità** con le sole tappe nuove;
- sono novità le tappe applicabili non viste con `introducedIn` maggiore della `catalogRevision` registrata; per un ruolo `COMPLETED` anche le tappe diventate applicabili per una funzionalità attivata dopo;
- mai un'apertura automatica della guida completa.

## Evoluzioni successive

- tappe pratiche che si completano quando l'utente svolge davvero l'azione, per esempio la prima disponibilità;
- lista «Primi passi» sulla dashboard per gli amministratori di un tenant appena creato;
- guida per la modalità bootstrap del Super amministratore;
- testi in altre lingue;
- interruttore del tenant per gli amministratori, se richiesto dalle associazioni.

## Criticità

| Criticità | Rischio | Mitigazione proposta |
| --- | --- | --- |
| Nomi già usati | «Onboarding guidato», «Configurazione iniziale» e «Primo accesso» indicano già altro | nome **Guida**, prefisso `guided-tour`, vocabolario dei testi vincolato |
| Ruolo scelto all'accesso | una persona con più ruoli vedrebbe guide sbagliate o ripetute | stato per persona e ruolo, ruolo letto dal token |
| Persona in più tenant | con lo stato per tenant la guida si ripeterebbe in ogni organizzazione, Super amministratore incluso | stato globale nello schema `public` |
| Nessun indicatore di primo accesso | al rilascio tutti gli utenti esistenti riceverebbero la guida automatica | profilo con invito creato dalla migrazione |
| Stato nel browser non affidabile | `localStorage` svuotato alla chiusura e senza separazione per utente | backend come fonte; `sessionStorage` solo per il rinvio di sessione |
| Ancore fragili | rotte lazy, menu per ruolo e flag, contenuti asincroni e layout mobile | identificativi stabili, candidate multiple, attesa limitata, scheda centrata, script di controllo |
| Testi che invecchiano | una pagina cambia e la guida descrive un'interfaccia che non esiste più | catalogo accanto al codice, script delle ancore, voce nel template di pull request |
| Tesoriere senza Economia | il ruolo non ha quasi nulla da usare e la guida sarebbe vuota | tappa dedicata che spiega a chi chiedere |
| Percorsi lunghi per gli amministratori | troppe tappe rendono la guida pesante e verrebbe saltata | limite di 10, tappa unica per gli strumenti, guide di pagina in fase 2 |
| Interferenze con il flusso esistente | documenti legali, collegamenti diretti, dialoghi, modifiche non salvate, aggiornamento dell'applicazione | avvio solo sulla dashboard, condizioni di avvio, pausa su navigazione annullata, ripresa dall'ultima tappa |
| Pulsante di aiuto conteso | la specifica delle richieste di assistenza usa la stessa icona nella topbar | un solo pulsante di aiuto con menu, da riportare nella specifica di assistenza |
| Nuovo canale di interfaccia | lo standard di layout ne prevede cinque | sezione dedicata nello standard all'implementazione |
| Dati globali e GDPR | la cancellazione oggi avviene per tenant | eliminazione quando cade l'ultima appartenenza attiva |
| Accessibilità dell'evidenziazione | l'oscuramento non dice nulla a chi usa un lettore di schermo | testi autosufficienti, dialogo accessibile, annunci |
| Utente esterno | testi pensati per l'Utente prometterebbero eventi e brani non visibili | varianti di testo per ruolo e verifica manuale |
| Telefoni modesti | molti musicisti usano Taurus da telefono | nessuna libreria, chunk differito, un ricalcolo per frame |
| Due schede aperte | salvataggi concorrenti dello stesso avanzamento | lock ottimistico e unione delle tappe viste |

## Punti aperti

Ogni punto riporta la proposta di questa specifica; la decisione resta da confermare prima di passare a `approved`.

1. **Stato globale o per tenant.** Proposta: globale per persona e ruolo, nello schema `public`. L'alternativa per tenant segue il modello di `notification_profile` e semplifica la cancellazione, ma ripete la guida in ogni organizzazione.
2. **Utenti esistenti al rilascio.** Proposta: invito discreto una sola volta per ruolo. Alternative: guida automatica per tutti oppure nessuna proposta.
3. **Numero di proposte automatiche.** Proposta: 3 per ruolo, poi solo il punto informativo sul pulsante di aiuto.
4. **Interruttore unico o per ruolo.** Proposta: unico per persona; la scelta per ruolo è già coperta da **Salta la guida**.
5. **Controllo dell'amministratore.** Proposta: nessun flag del tenant nella prima versione, solo l'interruttore applicativo. Da verificare se le associazioni vogliono spegnere la guida per tutti i propri membri.
6. **Guide di pagina.** Proposta: fase 2. Includerle nella prima versione rende la guida più completa ma raddoppia contenuti e test.
7. **Novità.** Proposta: fase 3, da confermare o rinviare.
8. **Validazione dei testi.** Proposta: una persona per ruolo dell'associazione pilota rilegge le tappe prima del rilascio. Va individuato chi.
9. **Modalità bootstrap.** Proposta: esclusa; l'avviso «Primo accesso» nella pagina Tenant e il runbook bastano al Super amministratore.
10. **Metriche.** Proposta: soli contatori aggregati per ruolo ed esito. Da confermare che siano accettabili anche questi.
11. **Pulsante di aiuto condiviso.** Proposta: un solo pulsante con menu; la specifica delle richieste di assistenza va allineata.
12. **Tappe solo descrittive.** Proposta: nella prima versione la guida mostra e non fa compiere azioni reali.
13. **Lingua.** Proposta: solo italiano, come il resto dell'interfaccia.
