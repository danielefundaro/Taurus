package com.fundaro.zodiac.taurus.domain.onboarding;

import java.util.List;

public enum OnboardingSection {
    INSTRUMENTS("Strumenti", List.of("riferimento", "nome", "descrizione")),
    USERS("Utenti", List.of("riferimento", "nome", "cognome", "email", "data_nascita", "ruoli", "strumenti", "attivo")),
    INVENTORY("Inventario", List.of("numero_inventario", "nome", "descrizione", "quantita_totale", "valore_unitario_stimato", "valuta", "condizione", "note_condizione")),
    CATEGORIES("Categorie", List.of("nome", "descrizione", "direzione", "ordine")),
    ACCOUNTS("Conti", List.of("riferimento", "nome", "descrizione", "tipo", "valuta", "iban", "banca", "ordine")),
    OPENING_BALANCES("Saldi iniziali", List.of("conto", "data", "importo"));

    private final String sheetName;
    private final List<String> headers;
    OnboardingSection(String sheetName, List<String> headers) { this.sheetName = sheetName; this.headers = headers; }
    public String getSheetName() { return sheetName; }
    public List<String> getHeaders() { return headers; }
    public static OnboardingSection fromSheet(String name) {
        for (OnboardingSection value : values()) if (value.sheetName.equals(name)) return value;
        return null;
    }
}
