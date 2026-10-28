package com.fundaro.zodiac.taurus.service.eventpreparation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventMaterial;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

class PreparationHashesTest {

    @Test
    void budgetHashIsTheCanonicalJsonWithCurrencyAndSchema() throws Exception {
        CalendarEvents event = event("100.50", cost("Pullman", "20"));
        String json = "{\"area\":\"budget\",\"costs\":[{\"amount\":\"20\",\"description\":\"Pullman\"}],\"currency\":\"EUR\",\"fee\":\"100.5\",\"schema\":2}";

        assertThat(PreparationHashes.budget(event)).isEqualTo(sha256(json));
    }

    @Test
    void equalAmountsWithDifferentScalesGiveTheSameHash() {
        assertThat(PreparationHashes.budget(event("10.0", cost("Sala", "5.00")))).isEqualTo(PreparationHashes.budget(event("10", cost("Sala", "5"))));
        assertThat(PreparationHashes.budget(event(null))).isEqualTo(PreparationHashes.budget(event("0.00")));
    }

    @Test
    void everyAttestedValueChangesTheBudgetHash() {
        String base = PreparationHashes.budget(event("10", cost("Sala", "5")));

        assertThat(PreparationHashes.budget(event("11", cost("Sala", "5")))).isNotEqualTo(base);
        assertThat(PreparationHashes.budget(event("10", cost("Sala", "6")))).isNotEqualTo(base);
        assertThat(PreparationHashes.budget(event("10", cost("Pullman", "5")))).isNotEqualTo(base);
        assertThat(PreparationHashes.budget(event("10", cost("Sala", "5"), cost("Pullman", "0")))).isNotEqualTo(base);
    }

    @Test
    void legacyConfirmationsNoLongerMatch() throws Exception {
        CalendarEvents event = event("10");
        // The schema 1 fingerprint: the values as Arrays.deepToString, without keys, currency or schema.
        String legacy = sha256(Arrays.deepToString(new Object[]{new BigDecimal("10")}));

        assertThat(PreparationHashes.budget(event)).isNotEqualTo(legacy);
    }

    @Test
    void materialHashFollowsAssignmentQuantityAndCondition() {
        InventoryItem item = new InventoryItem();
        item.setId(5L);
        item.setConditionStatus(InventoryCondition.GOOD);
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(9L);
        assignment.setItem(item);
        assignment.setAssignedQuantity(2);
        assignment.setStatus(InventoryAssignmentStatus.ACTIVE);
        CalendarEventMaterial material = new CalendarEventMaterial();
        material.setItem(item);
        material.setAssignment(assignment);
        material.setRequiredQuantity(1);
        String base = PreparationHashes.material(material);

        assignment.setReturnedQuantity(1);
        String returned = PreparationHashes.material(material);
        item.setConditionStatus(InventoryCondition.TO_REPAIR);
        String broken = PreparationHashes.material(material);
        material.setAssignment(null);
        String unassigned = PreparationHashes.material(material);

        assertThat(List.of(base, returned, broken, unassigned)).doesNotHaveDuplicates();
    }

    @Test
    void presenceHashFollowsTheRegister() {
        Date arrival = new Date(1_800_000_000_000L);
        String base = PreparationHashes.presence(List.of(new PreparationSnapshot.Presence(1L, arrival, null)));

        assertThat(PreparationHashes.presence(List.of(new PreparationSnapshot.Presence(1L, arrival, null)))).isEqualTo(base);
        assertThat(PreparationHashes.presence(List.of(new PreparationSnapshot.Presence(1L, arrival, "In ritardo")))).isNotEqualTo(base);
        assertThat(PreparationHashes.presence(List.of())).isNotEqualTo(base);
    }

    @Test
    void noMovementsHashIncludesTheBudget() {
        assertThat(PreparationHashes.noMovements(event("10"), List.of())).isNotEqualTo(PreparationHashes.noMovements(event("20"), List.of()));
    }

    @Test
    void shortHashIsTwelveHexadecimalDigitsAndHidesTheValue() {
        String value = PreparationHashes.shortHash("admin-keycloak-id");

        assertThat(value).hasSize(12).matches("[0-9a-f]{12}").doesNotContain("admin");
        assertThat(PreparationHashes.shortHash(null)).hasSize(12);
    }

    private static CalendarEvents event(String fee, EventCost... costs) {
        CalendarEvents event = new CalendarEvents();
        event.setFee(fee == null ? null : new BigDecimal(fee));
        event.setCosts(new ArrayList<>(List.of(costs)));
        return event;
    }

    private static EventCost cost(String description, String amount) {
        EventCost cost = new EventCost();
        cost.setDescription(description);
        cost.setAmount(new BigDecimal(amount));
        return cost;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
