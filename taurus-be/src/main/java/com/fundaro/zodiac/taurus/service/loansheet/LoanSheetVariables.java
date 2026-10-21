package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Variable;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.VariableGroup;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.VariableType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalog of the variables available to a loan sheet template. It is the single definition used by the validation of
 * the uploaded templates, by {@link LoanSheetModelFactory} and by the guide of the template page.
 */
public final class LoanSheetVariables {

    public static final String SHEET_NUMBER = "foglio.numero";
    public static final String GOODS = "beni";
    public static final String LOGO = "associazione.logo";
    public static final String ELECTRONIC = "firma.elettronica";
    public static final String SIGNED_NOTE = "Firmato elettronicamente – vedi attestazione";
    public static final List<String> DURATIONS = List.of(
        "durata.mesiMinimi",
        "durata.mesiMassimi",
        "durata.mesiRinnovoMassimi",
        "durata.mesiComplessiviMassimi"
    );

    public static final List<VariableGroup> GROUPS = List.of(
        group(
            "Associazione",
            "Dati del tenant, letti dall'anagrafica dell'associazione.",
            text("associazione.nome", "Denominazione del tenant", "Associazione Musicale Esempio APS"),
            text("associazione.indirizzo", "Indirizzo, CAP, città e provincia", "Via Roma 10, 00100 Roma (RM)"),
            text("associazione.email", "E-mail del tenant", "info@example.org"),
            text("associazione.telefono", "Telefono del tenant", "06 1234567"),
            text("associazione.pec", "PEC del tenant", "associazione@pec.example.org"),
            text("associazione.recapiti", "E-mail, telefono e PEC presenti, separati da virgola", "info@example.org, tel. 06 1234567, PEC associazione@pec.example.org"),
            text("associazione.partitaIva", "Partita IVA", "01234567890"),
            text("associazione.codiceFiscale", "Codice fiscale", "91234567890"),
            text("associazione.datiFiscali", "«P. IVA …, C.F. …», omettendo le parti mancanti", "P. IVA 01234567890, C.F. 91234567890"),
            text(LOGO, "Logo del tenant come data: URI, da usare nell'attributo src di un'immagine; vuoto se il tenant non ha logo", "data:image/png;base64,…")
        ),
        group(
            "Foglio",
            "Dati registrati all'emissione del foglio.",
            text(SHEET_NUMBER, "Numero del foglio; vuoto nelle anteprime", "2026/007"),
            text("foglio.data", "Data del foglio", "28/09/2026"),
            text("foglio.luogo", "Luogo del foglio", "Roma"),
            text("foglio.luogoEData", "Luogo e data separati da virgola", "Roma, 28/09/2026"),
            text("foglio.delibera", "Numero della delibera o del provvedimento di consegna", "7"),
            text("foglio.dataDelibera", "Data della delibera", "25/09/2026"),
            text("foglio.firmatario", "Nome e cognome di chi firma per l'Associazione", "Mario Bianchi"),
            flag("foglio.anteprima", "Vale sì nelle anteprime")
        ),
        group(
            "Socio, Affidatario e minore",
            "Dati del socio già presenti in Taurus. Gli altri dati anagrafici si compilano a mano sul foglio di carta; nel foglio firmato elettronicamente li inserisce l'Affidatario prima della firma.",
            text("socio.nome", "Nome del socio", "Giulia"),
            text("socio.cognome", "Cognome del socio", "Rossi"),
            text("socio.nomeCognome", "Nome e cognome del socio", "Giulia Rossi"),
            text("socio.dataNascita", "Data di nascita del socio", "14/03/2010"),
            text("socio.email", "E-mail del socio", "giulia.rossi@example.org"),
            flag("affidatario.socioMaggiorenne", "L'Affidatario è il socio maggiorenne"),
            flag("affidatario.genitoreTutore", "L'Affidatario è il genitore o tutore del socio minorenne"),
            text("affidatario.nomeCognome", "Nome e cognome del socio se è l'Affidatario; vuoto se l'Affidatario è il genitore o tutore", "Giulia Rossi"),
            text("affidatario.dataNascita", "Data di nascita del socio se è l'Affidatario", "14/03/1990"),
            text("affidatario.email", "E-mail del socio se è l'Affidatario", "giulia.rossi@example.org"),
            text("affidatario.luogoNascita", "Luogo di nascita, inserito dall'Affidatario prima della firma elettronica; vuoto su carta", "Roma"),
            text("affidatario.residenza", "Indirizzo di residenza (via o piazza e numero), inserito prima della firma elettronica", "Via Garibaldi 5"),
            text("affidatario.cap", "CAP di residenza, inserito prima della firma elettronica", "00100"),
            text("affidatario.cittaProvincia", "Città e provincia di residenza, inserite prima della firma elettronica", "Roma (RM)"),
            text("affidatario.codiceFiscale", "Codice fiscale, inserito prima della firma elettronica; vuoto se l'Affidatario dichiara di non averne uno italiano", "RSSGLI90C54H501X"),
            text("affidatario.documento", "Tipo e numero del documento di identità, inseriti prima della firma elettronica", "Carta d'identità CA00000AA"),
            text("affidatario.documentoRilasciatoDa", "Ente che ha rilasciato il documento, inserito prima della firma elettronica", "Comune di Roma"),
            text("affidatario.documentoRilasciatoIl", "Data di rilascio del documento, inserita prima della firma elettronica", "02/05/2022"),
            text("affidatario.telefono", "Telefono, facoltativo, inserito prima della firma elettronica", "333 1234567"),
            text("minore.nomeCognome", "Nome e cognome del socio se l'Affidatario è il genitore o tutore; vuoto altrimenti", "Luca Rossi"),
            text("minore.dataNascita", "Data di nascita del socio se l'Affidatario è il genitore o tutore", "14/03/2014"),
            text("minore.luogoNascita", "Luogo di nascita del socio minorenne, inserito dal genitore prima della firma elettronica", "Roma")
        ),
        group(
            "Firma elettronica",
            "Valori dei documenti firmati elettronicamente: ogni atto (emissione, variazione, riconsegna) è un documento a sé, con le sole parti del modello marcate con data-taurus-act.",
            flag(ELECTRONIC, "Il documento è un atto da firmare elettronicamente; vale no su carta e nelle copie di consultazione"),
            text("firma.dicitura", "Dicitura da stampare al posto delle righe per la firma", SIGNED_NOTE),
            text("firma.giorniDisponibilita", "Giorni in cui gli atti firmati restano scaricabili dopo la definizione o l'annullamento del foglio", "30"),
            flag("atto.emissione", "Il documento è l'emissione del foglio"),
            flag("atto.variazione", "Il documento è una variazione del registro: registro contiene solo le sue righe"),
            flag("atto.riconsegna", "Il documento è il verbale di riconsegna")
        ),
        group(
            "Beni consegnati",
            "Righe della sezione 3, congelate all'emissione.",
            list(
                GOODS,
                "Un elemento per bene consegnato",
                text("numero", "Numero progressivo della riga", "1"),
                text("nome", "Nome dell'oggetto", "Clarinetto in Si bemolle"),
                text("marcaModello", "Marca e modello", "Yamaha YCL-255"),
                text("matricola", "Matricola", "SN-004512"),
                text("numeroInventario", "Numero inventariale", "INV-0001"),
                text("matricolaInventario", "«matricola / numero inventariale», oppure il solo numero inventariale", "SN-004512 / INV-0001"),
                text("accessori", "Accessori o taglia", "Custodia, bocchino, legatura"),
                text("quantita", "Quantità in carico all'emissione", "1"),
                text("valoreUnitario", "Valore unitario stimato dell'oggetto all'emissione, se espresso in euro", "75,00"),
                text("valoreConcordato", "Valore concordato: valore stimato dell'oggetto in euro, totale seguito dall'unitario se la quantità è maggiore di 1", "150,00 (75,00 cad.)"),
                text("stato", "Stato di conservazione alla consegna", "Buono"),
                text("note", "Note di conservazione alla consegna", "Graffio sul barilotto"),
                text("statoENote", "Stato e note di conservazione separati da trattino", "Buono – Graffio sul barilotto"),
                flag("aScadenza", "Bene in comodato con scadenza"),
                flag("precario", "Bene in comodato precario"),
                text("scadenza", "Data di scadenza, solo per il comodato a scadenza", "30/09/2027")
            ),
            list(
                "beniRigheVuote",
                "Righe vuote necessarie per arrivare a 14 righe totali",
                text("numero", "Numero progressivo della riga", "4")
            )
        ),
        group(
            "Durata",
            "Regime dei beni e regole di durata del comodato.",
            flag("durata.aScadenza", "Il foglio contiene almeno un bene in comodato a scadenza"),
            flag("durata.precario", "Il foglio contiene almeno un bene in comodato precario"),
            text("durata.scadenzaStrumenti", "«del gg/mm/aaaa» con la scadenza comune degli strumenti, «indicata per ciascuno strumento nella sezione 3» se le scadenze sono diverse, «del ____ / ____ / ________» senza strumenti", "del 30/09/2027"),
            text("durata.scadenzaUnica", "Scadenza comune a tutti gli strumenti; vuota se non ci sono strumenti o se le scadenze sono diverse", "30/09/2027"),
            flag("durata.scadenzeDiverse", "Gli strumenti hanno scadenze diverse"),
            text("durata.mesiMinimi", "Durata minima del comodato a scadenza, in mesi", "12"),
            text("durata.mesiMassimi", "Durata massima del comodato a scadenza, in mesi", "24"),
            text("durata.mesiRinnovoMassimi", "Estensione massima di un rinnovo, in mesi", "24"),
            text("durata.mesiComplessiviMassimi", "Durata complessiva massima per strumento, in mesi", "36")
        ),
        group(
            "Registro delle variazioni",
            "Righe della sezione 8, in ordine cronologico.",
            list(
                "registro",
                "Un elemento per variazione",
                text("data", "Data della variazione", "15/11/2026"),
                text("bene", "Bene e quantità; per le sostituzioni, anche il bene sostituito", "Leggio pieghevole"),
                text("marcaModelloMatricola", "Marca, modello e matricola o numero inventariale", "INV-0340"),
                text("operazione", "C (consegna), S (sostituzione), R (riconsegna parziale) o N (rinnovo)", "C"),
                text("scadenza", "Scadenza dello strumento", "30/09/2027"),
                text("valoreConcordato", "Valore concordato: valore stimato dell'oggetto in euro", "25,00"),
                text("delibera", "Per i rinnovi, estremi della delibera su due righe; da stampare con white-space: pre-line", "Delibera n. 12")
            ),
            list("registroRigheVuote", "Righe vuote necessarie per arrivare a 21 righe totali, senza campi")
        ),
        group(
            "Riconsegna",
            "Verbale della sezione 9, valorizzato quando il foglio è chiuso.",
            flag("riconsegna.registrata", "Il foglio è chiuso e la riconsegna finale è registrata"),
            text("riconsegna.data", "Data della riconsegna finale", "10/09/2027"),
            list(
                "riconsegna.beni",
                "Beni restituiti nella riconsegna finale",
                text("numero", "Numero progressivo della riga", "1"),
                text("bene", "Bene restituito", "Clarinetto in Si bemolle"),
                text("matricolaInventario", "Matricola o numero inventariale", "SN-004512 / INV-0001"),
                text("restituito", "«Sì», oppure «No» con la quantità mancante", "Sì"),
                text("stato", "Stato alla riconsegna", "Buono"),
                text("note", "Note della riconsegna", "Ancia da sostituire")
            ),
            list(
                "riconsegna.righeVuote",
                "Righe vuote necessarie per arrivare a 14 righe totali",
                text("numero", "Numero progressivo della riga", "2")
            ),
            flag("riconsegna.senzaAddebiti", "Esito senza addebiti"),
            flag("riconsegna.conAddebiti", "Esito con addebito"),
            text("riconsegna.importo", "Importo dell'addebito", "1.250,00"),
            text("riconsegna.termine", "Termine di pagamento dell'addebito", "30/09/2027"),
            text("riconsegna.ricevutoDa", "Nome e cognome di chi riceve i beni per l'Associazione", "Mario Bianchi")
        )
    );

