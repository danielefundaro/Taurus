package com.fundaro.zodiac.taurus.repository.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * Lettura unificata delle tre origini tecniche della console amministrativa:
 * fan-out in-app ({@code notification_outbox}), consegne push
 * ({@code notification_push_delivery}) e promemoria evento ({@code push_reminders}).
 *
 * <p>La query gira nello schema del tenant corrente perché il connection provider
 * multi-tenant imposta lo schema sulla connessione JDBC; i nomi tabella restano
 * quindi volutamente non qualificati.
 */
@Repository
public class NotificationDeliveryAdminQueryRepository {

    /** Campi ordinabili esposti dall'API mappati sulla colonna della union. */
    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
        "occurredAt", "occurred_at",
        "editDate", "edit_date",
        "attempts", "attempts",
        "status", "status",
        "id", "id"
    );

    private static final String UNION = """
        SELECT 'OUTBOX' AS origin, o.id AS id, o.source AS source, o.operation AS operation,
               'IN_APP_FANOUT' AS delivery_type, o.status AS status, o.occurred_at AS occurred_at,
               o.attempts AS attempts, o.edit_date AS edit_date, o.next_attempt_at AS next_attempt_at,
               o.last_error AS last_error, NULL AS skip_reason, o.event_key AS event_key
          FROM notification_outbox o
         WHERE o.deleted = FALSE
        UNION ALL
        SELECT 'PUSH', p.id, p.source, NULL,
               p.delivery_type, p.status, p.scheduled_at,
               p.attempts, p.edit_date, p.next_attempt_at,
               p.last_error, p.skip_reason, p.source_event_key
          FROM notification_push_delivery p
         WHERE p.deleted = FALSE
        UNION ALL
        SELECT 'REMINDER', r.id, 'CALENDAR', NULL,
               'EVENT_REMINDER', r.status, r.send_at,
               r.attempts, r.edit_date, r.next_attempt_at,
               r.last_error, r.skip_reason, 'reminder:' || r.event_id || ':' || r.id
          FROM push_reminders r
         WHERE r.deleted = FALSE
        """;

    private final EntityManager entityManager;

    public NotificationDeliveryAdminQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static Set<String> sortableFields() {
        return SORTABLE_COLUMNS.keySet();
    }

    public long count(NotificationDeliveryFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM (").append(UNION).append(") AS delivery WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filter);
        Query query = entityManager.createNativeQuery(sql.toString());
        bind(query, parameters);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Totale e istante più vecchio delle righe in un dato stato, aggregati sulle
     * tre origini: è il numero che la dashboard operativa mostra come unico dato.
     */
    public NotificationDeliverySummary summarize(NotificationStatus status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*), MIN(occurred_at) FROM (").append(UNION).append(") AS delivery WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, new NotificationDeliveryFilter(status, null, null, null, null, null));
        Query query = entityManager.createNativeQuery(sql.toString());
        bind(query, parameters);
        Object[] row = (Object[]) query.getSingleResult();
        return new NotificationDeliverySummary(((Number) row[0]).longValue(), toZoned(row[1]));
    }

    public List<NotificationDeliveryRow> find(NotificationDeliveryFilter filter, int page, int size, String sortField, boolean descending) {
        String column = SORTABLE_COLUMNS.get(sortField);
        if (column == null) throw new IllegalArgumentException("Unsupported sort field: " + sortField);
        StringBuilder sql = new StringBuilder("SELECT * FROM (").append(UNION).append(") AS delivery WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, filter);
        // La direzione e la colonna provengono dalla whitelist, mai dall'input grezzo.
        sql.append(" ORDER BY ").append(column).append(descending ? " DESC" : " ASC").append(", origin ASC, id ASC");
        sql.append(" LIMIT ?").append(parameters.size() + 1).append(" OFFSET ?").append(parameters.size() + 2);
        parameters.add(size);
        parameters.add((long) page * size);
        Query query = entityManager.createNativeQuery(sql.toString());
        bind(query, parameters);
        List<?> rows = query.getResultList();
        List<NotificationDeliveryRow> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            result.add(toRow((Object[]) row));
        }
        return result;
    }

    private static void appendFilters(StringBuilder sql, List<Object> parameters, NotificationDeliveryFilter filter) {
        if (filter.status() != null) {
            parameters.add(filter.status().name());
            sql.append(" AND status = ?").append(parameters.size());
        }
        if (filter.origin() != null) {
            parameters.add(filter.origin().name());
            sql.append(" AND origin = ?").append(parameters.size());
        }
        if (filter.source() != null) {
            parameters.add(filter.source().name());
            sql.append(" AND source = ?").append(parameters.size());
        }
        if (filter.operation() != null && !filter.operation().isBlank()) {
            parameters.add(filter.operation().trim());
            sql.append(" AND operation = ?").append(parameters.size());
        }
        if (filter.from() != null) {
            parameters.add(Timestamp.from(filter.from().toInstant()));
            sql.append(" AND occurred_at >= ?").append(parameters.size());
        }
        if (filter.to() != null) {
            parameters.add(Timestamp.from(filter.to().toInstant()));
            sql.append(" AND occurred_at < ?").append(parameters.size());
        }
    }

    private static void bind(Query query, List<Object> parameters) {
        for (int index = 0; index < parameters.size(); index++) {
            query.setParameter(index + 1, parameters.get(index));
        }
    }

    private static NotificationDeliveryRow toRow(Object[] row) {
        return new NotificationDeliveryRow(
            NotificationDeliveryOrigin.valueOf((String) row[0]),
            ((Number) row[1]).longValue(),
            NotificationSource.valueOf((String) row[2]),
            (String) row[3],
            (String) row[4],
            NotificationStatus.valueOf((String) row[5]),
            toZoned(row[6]),
            ((Number) row[7]).intValue(),
            toZoned(row[8]),
            toZoned(row[9]),
            (String) row[10],
            (String) row[11],
            (String) row[12]
        );
    }

    private static ZonedDateTime toZoned(Object value) {
        if (value == null) return null;
        if (value instanceof ZonedDateTime zoned) return zoned;
        if (value instanceof OffsetDateTime offset) return offset.toZonedDateTime();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atZone(java.time.ZoneId.systemDefault());
        if (value instanceof Instant instant) return instant.atZone(java.time.ZoneId.systemDefault());
        throw new IllegalStateException("Unsupported timestamp type: " + value.getClass().getName());
    }

    /** Aggregato a bassa cardinalità: nessun identificativo, solo conteggio e istante. */
    public record NotificationDeliverySummary(long failureCount, ZonedDateTime oldestOccurredAt) {}

    /** Filtri della console: origine, sorgente, operazione e intervallo temporale. */
    public record NotificationDeliveryFilter(
        NotificationStatus status,
        NotificationDeliveryOrigin origin,
        NotificationSource source,
        String operation,
        ZonedDateTime from,
        ZonedDateTime to
    ) {}

    /** Riga tecnica grezza; la chiave evento viene sempre hashata prima di uscire dal servizio. */
    public record NotificationDeliveryRow(
        NotificationDeliveryOrigin origin,
        long id,
        NotificationSource source,
        String operation,
        String deliveryType,
        NotificationStatus status,
        ZonedDateTime occurredAt,
        int attempts,
        ZonedDateTime editDate,
        ZonedDateTime nextAttemptAt,
        String lastError,
        String skipReason,
        String eventKey
    ) {}
}
