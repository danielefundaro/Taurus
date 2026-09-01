# Stile editoriale delle notifiche interne

## Obiettivo

Questa specifica definisce lo stile editoriale comune per tutte le notifiche interne di Taurus. Deve essere usata come riferimento quando si aggiunge o si modifica una notifica, indipendentemente dall'ambito funzionale e dai destinatari.

L'obiettivo è rendere ogni notifica immediatamente riconoscibile, sintetica e coerente nelle scelte lessicali, nella punteggiatura e nella rappresentazione dei dati.

## Centralizzazione nel backend

Le notifiche interne generate dagli eventi applicativi sono centralizzate nel backend e hanno come unico punto di composizione `NoticesAspect`.

In particolare:

- `NoticesAspect` definisce il titolo, la descrizione e i destinatari della notifica;
- i service applicativi, i controller e gli scheduler eseguono esclusivamente la logica di dominio e non compongono né inviano direttamente notifiche;
- quando una notifica deriva da un processo schedulato o da un salvataggio tecnico, l'aspect intercetta l'evento pertinente e genera la notifica;
- `NoticesService` mantiene la responsabilità tecnica di distribuire e persistere le notifiche, ma non ne definisce il contenuto editoriale;
- il frontend visualizza il titolo e la descrizione ricevuti dal backend senza ricostruirli o modificarne il significato.

Ogni nuova notifica interna deve quindi essere aggiunta all'aspect seguendo i modelli editoriali descritti in questo documento. Eventuali servizi di supporto possono fornire all'aspect dati contestuali, ma non devono contenere testi o regole editoriali delle notifiche.

## Principi generali

- Il titolo classifica l'evento; la descrizione spiega cosa è successo.
- Il testo usa un tono neutro, informativo e non promozionale.
- Le frasi sono brevi e prive di dettagli tecnici non utili al destinatario.
- La forma attiva è preferita quando l'autore dell'azione è conosciuto.
- Una notifica rivolta direttamente a un utente usa la seconda persona.
- Titolo e descrizione sono testo semplice: non contengono HTML o Markdown.
- Non si usano punti esclamativi.
- Valori assenti non devono mai produrre testi come `null`, stringhe vuote o segnaposto tecnici.

## Titolo

Il titolo segue il formato:

```text
<Ambito>: <evento>
```

Regole:

- iniziale maiuscola e sentence case;
- nessun punto finale;
- nessun nome proprio o identificativo specifico;
- evento espresso, quando possibile, con un participio passato;
- formulazione breve, idealmente entro 50 caratteri;
- lo stesso evento usa sempre lo stesso verbo in ogni ambito.

Esempi:

- `Album: creato`
- `Traccia: pubblicata`
- `Evento: aggiornato`
- `Evento: disponibilità confermata`
- `Utente: rimosso`
- `Tenant: aggiornato`
- `Inventario: oggetto assegnato`
- `Inventario: assegnazione scaduta`

Da evitare:

- `Nuovo album creato`, perché combina due indicazioni equivalenti;
- `Aggiornamento`, perché non identifica l'entità interessata;
- `ATTENZIONE! Scadenza`, perché usa maiuscole enfatiche e punteggiatura non necessaria.

## Descrizione

### Notifica amministrativa o condivisa

Quando l'autore è conosciuto, usare il modello:

```text
<Attore> ha <azione> <entità> “<identificativo>” [dettagli].
```

Esempio:

```text
Mario Rossi ha aggiornato l'evento “Prova generale”.
```

Quando l'azione è automatica o l'autore non è rilevante, iniziare dall'entità:

```text
L'assegnazione dell'oggetto “INV-0042 — Microfono Shure” scadrà il 30/09/2026.
```

### Notifica rivolta al destinatario

Usare la seconda persona e il modello:

```text
La tua <entità> “<identificativo>” <evento>.
```

Esempio:

```text
La tua assegnazione dell'oggetto “INV-0042 — Microfono Shure” scadrà il 30/09/2026.
```

### Regole di composizione

- La descrizione inizia con una lettera maiuscola e termina sempre con un punto.
- Ogni descrizione contiene una frase principale completa.
- Sono ammesse al massimo due frasi brevi.
- Nomi, titoli e identificativi leggibili dall'utente sono racchiusi tra virgolette tipografiche `“…”`.
- Gli identificativi tecnici sono omessi, salvo quando costituiscono un riferimento operativo utile.
- Le date assolute usano il formato `gg/mm/aaaa`.
- Le quantità riportano sempre l'unità, per esempio `2 unità`.
- La motivazione, quando presente e non vuota, è una seconda frase nel formato `Motivazione: …`.
- Se una parte facoltativa non è disponibile, viene omessa insieme alla relativa punteggiatura.

