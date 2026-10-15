package dev.srreejit.payments;

import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static dev.srreejit.payments.Models.*;

/** Uses a real PostgreSQL service when explicitly supplied; never substitutes H2. */
@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL",matches=".+")
@SpringBootTest(properties={"spring.datasource.url=${TEST_POSTGRES_URL}","spring.datasource.username=${TEST_POSTGRES_USER:payments}",
    "spring.datasource.password=${TEST_POSTGRES_PASSWORD}","payment.api-key=test-only-api-key-with-at-least-32-characters",
    "payment.webhook-secret="+SignatureVerifierTest.SECRET,"outbox.enabled=false"})
class PostgresIntegrationTest {
    @Autowired PaymentService payments; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
    @BeforeEach void clean(){jdbc.update("delete from outbox");jdbc.update("delete from event_receipts");jdbc.update("delete from payments");}
    WebhookEvent seed(){var p=payments.create(new CreatePayment(1000,"INR"));return new WebhookEvent("postgres-event",p.paymentId(),1000,"INR",Status.SUCCEEDED);}
    @Test void concurrentDuplicateRequestsCommitOneEvent() throws Exception {
        var event=seed();var pool=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);
        try{
            var futures=new ArrayList<Future<Ack>>();
            for(int i=0;i<16;i++)futures.add(pool.submit(()->{start.await();return payments.accept(event);}));
            start.countDown();int applied=0;
            for(var future:futures)if(future.get(15,TimeUnit.SECONDS).disposition().equals("APPLIED"))applied++;
            assertEquals(1,applied);assertEquals(1,payments.outbox().size());
        }finally{pool.shutdownNow();}
    }
    @Test void transactionRollsBackWhenOutboxCannotBeInserted(){
        var event=seed();jdbc.execute("alter table outbox add constraint test_reject_outbox check (attempts > 100)");
        try{assertThrows(DataAccessException.class,()->payments.accept(event));}
        finally{jdbc.execute("alter table outbox drop constraint test_reject_outbox");}
        assertEquals(Status.CREATED,payments.get(event.paymentId()).status());
        assertEquals(0,jdbc.queryForObject("select count(*) from event_receipts",Integer.class));
    }
    @Test void concurrentWorkersDoNotClaimTheSameLockedRow() throws Exception {
        payments.accept(seed());var calls=new AtomicInteger();
        EventPublisher publisher=(id,p,body)->{calls.incrementAndGet();Thread.sleep(50);};
        var one=new OutboxDispatcher(jdbc,manager,publisher,Clock.systemUTC(),3);
        var two=new OutboxDispatcher(jdbc,manager,publisher,Clock.systemUTC(),3);
        var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(one::dispatchOne);var b=pool.submit(two::dispatchOne);a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}
        finally{pool.shutdownNow();}
        assertEquals(1,calls.get());assertEquals("DELIVERED",payments.outbox().get(0).state());
    }
}
