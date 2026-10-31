package com.fundaro.zodiac.taurus.web.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qualifica l'identificativo di correlazione delle richieste: quello che finisce nei log e nella
 * risposta, e che non deve restare addosso al thread dopo la richiesta.
 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void keepsTheIdentifierOfTheProxyAndEchoesItBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "edge-7f3a9c21");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seenDuringTheRequest = runThrough(request, response);

        assertThat(seenDuringTheRequest).isEqualTo("edge-7f3a9c21");
        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("edge-7f3a9c21");
    }

    @Test
    void generatesAnIdentifierWhenNobodyProvidesOne() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seenDuringTheRequest = runThrough(new MockHttpServletRequest(), response);

        assertThat(UUID.fromString(seenDuringTheRequest)).hasToString(seenDuringTheRequest);
        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo(seenDuringTheRequest);
    }

    // Il valore arriva dal chiamante e finisce nei log e in un'intestazione: un contenuto con
    // ritorni a capo, spazi o segni di marcatura va sostituito, non ripetuto.
    @ParameterizedTest
    // L'ultimo valore ha 65 caratteri, uno oltre il limite accettato.
    @ValueSource(
        strings = { "riga\nspezzata", "con spazi", "<script>", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
    )
    void replacesAnIdentifierThatDoesNotLookLikeOne(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seenDuringTheRequest = runThrough(request, response);

        assertThat(seenDuringTheRequest).isNotEqualTo(incoming);
        assertThat(UUID.fromString(seenDuringTheRequest)).hasToString(seenDuringTheRequest);
    }

    @Test
    void leavesNothingOnTheThreadAfterTheRequest() throws Exception {
        runThrough(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_KEY)).isNull();
    }

    @Test
    void leavesNothingOnTheThreadWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() ->
            filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("richiesta fallita");
            })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_KEY)).isNull();
    }

    /** Esegue il filtro e restituisce l'identificativo visto dal resto della catena. */
    private String runThrough(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        String[] seen = new String[1];
        filter.doFilter(request, response, (req, res) -> seen[0] = MDC.get(RequestCorrelationFilter.REQUEST_ID_KEY));
        return seen[0];
    }
}
