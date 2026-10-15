package dev.srreejit.payments;

import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static dev.srreejit.payments.Models.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:service;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "payment.api-key=test-only-api-key-with-at-least-32-characters", "payment.webhook-secret="+SignatureVerifierTest.SECRET,"outbox.enabled=false"})
class PaymentServiceTest {
    @Autowired PaymentService service;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() { jdbc.update("delete from outbox"); jdbc.update("delete from event_receipts"); jdbc.update("delete from payments"); }
    WebhookEvent success(String eventId, Payment p) { return new WebhookEvent(eventId,p.paymentId(),p.amountMinor(),p.currency(),Status.SUCCEEDED); }
    int count(String table) { return jdbc.queryForObject("select count(*) from "+table,Integer.class); }
    @Test void commitsPaymentReceiptAndOutboxTogether() {
        var p=service.create(new CreatePayment(1000,"INR"));
        assertEquals("APPLIED",service.accept(success("evt-1",p)).disposition());
        assertEquals(Status.SUCCEEDED,service.get(p.paymentId()).status());
        assertEquals(1,count("event_receipts")); assertEquals(1,count("outbox"));
    }
    @Test void duplicateAndSameOutcomeEventsDoNotCreateAnotherOutbox() {
        var p=service.create(new CreatePayment(1000,"INR")); var e=success("evt-1",p);
        service.accept(e); assertEquals("DUPLICATE",service.accept(e).disposition());
        assertEquals("IGNORED",service.accept(success("evt-2",p)).disposition());
        assertEquals(2,count("event_receipts")); assertEquals(1,count("outbox"));
    }
    @Test void reusedEventIdWithDifferentPaymentIsConflict() {
        var one=service.create(new CreatePayment(1000,"INR")); var two=service.create(new CreatePayment(1000,"INR"));
        service.accept(success("shared",one));
        assertEquals(409,assertThrows(DomainException.class,()->service.accept(success("shared",two))).httpStatus());
        assertEquals(Status.CREATED,service.get(two.paymentId()).status());
    }
    @Test void contradictoryStatusAndWrongAmountAreRejected() {
        var p=service.create(new CreatePayment(1000,"INR"));
        assertThrows(DomainException.class,()->service.accept(new WebhookEvent("bad",p.paymentId(),999,"INR",Status.SUCCEEDED)));
        assertEquals(0,count("event_receipts")); service.accept(success("success",p));
        assertThrows(DomainException.class,()->service.accept(new WebhookEvent("failure",p.paymentId(),1000,"INR",Status.FAILED)));
        assertEquals(1,count("outbox"));
    }
    @Test void failedOutboxInsertRollsBackPaymentAndReceipt() {
        var p=service.create(new CreatePayment(1000,"INR"));
        jdbc.execute("alter table outbox add constraint test_reject_outbox check (attempts > 100)");
        try { assertThrows(DataAccessException.class,()->service.accept(success("rollback",p))); }
        finally { jdbc.execute("alter table outbox drop constraint test_reject_outbox"); }
        assertEquals(Status.CREATED,service.get(p.paymentId()).status());
        assertEquals(0,count("event_receipts")); assertEquals(0,count("outbox"));
    }
    @Test void concurrentDuplicateDeliveriesHaveOneBusinessEffect() throws Exception {
        var p=service.create(new CreatePayment(1000,"INR")); var event=success("race",p);
        var pool=Executors.newFixedThreadPool(8); var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Ack>>();
            for(int i=0;i<16;i++) futures.add(pool.submit(()->{start.await();return service.accept(event);}));
            start.countDown(); int applied=0;
            for(var future:futures) if(future.get(10,TimeUnit.SECONDS).disposition().equals("APPLIED")) applied++;
            assertEquals(1,applied); assertEquals(1,count("event_receipts")); assertEquals(1,count("outbox"));
        } finally { pool.shutdownNow(); }
    }
    @Test void crossPaymentEventIdRaceHasOneWinner() throws Exception {
        var one=service.create(new CreatePayment(1000,"INR"));var two=service.create(new CreatePayment(1000,"INR"));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<String> a=()->{start.await();try{return service.accept(success("shared-race",one)).disposition();}catch(DomainException ex){return "HTTP"+ex.httpStatus();}};
            Callable<String> b=()->{start.await();try{return service.accept(success("shared-race",two)).disposition();}catch(DomainException ex){return "HTTP"+ex.httpStatus();}};
            var fa=pool.submit(a);var fb=pool.submit(b);start.countDown();
            assertEquals(java.util.Set.of("APPLIED","HTTP409"),java.util.Set.of(fa.get(10,TimeUnit.SECONDS),fb.get(10,TimeUnit.SECONDS)));
            assertEquals(1,count("event_receipts"));assertEquals(1,count("outbox"));
        } finally {pool.shutdownNow();}
    }
}
