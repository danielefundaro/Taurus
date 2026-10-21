package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Line;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.RegisterEntry;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturn;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturnRow;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.HolderData;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Signature;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Tenant;
import com.fundaro.zodiac.taurus.service.report.ReportLabels;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.fundaro.zodiac.taurus.service.loansheet.LoanSheetText.*;

/**
 * Builds the data a template receives, as described by {@link LoanSheetVariables}: nested maps, lists, texts and
 * booleans only, so that a template cannot reach anything else. Missing texts are empty strings.
 */
public final class LoanSheetModelFactory {

    static final int MIN_GOODS_ROWS = 14;
    static final int MIN_REGISTER_ROWS = 21;
    static final int MIN_RETURN_ROWS = 14;
    static final String BLANK_DATE = "____ / ____ / ________";

    private LoanSheetModelFactory() {
    }

    public static Map<String, Object> build(LoanSheetContent content) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("associazione", association(content.tenant()));
        model.put("foglio", sheet(content));
        putMember(model, content);
        List<Map<String, Object>> goods = content.lines().stream().map(LoanSheetModelFactory::goodsRow).toList();
        model.put("beni", goods);
        model.put("beniRigheVuote", numberedRows(goods.size(), MIN_GOODS_ROWS));
        model.put("durata", duration(content.lines(), content.durations()));
        List<Map<String, Object>> register = content.register().stream().map(LoanSheetModelFactory::registerRow).toList();
        model.put("registro", register);
        // An act signed electronically prints only its own rows, without the blank rows of the paper form.
        boolean signedAct = content.signature() != null;
        model.put("registroRigheVuote", signedAct ? List.of() : blankRows(register.size(), MIN_REGISTER_ROWS));
        Map<String, Object> finalReturn = finalReturn(content.finalReturn());
        if (signedAct) finalReturn.put("righeVuote", List.of());
        model.put("riconsegna", finalReturn);
        model.put("firma", signature(content.signature()));
        model.put("atto", act(content.signature()));
        return model;
    }

    private static Map<String, Object> signature(Signature signature) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("elettronica", signature != null);
        values.put("dicitura", LoanSheetVariables.SIGNED_NOTE);
        values.put("giorniDisponibilita", signature == null ? "" : Integer.toString(signature.graceDays()));
        return values;
    }

    private static Map<String, Object> act(Signature signature) {
        String act = signature == null ? null : signature.act();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("emissione", LoanSheetActs.ISSUE.equals(act));
        values.put("variazione", LoanSheetActs.VARIATION.equals(act));
        values.put("riconsegna", LoanSheetActs.RETURN.equals(act));
        return values;
    }

    private static Map<String, Object> association(Tenant tenant) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("nome", text(tenant.name()));
        values.put("indirizzo", text(address(tenant)));
        values.put("email", text(tenant.email()));
        values.put("telefono", text(tenant.phone()));
        values.put("pec", text(tenant.pec()));
        values.put("recapiti", text(join(
            ", ",
            tenant.email(),
            blankToNull(tenant.phone()) == null ? null : "tel. " + tenant.phone().trim(),
            blankToNull(tenant.pec()) == null ? null : "PEC " + tenant.pec().trim()
        )));
        values.put("partitaIva", text(tenant.vatNumber()));
        values.put("codiceFiscale", text(tenant.taxCode()));
        values.put("datiFiscali", text(join(
            ", ",
            blankToNull(tenant.vatNumber()) == null ? null : "P. IVA " + tenant.vatNumber().trim(),
            blankToNull(tenant.taxCode()) == null ? null : "C.F. " + tenant.taxCode().trim()
        )));
        values.put("logo", tenant.logo() == null ? "" : tenant.logo());
        return values;
    }

    private static Map<String, Object> sheet(LoanSheetContent content) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("numero", text(content.sheetNumber()));
        values.put("data", text(date(content.issuedOn())));
        values.put("luogo", text(content.place()));
        values.put("luogoEData", text(join(", ", content.place(), date(content.issuedOn()))));
        values.put("delibera", text(content.authorizationReference()));
        values.put("dataDelibera", text(date(content.authorizationDate())));
        values.put("firmatario", text(content.associationSignatoryName()));
        values.put("anteprima", content.preview());
        return values;
    }

    private static void putMember(Map<String, Object> model, LoanSheetContent content) {
        boolean member = content.holderRole() == InventoryLoanHolderRole.MEMBER;
        boolean guardian = content.holderRole() == InventoryLoanHolderRole.GUARDIAN;
        String fullName = text(join(" ", content.memberName(), content.memberLastName()));
        String birthDate = text(date(content.memberBirthDate()));
        String email = text(content.memberEmail());

        Map<String, Object> socio = new LinkedHashMap<>();
        socio.put("nome", text(content.memberName()));
        socio.put("cognome", text(content.memberLastName()));
        socio.put("nomeCognome", fullName);
        socio.put("dataNascita", birthDate);
        socio.put("email", email);
        model.put("socio", socio);

        // The data entered by the holder are printed only in the issue signed electronically.
        HolderData data = content.signature() != null && LoanSheetActs.ISSUE.equals(content.signature().act()) ? content.signature().holder() : null;
        // A parent or guardian signing electronically enters the own name and date of birth with the other data.
        boolean parentData = guardian && data != null && data.holderName() != null;
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("socioMaggiorenne", member);
        holder.put("genitoreTutore", guardian);
        holder.put("nomeCognome", member ? fullName : parentData ? text(data.holderName()) : "");
        holder.put("dataNascita", member ? birthDate : parentData ? text(date(data.holderBirthDate())) : "");
        holder.put("email", member ? email : parentData ? text(data.holderEmail()) : "");
        holder.put("luogoNascita", data == null ? "" : text(data.birthPlace()));
        holder.put("residenza", data == null ? "" : text(data.residence()));
        holder.put("cap", data == null ? "" : text(data.postalCode()));
        holder.put("cittaProvincia", data == null ? "" : text(data.cityProvince()));
        holder.put("codiceFiscale", data == null ? "" : text(data.taxCode()));
        holder.put("documento", data == null ? "" : text(data.document()));
        holder.put("documentoRilasciatoDa", data == null ? "" : text(data.documentIssuedBy()));
        holder.put("documentoRilasciatoIl", data == null ? "" : text(date(data.documentIssuedOn())));
        holder.put("telefono", data == null ? "" : text(data.phone()));
        model.put("affidatario", holder);

        Map<String, Object> minor = new LinkedHashMap<>();
        minor.put("nomeCognome", guardian ? fullName : "");
        minor.put("dataNascita", guardian ? birthDate : "");
        minor.put("luogoNascita", data == null || !guardian ? "" : text(data.minorBirthPlace()));
        model.put("minore", minor);
    }

    private static Map<String, Object> goodsRow(Line line) {
        boolean fixedTerm = line.loanType() == InventoryLoanType.FIXED_TERM;
        String condition = line.conditionStatus() == null ? null : ReportLabels.inventoryCondition(line.conditionStatus());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("numero", Integer.toString(line.lineNumber()));
        row.put("nome", text(line.itemName()));
        row.put("marcaModello", text(line.brandModel()));
        row.put("matricola", text(line.serialNumber()));
        row.put("numeroInventario", text(line.inventoryNumber()));
        row.put("matricolaInventario", text(serialOrInventory(line.serialNumber(), line.inventoryNumber())));
        row.put("accessori", text(line.accessories()));
        row.put("quantita", Integer.toString(line.quantity()));
        row.put("valoreUnitario", text(amount(line.agreedUnitValue())));
        row.put("valoreConcordato", text(agreedValue(line.agreedUnitValue(), line.quantity())));
        row.put("stato", text(condition));
        row.put("note", text(line.conditionNotes()));
        row.put("statoENote", text(join(" – ", condition, line.conditionNotes())));
        row.put("aScadenza", fixedTerm);
        row.put("precario", line.loanType() == InventoryLoanType.PRECARIOUS);
        row.put("scadenza", fixedTerm ? text(date(line.expirationDate())) : "");
        return row;
    }

    private static Map<String, Object> duration(List<Line> lines, Durations durations) {
        Set<LocalDate> expirations = new TreeSet<>();
        lines.stream()
            .filter(line -> line.loanType() == InventoryLoanType.FIXED_TERM && line.expirationDate() != null)
            .forEach(line -> expirations.add(line.expirationDate()));
        boolean different = expirations.size() > 1;
        String single = expirations.size() == 1 ? date(expirations.iterator().next()) : "";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("aScadenza", lines.stream().anyMatch(line -> line.loanType() == InventoryLoanType.FIXED_TERM));
        values.put("precario", lines.stream().anyMatch(line -> line.loanType() == InventoryLoanType.PRECARIOUS));
        values.put("scadenzaStrumenti", expirations.isEmpty()
            ? "del " + BLANK_DATE
            : different ? "indicata per ciascuno strumento nella sezione 3" : "del " + single);
        values.put("scadenzaUnica", single);
        values.put("scadenzeDiverse", different);
        values.put("mesiMinimi", Integer.toString(durations.minMonths()));
        values.put("mesiMassimi", Integer.toString(durations.maxMonths()));
        values.put("mesiRinnovoMassimi", Integer.toString(durations.renewalMaxMonths()));
        values.put("mesiComplessiviMassimi", Integer.toString(durations.totalMaxMonths()));
        return values;
    }

    private static Map<String, Object> registerRow(RegisterEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("data", text(date(entry.date())));
        row.put("bene", text(entry.itemName()));
        row.put("marcaModelloMatricola", text(entry.brandModelSerial()));
        row.put("operazione", text(entry.operation()));
        row.put("scadenza", text(date(entry.expirationDate())));
        row.put("valoreConcordato", text(entry.agreedValue()));
        row.put("delibera", text(entry.authorization()));
        return row;
    }

    private static Map<String, Object> finalReturn(FinalReturn finalReturn) {
        boolean registered = finalReturn != null;
        boolean charges = registered && finalReturn.outcome() == InventoryLoanReturnOutcome.CHARGES;
        List<Map<String, Object>> rows = new ArrayList<>();
        if (registered) {
            for (FinalReturnRow source : finalReturn.rows()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("numero", Integer.toString(rows.size() + 1));
                row.put("bene", text(source.itemName()));
                row.put("matricolaInventario", text(source.serialOrInventory()));
                row.put("restituito", text(source.returned()));
                row.put("stato", text(source.condition()));
                row.put("note", text(source.notes()));
                rows.add(row);
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("registrata", registered);
        values.put("data", registered ? text(date(finalReturn.date())) : "");
        values.put("beni", rows);
        values.put("righeVuote", numberedRows(rows.size(), MIN_RETURN_ROWS));
        values.put("senzaAddebiti", registered && finalReturn.outcome() == InventoryLoanReturnOutcome.COMPLIANT);
        values.put("conAddebiti", charges);
        values.put("importo", charges ? text(amount(finalReturn.chargeAmount())) : "");
        values.put("termine", charges ? text(date(finalReturn.chargeDueDate())) : "");
        values.put("ricevutoDa", registered ? text(finalReturn.receivedByName()) : "");
        return values;
    }

    static String address(Tenant tenant) {
        String locality = join(" ", tenant.postalCode(), tenant.city());
        if (blankToNull(tenant.province()) != null) locality = join(" ", locality, "(" + tenant.province().trim() + ")");
        return join(", ", tenant.address(), locality);
    }

    private static List<Map<String, Object>> numberedRows(int filled, int minimum) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int number = filled + 1; number <= minimum; number++) rows.add(Map.of("numero", Integer.toString(number)));
        return rows;
    }

    private static List<Map<String, Object>> blankRows(int filled, int minimum) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = filled; index < minimum; index++) rows.add(Map.of());
        return rows;
    }

    private static String text(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "" : printable(normalized);
    }
}
