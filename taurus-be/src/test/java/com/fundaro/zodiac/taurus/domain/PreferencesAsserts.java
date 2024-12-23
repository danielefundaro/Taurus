package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.AssertUtils.zonedDataTimeSameInstant;
import static org.assertj.core.api.Assertions.assertThat;

public class PreferencesAsserts {

    /**
     * Asserts that the entity has all properties (fields/relationships) set.
     *
     * @param expected the expected entity
     * @param actual the actual entity
     */
    public static void assertPreferencesAllPropertiesEquals(Preferences expected, Preferences actual) {
        assertPreferencesAutoGeneratedPropertiesEquals(expected, actual);
        assertPreferencesAllUpdatablePropertiesEquals(expected, actual);
    }

    /**
     * Asserts that the entity has all updatable properties (fields/relationships) set.
     *
     * @param expected the expected entity
     * @param actual the actual entity
     */
    public static void assertPreferencesAllUpdatablePropertiesEquals(Preferences expected, Preferences actual) {
        assertPreferencesUpdatableFieldsEquals(expected, actual);
        assertPreferencesUpdatableRelationshipsEquals(expected, actual);
    }

    /**
     * Asserts that the entity has all the auto generated properties (fields/relationships) set.
     *
     * @param expected the expected entity
     * @param actual the actual entity
     */
    public static void assertPreferencesAutoGeneratedPropertiesEquals(Preferences expected, Preferences actual) {
        assertThat(expected)
            .as("Verify Preferences auto generated properties")
            .satisfies(e -> assertThat(e.getId()).as("check id").isEqualTo(actual.getId()));
    }

    /**
     * Asserts that the entity has all the updatable fields set.
     *
     * @param expected the expected entity
     * @param actual the actual entity
     */
    public static void assertPreferencesUpdatableFieldsEquals(Preferences expected, Preferences actual) {
        assertThat(expected)
            .as("Verify Preferences relevant properties")
            .satisfies(e -> assertThat(e.getDeleted()).as("check deleted").isEqualTo(actual.getDeleted()))
            .satisfies(e -> assertThat(e.getInsertBy()).as("check insertBy").isEqualTo(actual.getInsertBy()))
            .satisfies(e ->
                assertThat(e.getInsertDate())
                    .as("check insertDate")
                    .usingComparator(zonedDataTimeSameInstant)
                    .isEqualTo(actual.getInsertDate())
            )
            .satisfies(e -> assertThat(e.getEditBy()).as("check editBy").isEqualTo(actual.getEditBy()))
            .satisfies(e ->
                assertThat(e.getEditDate()).as("check editDate").usingComparator(zonedDataTimeSameInstant).isEqualTo(actual.getEditDate())
            )
            .satisfies(e -> assertThat(e.getUserId()).as("check userId").isEqualTo(actual.getUserId()))
            .satisfies(e -> assertThat(e.getKey()).as("check key").isEqualTo(actual.getKey()))
            .satisfies(e -> assertThat(e.getValue()).as("check value").isEqualTo(actual.getValue()));
    }

    /**
     * Asserts that the entity has all the updatable relationships set.
     *
     * @param expected the expected entity
     * @param actual the actual entity
     */
    public static void assertPreferencesUpdatableRelationshipsEquals(Preferences expected, Preferences actual) {
        // empty method
    }
}
