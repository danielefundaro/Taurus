package com.fundaro.zodiac.taurus.utils.pdf;

import java.util.List;

/**
 * Ricetta di trasformazione di una singola pagina, indipendente dal livello di servizio.
 *
 * <p>Le annotazioni PDF persistite vengono deserializzate in questo modello, che descrive soltanto
 * le operazioni da applicare all'immagine. La validazione dei valori e la conversione dai DTO
 * esposti via HTTP restano responsabilità del livello di servizio: qui non sono presenti vincoli
 * di bean validation, così il pacchetto {@code utils} non dipende dai contratti applicativi.
 *
 * <p>La lista dei ritagli non viene normalizzata: un valore nullo resta nullo e viene respinto
 * dalla validazione del servizio, come per qualsiasi altra ricetta non valida.
 */
public record PageEditRecipe(
    int recipeVersion,
    int rotationQuarterTurns,
    double deskewDegrees,
    List<Crop> crops,
    boolean grayscale,
    int brightness,
    int contrast,
    boolean autoContrast,
    Integer threshold
) {
    /** Ritaglio espresso in frazioni della larghezza e dell'altezza della pagina. */
    public record Crop(double x, double y, double width, double height) {}
}
