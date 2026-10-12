package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Default template of the loan sheet and initial duration rules of the tenants, shipped as backend resources.
 */
@Component
public class LoanTemplateDefaults {

    static final String TEMPLATE = "templates/inventory/loan-sheet.html";
    static final String PROPERTIES = "templates/inventory/loan-sheet.properties";
    public static final String FILE_NAME = "foglio-affidamento-modello-predefinito.html";

    private final String content;
    private final String contentHash;
    private final String label;
    private final Durations durations;

    public LoanTemplateDefaults() {
        Properties properties = new Properties();
        try (InputStream input = new ClassPathResource(PROPERTIES).getInputStream()) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load the default loan sheet properties", exception);
        }
        try (InputStream input = new ClassPathResource(TEMPLATE).getInputStream()) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load the default loan sheet template", exception);
        }
        contentHash = LoanSheetSupport.sha256(content);
        label = properties.getProperty("label");
        durations = new Durations(
            Integer.parseInt(properties.getProperty("min-months")),
            Integer.parseInt(properties.getProperty("max-months")),
            Integer.parseInt(properties.getProperty("renewal-max-months")),
            Integer.parseInt(properties.getProperty("total-max-months"))
        );
    }

    public String content() {
        return content;
    }

    public String contentHash() {
        return contentHash;
    }

    public String label() {
        return label;
    }

    public Durations durations() {
        return durations;
    }
}
