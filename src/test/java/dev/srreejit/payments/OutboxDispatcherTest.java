package dev.srreejit.payments;

import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static dev.srreejit.payments.Models.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:outbox;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "payment.api-key=test-only-api-key-with-at-least-32-characters","payment.webhook-secret="+SignatureVerifierTest.SECRET,"outbox.enabled=false"})
class OutboxDispatcherTest {
    @Autowired PaymentService payments; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
    final MutableClock clock=new MutableClock();
    static class MutableClock extends Clock {
        long now=2_000_000_000_000L;
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(now);}
    }
    @BeforeEach void seed() {
        jdbc.update("delete from outbox"); jdbc.update("delete from event_receipts"); jdbc.update("delete from payments");
        var p=payments.create(new CreatePayment(500,"INR"));
        payments.accept(new WebhookEvent("dispatch",p.paymentId(),500,"INR",Status.SUCCEEDED));
        jdbc.update("update outbox set next_attempt_ms=?",clock.millis());
    }
    OutboxView event(){return payments.outbox().get(0);}
    @Test void acknowledgesSuccessfulDeliveryOnce() {
        var calls=new AtomicInteger(); var worker=new OutboxDispatcher(jdbc,manager,(id,p,payload)->calls.incrementAndGet(),clock,3);
        assertTrue(worker.dispatchOne()); assertEquals("DELIVERED",event().state()); assertEquals(1,event().attempts());
        assertFalse(worker.dispatchOne()); assertEquals(1,calls.get());
    }
    @Test void retryWaitsUntilDueThenSucceeds() {
        var calls=new AtomicInteger(); var worker=new OutboxDispatcher(jdbc,manager,(id,p,payload)->{
            if(calls.incrementAndGet()==1)throw new IllegalStateException("simulated transient failure");
        },clock,3);
        worker.dispatchOne(); assertEquals("PENDING",event().state());
        assertEquals(clock.millis()+1000,event().nextAttemptMs()); assertFalse(worker.dispatchOne());
        clock.now+=1000; worker.dispatchOne(); assertEquals("DELIVERED",event().state()); assertEquals(2,event().attempts());
    }
    @Test void repeatedFailuresStopAtConfiguredLimit() {
        var worker=new OutboxDispatcher(jdbc,manager,(id,p,payload)->{throw new IllegalStateException("simulated outage");},clock,3);
        worker.dispatchOne(); clock.now+=1000; worker.dispatchOne(); clock.now+=2000; worker.dispatchOne();
        assertEquals("DEAD",event().state()); assertEquals(3,event().attempts());
        clock.now+=600000; assertFalse(worker.dispatchOne());
    }
}
