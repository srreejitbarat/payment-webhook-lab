package dev.srreejit.payments;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static dev.srreejit.payments.Models.*;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;
    public PaymentService(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.tx = new TransactionTemplate(manager); this.json = json; this.clock = clock;
    }
    public Payment create(CreatePayment input) {
        if (input == null) throw new DomainException(400, "payment must be an object");
        Rules.money(input.amountMinor(), input.currency());
        var id = UUID.randomUUID();
        jdbc.update("insert into payments(payment_id,amount_minor,currency,status) values (?,?,?,?)",
            id.toString(), input.amountMinor(), input.currency(), Status.CREATED.name());
        return get(id);
    }
    public Payment get(UUID id) { return payment(id, false); }
    private Payment payment(UUID id, boolean lock) {
        var rows = jdbc.query("select * from payments where payment_id=?" + (lock ? " for update" : ""),
            (rs, n) -> new Payment(UUID.fromString(rs.getString("payment_id")), rs.getLong("amount_minor"),
                rs.getString("currency"), Status.valueOf(rs.getString("status"))), id.toString());
        if (rows.isEmpty()) throw new DomainException(404, "payment not found");
        return rows.get(0);
    }
    private List<WebhookEvent> receipts(String eventId) {
        return jdbc.query("select * from event_receipts where event_id=?", (rs, n) ->
            new WebhookEvent(rs.getString("event_id"), UUID.fromString(rs.getString("payment_id")),
                rs.getLong("amount_minor"), rs.getString("currency"), Status.valueOf(rs.getString("status"))), eventId);
    }
    private Ack duplicate(WebhookEvent event, WebhookEvent original) {
        if (!original.equals(event)) throw new DomainException(409, "eventId was already used with different content");
        return new Ack("DUPLICATE", event.paymentId(), get(event.paymentId()).status());
    }
    public Ack accept(WebhookEvent event) {
        Rules.event(event);
        try {
            return tx.execute(transaction -> {
                // Serialize transitions for this payment across all application instances.
                Payment payment = payment(event.paymentId(), true);
                var existing = receipts(event.eventId());
                if (!existing.isEmpty()) return duplicate(event, existing.get(0));
                boolean apply = Rules.transition(payment, event);
                jdbc.update("insert into event_receipts(event_id,payment_id,amount_minor,currency,status) values (?,?,?,?,?)",
                    event.eventId(), event.paymentId().toString(), event.amountMinor(), event.currency(), event.status().name());
                if (apply) {
                    jdbc.update("update payments set status=? where payment_id=?", event.status().name(), event.paymentId().toString());
                    String payload = json.writeValueAsString(new PublishedEvent(event.eventId(), event.paymentId(), event.status()));
                    jdbc.update("insert into outbox(event_id,payment_id,payload,state,attempts,next_attempt_ms) values (?,?,?,'PENDING',0,?)",
                        event.eventId(), event.paymentId().toString(), payload, clock.millis());
                }
                return new Ack(apply ? "APPLIED" : "IGNORED", event.paymentId(), event.status());
            });
        } catch (DuplicateKeyException ex) {
            // A conflicting event ID on another payment can race our initial read.
            // The failed transaction must finish rollback before inspecting the winner.
            var existing = receipts(event.eventId());
            if (existing.isEmpty()) throw ex;
            return duplicate(event, existing.get(0));
        }
    }
    public List<OutboxView> outbox() {
        return jdbc.query("select event_id,state,attempts,next_attempt_ms from outbox order by event_id limit 100",
            (rs, n) -> new OutboxView(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getLong(4)));
    }
}
