package dev.srreejit.payments;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.srreejit.payments.Models.*;

class RulesTest {
    final UUID id = UUID.randomUUID();
    WebhookEvent event(Status status) { return new WebhookEvent("evt-1", id, 1000, "INR", status); }
    @Test void permitsInitialTerminalTransition() {
        assertTrue(Rules.transition(new Payment(id,1000,"INR",Status.CREATED), event(Status.SUCCEEDED)));
    }
    @Test void sameTerminalOutcomeHasNoSecondBusinessEffect() {
        assertFalse(Rules.transition(new Payment(id,1000,"INR",Status.SUCCEEDED), event(Status.SUCCEEDED)));
    }
    @Test void rejectsContradictoryTerminalOutcome() {
        assertEquals(409, assertThrows(DomainException.class, () -> Rules.transition(
            new Payment(id,1000,"INR",Status.SUCCEEDED), event(Status.FAILED))).httpStatus());
    }
    @Test void rejectsMismatchedMoney() {
        assertThrows(DomainException.class, () -> Rules.transition(new Payment(id,2000,"INR",Status.CREATED), event(Status.SUCCEEDED)));
        assertThrows(DomainException.class, () -> Rules.transition(new Payment(id,1000,"USD",Status.CREATED), event(Status.SUCCEEDED)));
    }
    @Test void rejectsInvalidEventAndMoney() {
        assertThrows(DomainException.class, () -> Rules.event(event(Status.CREATED)));
        assertThrows(DomainException.class, () -> Rules.event(new WebhookEvent("",id,1000,"INR",Status.SUCCEEDED)));
        assertThrows(DomainException.class, () -> Rules.money(0,"INR"));
        assertThrows(DomainException.class, () -> Rules.money(1000,"ZZZ"));
        assertThrows(DomainException.class, () -> Rules.money(1000,"inr"));
    }
    @Test void backoffIsBoundedEvenForHugeAttemptCount() {
        assertEquals(1000, RetryPolicy.delayMs(1)); assertEquals(2000, RetryPolicy.delayMs(2));
        assertEquals(60000, RetryPolicy.delayMs(100000));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.delayMs(0));
    }
}
