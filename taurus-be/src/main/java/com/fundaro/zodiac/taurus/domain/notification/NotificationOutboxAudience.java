package com.fundaro.zodiac.taurus.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_outbox_audience")
public class NotificationOutboxAudience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_event_id", nullable = false)
    private NotificationOutbox event;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 32)
    private NotificationAudienceType type;

    @Column(name = "audience_value", nullable = false, length = 255)
    private String value;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public NotificationOutbox getEvent() { return event; }
    public void setEvent(NotificationOutbox event) { this.event = event; }
    public NotificationAudienceType getType() { return type; }
    public void setType(NotificationAudienceType type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
