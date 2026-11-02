package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Instruments;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InstrumentsRepository extends CatalogRepository<Instruments> {

    /**
     * Serializes the creation and renaming of the instruments of the current tenant schema, so that the check on the
     * name cannot be passed by two requests at the same time.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(current_schema() || ':instrument-name', 0))", nativeQuery = true)
    Integer lockTenantNames();

    /** Id and name of every instrument still in use, for the check on the name. */
    @Query("select instrument.id, instrument.name from Instruments instrument where instrument.deleted = false")
    List<Object[]> findActiveNames();
}