    private static final Map<String, Variable> BY_NAME = index();

    private LoanSheetVariables() {
    }

    /**
     * Returns the variable with the given full name, for example {@code associazione.nome} or {@code beni}.
     */
    public static Optional<Variable> find(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /**
     * Returns the names of the fields of a list, or an empty set for a name that is not a list.
     */
    public static Set<String> fields(String listName) {
        Variable variable = BY_NAME.get(listName);
        if (variable == null || variable.type() != VariableType.LIST) return Set.of();
        Set<String> names = new LinkedHashSet<>();
        variable.fields().forEach(field -> names.add(field.name()));
        return names;
    }

    private static Map<String, Variable> index() {
        Map<String, Variable> result = new LinkedHashMap<>();
        GROUPS.forEach(group -> group.variables().forEach(variable -> result.put(variable.name(), variable)));
        return result;
    }

    private static VariableGroup group(String label, String description, Variable... variables) {
        return new VariableGroup(label, description, List.of(variables));
    }

    private static Variable text(String name, String description, String example) {
        return new Variable(name, VariableType.TEXT, description, example, List.of());
    }

    private static Variable flag(String name, String description) {
        return new Variable(name, VariableType.FLAG, description, null, List.of());
    }

    private static Variable list(String name, String description, Variable... fields) {
        return new Variable(name, VariableType.LIST, description, null, List.of(fields));
    }
}