## Attori e destinatari

- Per le notifiche amministrative usare il nome completo dell'autore: `Mario Rossi ha aggiornato…`.
- Se il nome non è disponibile, usare un identificativo leggibile oppure una formulazione impersonale; non mostrare UUID o valori tecnici non comprensibili.
- Per le notifiche automatiche non è necessario anteporre `Il sistema ha…`: descrivere direttamente l'evento.
- Per le notifiche personali usare `la tua`, `il tuo` o una costruzione equivalente.
- I nomi degli altri utenti non sono racchiusi tra virgolette.

## Rappresentazione delle entità

| Entità | Formato nella descrizione | Esempio |
| --- | --- | --- |
| Album | `l'album “<nome>”` | `l'album “Live 2026”` |
| Traccia | `la traccia “<nome>”` | `la traccia “Marcia sinfonica”` |
| Evento | `l'evento “<nome>”` | `l'evento “Prova generale”` |
| Strumento | `lo strumento “<nome>”` | `lo strumento “Clarinetto”` |
| Utente | nome e cognome senza virgolette | `Luca Bianchi` |
| Tenant | `il tenant “<nome>”` | `il tenant “Banda cittadina”` |
| Oggetto inventario | `l'oggetto “<numero> — <nome>”` | `l'oggetto “INV-0042 — Microfono Shure”` |

Per gli oggetti di inventario si usa sempre il formato `numero — nome`. Il solo nome è ammesso esclusivamente quando il numero non esiste; non si alternano formati diversi per lo stesso oggetto.

## Vocabolario delle azioni

Per evitare sinonimi non intenzionali, usare i seguenti verbi:

| Evento | Verbo consigliato |
| --- | --- |
| Creazione | `creato` / `creata` |
| Modifica | `aggiornato` / `aggiornata` |
| Eliminazione | `rimosso` / `rimossa` |
| Pubblicazione | `pubblicato` / `pubblicata` |
| Assegnazione | `assegnato` / `assegnata` |
| Conferma | `confermato` / `confermata` |
| Rifiuto | `rifiutato` / `rifiutata` |
| Scadenza futura | `in scadenza` oppure `scadrà` |
| Scadenza raggiunta | `scaduto` / `scaduta` |
| Riconsegna | `riconsegnato` / `riconsegnata` oppure `riconsegna completata` |

Non alternare `aggiunto`, `inserito` e `creato` per descrivere la stessa operazione. Eventuali eccezioni devono dipendere dal significato funzionale, non dall'ambito in cui è implementata la notifica.

## Esempi completi

### Creazione di un album

Titolo:

```text
Album: creato
```

Descrizione:

```text
Mario Rossi ha creato l'album “Live 2026”.
```

### Conferma della disponibilità

Titolo:

```text
Evento: disponibilità confermata
```

Descrizione:

```text
Luca Bianchi ha confermato la disponibilità per l'evento “Prova generale”.
```

### Assegnazione di un oggetto

Titolo:

```text
Inventario: oggetto assegnato
```

Descrizione:

```text
Mario Rossi ha assegnato 2 unità dell'oggetto “INV-0042 — Microfono Shure” a Luca Bianchi.
```

### Avviso personale di scadenza

Titolo:

```text
Inventario: assegnazione in scadenza
```

Descrizione:

```text
La tua assegnazione dell'oggetto “INV-0042 — Microfono Shure” scadrà il 30/09/2026.
```

### Presa visione rifiutata

Titolo:

```text
Inventario: presa visione rifiutata
```

Descrizione:

```text
Luca Bianchi ha rifiutato la revisione 3 dell'assegnazione dell'oggetto “INV-0042 — Microfono Shure”. Motivazione: il materiale risulta danneggiato.
```

Se la motivazione non è disponibile, la seconda frase viene omessa completamente.

## Checklist per nuove notifiche

Prima di introdurre o modificare una notifica, verificare che:

- il titolo rispetti il formato `<Ambito>: <evento>`;
- il titolo non termini con un punto;
- la descrizione termini con un punto;
- la forma attiva identifichi l'autore, quando conosciuto;
- una notifica personale si rivolga direttamente al destinatario;
- entità e identificativi seguano i formati definiti in questa specifica;
- date, quantità e motivazioni siano formattate in modo uniforme;
- nessun valore assente possa apparire come `null` o come testo incompleto;
- non siano presenti HTML, Markdown, abbreviazioni tecniche o enfasi non necessarie.
