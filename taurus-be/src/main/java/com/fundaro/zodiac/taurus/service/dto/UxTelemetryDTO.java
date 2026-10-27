package com.fundaro.zodiac.taurus.service.dto;

import java.util.Map;

/**
 * Aggregato di uso spedito dal frontend alla fine di una sessione di lavoro su una funzione.
 *
 * <p>Porta solo nomi di eventi previsti dall'interfaccia, i loro conteggi e il tempo trascorso:
 * nessun contenuto, nessun identificativo di entita, nessun testo cercato dall'utente.
 *
 * @param feature identificativo della funzione nel catalogo, per esempio {@code track-parts-workspace}
 * @param durationMs millisecondi dalla prima apertura della funzione
 * @param events conteggio per nome di evento
 */
public record UxTelemetryDTO(String feature, long durationMs, Map<String, Integer> events) {}
