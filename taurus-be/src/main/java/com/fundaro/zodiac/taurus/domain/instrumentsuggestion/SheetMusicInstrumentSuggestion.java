package com.fundaro.zodiac.taurus.domain.instrumentsuggestion;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * An instrument name read on a part that matched no tenant instrument, or more than one. The part stays without that
 * instrument until someone creates or associates it, or ignores the name.
 */
@Entity
@Table(name = "sheet_music_instrument_suggestion")
public class SheetMusicInstrumentSuggestion extends TenantAuditedEntity {

    @Column(name = "sheet_music_id", nullable = false, updatable = false)
    private Long sheetMusicId;

    /** The line read on the part. */
    @Column(name = "read_text", nullable = false, updatable = false)
    private String readText;

    @Column(name = "proposed_name", nullable = false, updatable = false)
    private String proposedName;

    /** The proposed name in the form of the uniqueness check of the instruments: groups the parts of the same name. */
    @Column(name = "normalized_name", nullable = false, updatable = false)
    private String normalizedName;

    @Column(name = "catalog_keyword", updatable = false)
    private String catalogKeyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private InstrumentSuggestionSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InstrumentSuggestionStatus status = InstrumentSuggestionStatus.OPEN;

    /** Instrument created or associated, set with {@link InstrumentSuggestionStatus#ACCEPTED}. */
    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    public void accept(Long instrumentId, String actor) {
        resolve(InstrumentSuggestionStatus.ACCEPTED, actor);
        this.instrumentId = instrumentId;
    }

    public void ignore(String actor) {
        resolve(InstrumentSuggestionStatus.IGNORED, actor);
    }

    private void resolve(InstrumentSuggestionStatus status, String actor) {
        this.status = status;
        this.resolvedBy = actor;
        this.resolvedAt = ZonedDateTime.now();
        touchAudit(actor);
    }

    public boolean isOpen() {
        return status == InstrumentSuggestionStatus.OPEN;
    }

    public Long getSheetMusicId() {
        return sheetMusicId;
    }

    public void setSheetMusicId(Long sheetMusicId) {
        this.sheetMusicId = sheetMusicId;
    }

    public String getReadText() {
        return readText;
    }

    public void setReadText(String readText) {
        this.readText = readText;
    }

    public String getProposedName() {
        return proposedName;
    }

    public void setProposedName(String proposedName) {
        this.proposedName = proposedName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getCatalogKeyword() {
        return catalogKeyword;
    }

    public void setCatalogKeyword(String catalogKeyword) {
        this.catalogKeyword = catalogKeyword;
    }

    public InstrumentSuggestionSource getSource() {
        return source;
    }

    public void setSource(InstrumentSuggestionSource source) {
        this.source = source;
    }

    public InstrumentSuggestionStatus getStatus() {
        return status;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public ZonedDateTime getResolvedAt() {
        return resolvedAt;
    }
}
