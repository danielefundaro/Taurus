package com.fundaro.zodiac.taurus.service;

/**
 * La regola di ricorrenza genererebbe più occorrenze del limite della serie.
 *
 * <p>È una regola incompleta solo in senso lato: l'utente ha chiesto qualcosa di valido ma troppo
 * ampio, e il messaggio deve dirgli il limite invece di parlare di regola non valida.
 */
public class OccurrenceLimitExceededException extends IllegalArgumentException {

    private final int maximum;

    public OccurrenceLimitExceededException(int maximum) {
        super("The recurrence exceeds the maximum of " + maximum + " occurrences");
        this.maximum = maximum;
    }

    public int maximum() {
        return maximum;
    }
}
