package dev.srreejit.payments;

import java.util.Currency;
import static dev.srreejit.payments.Models.*;

public final class Rules {
    private Rules() {}
    public static void money(long amountMinor, String currency) {
        if (amountMinor <= 0 || amountMinor > 9_999_999_999_999L)
            throw new DomainException(400, "amountMinor must be a positive supported integer");
        try {
            if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException();
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) { throw new DomainException(400, "currency must be an uppercase ISO currency code"); }
    }
    public static void event(WebhookEvent event) {
        if (event.eventId() == null || !event.eventId().matches("[A-Za-z0-9_-]{1,80}"))
            throw new DomainException(400, "eventId must contain 1-80 letters, digits, underscores or hyphens");
        if (event.paymentId() == null || event.status() == null || event.status() == Status.CREATED)
            throw new DomainException(400, "paymentId and a terminal status are required");
        money(event.amountMinor(), event.currency());
    }
    /** Returns false for another event describing the already reached state. */
    public static boolean transition(Payment payment, WebhookEvent event) {
        if (payment.amountMinor() != event.amountMinor() || !payment.currency().equals(event.currency()))
            throw new DomainException(409, "event amount or currency does not match payment");
        if (payment.status() == event.status()) return false;
        if (payment.status() != Status.CREATED)
            throw new DomainException(409, "a terminal payment cannot change to a different terminal state");
        return true;
    }
}
