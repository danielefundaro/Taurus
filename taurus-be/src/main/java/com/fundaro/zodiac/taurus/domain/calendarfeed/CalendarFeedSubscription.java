package com.fundaro.zodiac.taurus.domain.calendarfeed;

import com.fundaro.zodiac.taurus.domain.Users;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_feed_subscription")
public class CalendarFeedSubscription {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "feed_type", nullable = false, length = 16) private CalendarFeedType feedType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_user_id") private Users owner;
    @Enumerated(EnumType.STRING) @Column(name = "visibility_scope", nullable = false, length = 16) private CalendarFeedScope visibilityScope;
    @Enumerated(EnumType.STRING) @Column(name = "detail_level", nullable = false, length = 16) private CalendarFeedDetailLevel detailLevel;
    @Column(name = "past_days", nullable = false) private int pastDays;
    @Column(name = "future_months", nullable = false) private int futureMonths;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CalendarFeedStatus status;
    @Column(name = "token_version", nullable = false) private int tokenVersion;
    @Column(name = "token_fingerprint", nullable = false, length = 12) private String tokenFingerprint;
    @Column(name = "last_accessed_at") private Instant lastAccessedAt;
    @Column(nullable = false) private boolean deleted;
    @Column(name = "insert_date", nullable = false, updatable = false) private Instant insertDate;
    @Column(name = "insert_by", nullable = false, updatable = false) private String insertBy;
    @Column(name = "edit_date", nullable = false) private Instant editDate;
    @Column(name = "edit_by", nullable = false) private String editBy;
    @Version @Column(name = "entity_version", nullable = false) private long entityVersion;

    public UUID getId() { return id; } public void setId(UUID v) { id = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public CalendarFeedType getFeedType() { return feedType; } public void setFeedType(CalendarFeedType v) { feedType = v; }
    public Users getOwner() { return owner; } public void setOwner(Users v) { owner = v; }
    public CalendarFeedScope getVisibilityScope() { return visibilityScope; } public void setVisibilityScope(CalendarFeedScope v) { visibilityScope = v; }
    public CalendarFeedDetailLevel getDetailLevel() { return detailLevel; } public void setDetailLevel(CalendarFeedDetailLevel v) { detailLevel = v; }
    public int getPastDays() { return pastDays; } public void setPastDays(int v) { pastDays = v; }
    public int getFutureMonths() { return futureMonths; } public void setFutureMonths(int v) { futureMonths = v; }
    public CalendarFeedStatus getStatus() { return status; } public void setStatus(CalendarFeedStatus v) { status = v; }
    public int getTokenVersion() { return tokenVersion; } public void setTokenVersion(int v) { tokenVersion = v; }
    public String getTokenFingerprint() { return tokenFingerprint; } public void setTokenFingerprint(String v) { tokenFingerprint = v; }
    public Instant getLastAccessedAt() { return lastAccessedAt; } public void setLastAccessedAt(Instant v) { lastAccessedAt = v; }
    public boolean isDeleted() { return deleted; } public void setDeleted(boolean v) { deleted = v; }
    public Instant getInsertDate() { return insertDate; } public void setInsertDate(Instant v) { insertDate = v; }
    public String getInsertBy() { return insertBy; } public void setInsertBy(String v) { insertBy = v; }
    public Instant getEditDate() { return editDate; } public void setEditDate(Instant v) { editDate = v; }
    public String getEditBy() { return editBy; } public void setEditBy(String v) { editBy = v; }
    public long getEntityVersion() { return entityVersion; }
}
