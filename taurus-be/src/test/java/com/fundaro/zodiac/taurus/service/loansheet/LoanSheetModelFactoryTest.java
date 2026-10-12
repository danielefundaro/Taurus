package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Line;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Variable;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.VariableType;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturn;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturnRow;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Tenant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class LoanSheetModelFactoryTest {

    private static final Tenant TENANT = new Tenant("Banda APS", "Via Roma 1", "00100", "Roma", "RM", "info@banda.it", null, null, null, "91234567890", null);
    private static final Durations DURATIONS = new Durations(12, 24, 24, 36);

    @Test
    void producesExactlyTheVariablesOfTheCatalog() {
        Map<String, Object> model = LoanSheetModelFactory.build(content(
            InventoryLoanHolderRole.MEMBER,
            List.of(line(1, InventoryLoanType.FIXED_TERM, LocalDate.of(2027, 9, 30))),
            finalReturn(InventoryLoanReturnOutcome.COMPLIANT)
        ));

        Set<String> expected = new TreeSet<>();
        Set<String> actual = new TreeSet<>();
        LoanSheetVariables.GROUPS.forEach(group -> group.variables().forEach(variable -> {
            expected.add(variable.name());
            if (variable.type() == VariableType.LIST) variable.fields().forEach(field -> expected.add(variable.name() + "[]." + field.name()));
        }));
        collect("", model, actual);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void prefillsTheMemberAsHolderWhenAdult() {
        Map<String, Object> model = LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(), null));

        assertThat(map(model, "affidatario"))
            .containsEntry("socioMaggiorenne", true)
            .containsEntry("genitoreTutore", false)
            .containsEntry("nomeCognome", "Mario Rossi")
            .containsEntry("dataNascita", "01/05/2000")
            .containsEntry("email", "mario@example.org");
        assertThat(map(model, "minore")).containsEntry("nomeCognome", "").containsEntry("dataNascita", "");
    }

    @Test
    void leavesTheHolderBlankAndPrefillsTheMinorWhenAGuardianSigns() {
        Map<String, Object> model = LoanSheetModelFactory.build(content(InventoryLoanHolderRole.GUARDIAN, List.of(), null));

        assertThat(map(model, "affidatario")).containsEntry("genitoreTutore", true).containsEntry("nomeCognome", "").containsEntry("email", "");
        assertThat(map(model, "minore")).containsEntry("nomeCognome", "Mario Rossi").containsEntry("dataNascita", "01/05/2000");
        assertThat(map(model, "socio")).containsEntry("nomeCognome", "Mario Rossi");
    }

    @Test
    void describesTheExpirationOfTheInstruments() {
        Map<String, Object> none = map(LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(line(1, InventoryLoanType.PRECARIOUS, null)), null)), "durata");
        assertThat(none)
            .containsEntry("scadenzaStrumenti", "del ____ / ____ / ________")
            .containsEntry("scadenzaUnica", "")
            .containsEntry("aScadenza", false)
            .containsEntry("precario", true);

        Map<String, Object> single = map(LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(
            line(1, InventoryLoanType.FIXED_TERM, LocalDate.of(2027, 9, 30)),
            line(2, InventoryLoanType.FIXED_TERM, LocalDate.of(2027, 9, 30))
        ), null)), "durata");
        assertThat(single).containsEntry("scadenzaStrumenti", "del 30/09/2027").containsEntry("scadenzaUnica", "30/09/2027").containsEntry("scadenzeDiverse", false);

        Map<String, Object> different = map(LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(
            line(1, InventoryLoanType.FIXED_TERM, LocalDate.of(2027, 9, 30)),
            line(2, InventoryLoanType.FIXED_TERM, LocalDate.of(2028, 1, 15))
        ), null)), "durata");
        assertThat(different)
            .containsEntry("scadenzaStrumenti", "indicata per ciascuno strumento nella sezione 3")
            .containsEntry("scadenzaUnica", "")
            .containsEntry("scadenzeDiverse", true)
            .containsEntry("mesiMinimi", "12")
            .containsEntry("mesiComplessiviMassimi", "36");
    }

    @Test
    void fillsTheTablesUpToTheRowsOfTheModel() {
        Map<String, Object> model = LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(
            line(1, InventoryLoanType.FIXED_TERM, LocalDate.of(2027, 9, 30)),
            line(2, InventoryLoanType.PRECARIOUS, null)
        ), null));

        List<?> goods = (List<?>) model.get("beni");
        assertThat(goods).hasSize(2);
        assertThat(row(goods.get(0)))
            .containsEntry("matricolaInventario", "SN-1 / INV-1")
            .containsEntry("valoreConcordato", "150,00 (75,00 cad.)")
            .containsEntry("statoENote", "Buono – Segni d’uso")
            .containsEntry("scadenza", "30/09/2027")
            .containsEntry("aScadenza", true);
        assertThat(row(goods.get(1))).containsEntry("scadenza", "").containsEntry("precario", true);
        List<?> emptyGoods = (List<?>) model.get("beniRigheVuote");
        assertThat(emptyGoods).hasSize(12);
        assertThat(row(emptyGoods.get(0))).containsEntry("numero", "3");
        assertThat((List<?>) model.get("registroRigheVuote")).hasSize(21);
        assertThat((List<?>) map(model, "riconsegna").get("righeVuote")).hasSize(14);
    }

    @Test
    void fillsTheReturnReportOnlyForAClosedSheet() {
        Map<String, Object> open = map(LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(), null)), "riconsegna");
        assertThat(open).containsEntry("registrata", false).containsEntry("data", "").containsEntry("conAddebiti", false).containsEntry("ricevutoDa", "");

        Map<String, Object> closed = map(LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(), finalReturn(InventoryLoanReturnOutcome.CHARGES))), "riconsegna");
        assertThat(closed)
            .containsEntry("registrata", true)
            .containsEntry("data", "10/09/2027")
            .containsEntry("conAddebiti", true)
            .containsEntry("senzaAddebiti", false)
            .containsEntry("importo", "1.250,00")
            .containsEntry("termine", "10/10/2027")
            .containsEntry("ricevutoDa", "Anna Verdi");
        assertThat((List<?>) closed.get("beni")).hasSize(1);
        assertThat((List<?>) closed.get("righeVuote")).hasSize(13);
    }

    @Test
    void printsContactsAndTaxDataWithoutTheMissingParts() {
        Map<String, Object> association = map(LoanSheetModelFactory.build(content(InventoryLoanHolderRole.MEMBER, List.of(), null)), "associazione");

        assertThat(association)
            .containsEntry("indirizzo", "Via Roma 1, 00100 Roma (RM)")
            .containsEntry("recapiti", "info@banda.it")
            .containsEntry("datiFiscali", "C.F. 91234567890")
            .containsEntry("partitaIva", "")
            .containsEntry("logo", "");
    }

    @SuppressWarnings("unchecked")
    private static void collect(String prefix, Map<String, Object> values, Set<String> names) {
        values.forEach((key, value) -> {
            String name = prefix + key;
            if (value instanceof Map<?, ?> nested) {
                collect(name + ".", (Map<String, Object>) nested, names);
            } else if (value instanceof List<?> list) {
                names.add(name);
                if (list.isEmpty()) {
                    Variable variable = LoanSheetVariables.find(name).orElseThrow();
                    variable.fields().forEach(field -> names.add(name + "[]." + field.name()));
                }
                for (Object element : list) ((Map<String, Object>) element).keySet().forEach(field -> names.add(name + "[]." + field));
            } else {
                names.add(name);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> row(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> model, String key) {
        return (Map<String, Object>) model.get(key);
    }

    private static Line line(int number, InventoryLoanType loanType, LocalDate expiration) {
        return new Line(number, (long) number, "Bene " + number, "Marca", "SN-" + number, "INV-" + number, "Custodia", 2, new BigDecimal("75"),
            InventoryCondition.GOOD, "Segni d’uso", loanType, expiration);
    }

    private static FinalReturn finalReturn(InventoryLoanReturnOutcome outcome) {
        boolean charges = outcome == InventoryLoanReturnOutcome.CHARGES;
        return new FinalReturn(
            LocalDate.of(2027, 9, 10),
            List.of(new FinalReturnRow("Clarinetto", "SN-1 / INV-1", "Sì", "Buono", null)),
            outcome,
            charges ? new BigDecimal("1250") : null,
            charges ? LocalDate.of(2027, 10, 10) : null,
            "Anna Verdi"
        );
    }

    private static LoanSheetContent content(InventoryLoanHolderRole role, List<Line> lines, FinalReturn finalReturn) {
        return new LoanSheetContent(
            false,
            TENANT,
            "2026/001",
            DURATIONS,
            role,
            "Mario",
            "Rossi",
            LocalDate.of(2000, 5, 1),
            "mario@example.org",
            lines,
            LocalDate.of(2026, 9, 28),
            "Roma",
            "7",
            LocalDate.of(2026, 9, 25),
            "Anna Verdi",
            List.of(),
            finalReturn
        );
    }
}
