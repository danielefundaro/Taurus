package com.fundaro.zodiac.taurus.service.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Chiusura tecnica motivata di un job push o promemoria.
 *
 * <p>Il motivo è un codice tecnico breve, non testo libero: finisce in
 * {@code skip_reason} e non deve poter veicolare dati personali.
 */
public record NotificationDeliveryCloseRequest(
    @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String reason
) {}
