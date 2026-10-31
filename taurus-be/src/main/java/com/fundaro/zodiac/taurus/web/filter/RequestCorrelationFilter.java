package com.fundaro.zodiac.taurus.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Dà a ogni richiesta un identificativo e lo mette nel contesto di log.
 *
 * <p>Con i log in JSON ({@code jhipster.logging.use-json-format}) il campo {@code requestId} compare
 * in ogni riga prodotta durante la richiesta: è quello che permette di ricostruire un guasto
 * raccogliendo le righe di repliche e thread diversi, e di collegare la segnalazione di un utente —
 * che legge l'identificativo nell'intestazione {@code X-Request-Id} della risposta — alle righe che
 * lo riguardano.
 *
 * <p>Il valore arriva dal reverse proxy quando c'è, così la stessa richiesta ha lo stesso
 * identificativo nei log del proxy e in questi. Un valore che non rispetta il formato atteso viene
 * sostituito invece di essere accettato: finirebbe nei log e nelle intestazioni della risposta, dove
 * un contenuto arbitrario del chiamante non deve arrivare.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String REQUEST_ID_KEY = "requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Il thread torna al pool: senza questa rimozione l'identificativo resterebbe addosso
            // alla richiesta successiva che lo riusa.
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    private static String requestId(String incoming) {
        return incoming != null && ACCEPTED.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }
}
