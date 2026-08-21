package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchServiceImplTest {

    @Test
    void shouldSerializeJavaTimeValuesWithTheApplicationObjectMapper() throws Exception {
        ObjectMapper applicationObjectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ZonedDateTime editDate = ZonedDateTime.of(2026, 8, 21, 17, 10, 12, 0, ZoneOffset.UTC);

        String json = OpenSearchServiceImpl
            .createJsonpMapper(applicationObjectMapper)
            .objectMapper()
            .writeValueAsString(Map.of("editDate", editDate));

        assertThat(json).contains("2026-08-21T17:10:12Z");
    }
}
