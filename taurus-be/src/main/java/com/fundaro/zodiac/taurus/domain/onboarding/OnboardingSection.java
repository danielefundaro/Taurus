package com.fundaro.zodiac.taurus.domain.onboarding;

import java.util.List;

public enum OnboardingSection {
    /** Sound and transposition columns were added later: files prepared before can omit them. */
    INSTRUMENTS("Strumenti", List.of("riferimento", "nome", "descrizione", "suono", "trasposizione", "trasposizione_chiave_basso"), 3),
    USERS("Utenti", List.of("riferimento", "nome", "cognome", "email", "data_nascita", "ruoli", "strumenti", "attivo")),
    INVENTORY("Inventario", List.of("numero_inventario", "nome", "descrizione", "quantita_totale", "valore_unitario_stimato", "valuta", "condizione", "note_condizione")),
    CATEGORIES("Categorie", List.of("nome", "descrizione", "direzione", "ordine")),
    ACCOUNTS("Conti", List.of("riferimento", "nome", "descrizione", "tipo", "valuta", "iban", "banca", "ordine")),
    OPENING_BALANCES("Saldi iniziali", List.of("conto", "data", "importo"));

    private final String sheetName;
    private final List<String> headers;
    private final int optionalTrailingHeaders;

    OnboardingSection(String sheetName, List<String> headers) {
        this(sheetName, headers, 0);
    }

    OnboardingSection(String sheetName, List<String> headers, int optionalTrailingHeaders) {
        this.sheetName = sheetName;
        this.headers = headers;
        this.optionalTrailingHeaders = optionalTrailingHeaders;
    }

    public String getSheetName() {
        return sheetName;
    }

    public List<String> getHeaders() {
        return headers;
    }

    /** The headers of the template, or the same headers without the optional columns at the end. */
    public boolean acceptsHeaders(List<String> actual) {
        return actual.equals(headers) || (optionalTrailingHeaders > 0 && actual.equals(headers.subList(0, headers.size() - optionalTrailingHeaders)));
    }

    public static OnboardingSection fromSheet(String name) {
        for (OnboardingSection value : values()) if (value.sheetName.equals(name)) return value;
        return null;
    }
}
