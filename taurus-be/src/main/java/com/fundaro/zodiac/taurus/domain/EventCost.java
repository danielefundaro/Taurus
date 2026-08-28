package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "calendar_event_cost")
public class EventCost implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Transient
    private Long order;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrder() { return order; }
    public void setOrder(Long order) { this.order = order; }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventCost that)) return false;
        return Objects.equals(description, that.description) && Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, amount);
    }

    @Override
    public String toString() {
        return "EventCost{" +
            "description='" + description +
            "', amount=" + amount +
            "}";
    }
}
