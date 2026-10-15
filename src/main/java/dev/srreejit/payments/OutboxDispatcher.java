package dev.srreejit.payments;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name="outbox.enabled", havingValue="true", matchIfMissing=true)
public class OutboxDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int maxAttempts;
    public OutboxDispatcher(JdbcTemplate jdbc, PlatformTransactionManager manager, EventPublisher publisher,
                            Clock clock, @Value("${outbox.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("max attempts must be between 1 and 20");
        this.jdbc = jdbc; tx = new TransactionTemplate(manager); this.publisher = publisher; this.clock = clock; this.maxAttempts = maxAttempts;
    }
    private record Pending(String id, String payment, String payload, int attempts) {}
    @Scheduled(fixedDelayString="${outbox.poll-delay-ms:1000}")
    public void scheduled() { dispatchOne(); }
    public boolean dispatchOne() {
        Boolean result = tx.execute(transaction -> {
            var rows = jdbc.query("select event_id,payment_id,payload,attempts from outbox where state='PENDING' and next_attempt_ms<=? order by next_attempt_ms,event_id limit 1 for update skip locked",
                (rs,n) -> new Pending(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4)), clock.millis());
            if (rows.isEmpty()) return false;
            var event = rows.get(0);
            int attempts = event.attempts() + 1;
            String state = "DELIVERED";
            long next = clock.millis();
            try { publisher.publish(event.id(), event.payment(), event.payload()); }
            catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                state = attempts >= maxAttempts ? "DEAD" : "PENDING";
                next += RetryPolicy.delayMs(attempts);
                // No payload, signature, secret or provider response is written to logs.
                LOG.warn("Outbox delivery failed eventId={} attempt={} state={} cause={}", event.id(), attempts, state, ex.getClass().getSimpleName());
            }
            jdbc.update("update outbox set state=?,attempts=?,next_attempt_ms=? where event_id=?", state, attempts, next, event.id());
            return true;
        });
        return Boolean.TRUE.equals(result);
    }
}
