package com.fundaro.zodiac.taurus.service.eventpreparation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventMaterial;
import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventProgramEntry;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The fingerprints of what a confirmation attests: SHA-256 of a canonical JSON — keys in alphabetical order, amounts
 * as plain decimal strings, instants in ISO-8601 — that names its schema. A new schema makes every older confirmation
 * obsolete, so a change of the attested data never passes as the old one.
 */
final class PreparationHashes {
    static final int SCHEMA = 2;
    /** The event budget has no currency of its own: it is the one the finance module books, EUR in this version. */
    static final String BUDGET_CURRENCY = "EUR";
    private static final ObjectMapper JSON = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private PreparationHashes() {
    }

    static String budget(CalendarEvents event) {
        List<Object> costs = event.getCosts().stream().map(cost -> (Object) object("amount", amount(cost.getAmount()), "description", cost.getDescription())).toList();
        return hash(object("area", "budget", "costs", costs, "currency", BUDGET_CURRENCY, "fee", amount(event.getFee()), "schema", SCHEMA));
    }

    static String presence(List<PreparationSnapshot.Presence> presences) {
        List<Object> rows = presences.stream().map(row -> (Object) object("arrival", instant(row.arrivalTime()), "note", row.note(), "user", row.userId())).toList();
        return hash(object("area", "presence", "rows", rows, "schema", SCHEMA));
    }

    static String material(CalendarEventMaterial material) {
        InventoryAssignment assignment = material.getAssignment();
        return hash(object(
            "area", "material",
            "assignment", assignment == null ? null : assignment.getId(),
            "assignmentStatus", assignment == null ? null : assignment.getStatus().name(),
            "condition", material.getItem().getConditionStatus().name(),
            "item", material.getItem().getId(),
            "outstanding", assignment == null ? null : assignment.getOutstandingQuantity(),
            "required", material.getRequiredQuantity(),
            "schema", SCHEMA
        ));
    }

    /** The budget and the movements of the event: the confirmation is only accepted without movements. */
    static String noMovements(CalendarEvents event, List<FinancialMovement> movements) {
        List<Object> rows = movements.stream().map(movement -> (Object) object(
            "amount", amount(movement.getAmount()),
            "bookingDate", String.valueOf(movement.getBookingDate()),
            "direction", movement.getDirection().name(),
            "id", movement.getId()
        )).toList();
        return hash(object("area", "noMovements", "budget", budget(event), "movements", rows, "schema", SCHEMA));
    }

    /** The revision of a saved program, for the key of its notification. */
    static String program(List<CalendarEventProgramEntry> entries) {
        List<Object> rows = entries.stream().map(entry -> (Object) object(
            "duration", entry.getPlannedDurationSeconds(),
            "encore", entry.isEncore(),
            "notes", entry.getNotes(),
            "order", entry.getDisplayOrder(),
            "track", entry.getTrack().getId()
        )).toList();
        return hash(object("area", "program", "rows", rows, "schema", SCHEMA));
    }

    /** The revision of saved materials, for the keys of their notifications. */
    static String materials(List<CalendarEventMaterial> materials) {
        List<Object> rows = materials.stream().map(material -> (Object) object(
            "assignment", material.getAssignment() == null ? null : material.getAssignment().getId(),
            "item", material.getItem().getId(),
            "notes", material.getNotes(),
            "required", material.getRequiredQuantity()
        )).toList();
        return hash(object("area", "materials", "rows", rows, "schema", SCHEMA));
    }

    /** The first twelve hexadecimal digits of the SHA-256 of a value, for the logs. */
    static String shortHash(String value) {
        return HexFormat.of().formatHex(sha256(String.valueOf(value).getBytes(StandardCharsets.UTF_8))).substring(0, 12);
    }

    private static Map<String, Object> object(Object... keysAndValues) {
        Map<String, Object> result = new TreeMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) result.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return result;
    }

    private static String amount(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String instant(Date value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(sha256(JSON.writeValueAsBytes(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write the canonical JSON of a confirmation", exception);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
