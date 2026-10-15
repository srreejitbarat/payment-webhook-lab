package dev.srreejit.payments;

import java.util.UUID;

public final class Models {
    private Models() {}
    public enum Status { CREATED, SUCCEEDED, FAILED }
    public record CreatePayment(long amountMinor, String currency) {}
    public record Payment(UUID paymentId, long amountMinor, String currency, Status status) {}
    public record WebhookEvent(String eventId, UUID paymentId, long amountMinor, String currency, Status status) {}
    public record Ack(String disposition, UUID paymentId, Status status) {}
    public record PublishedEvent(String eventId, UUID paymentId, Status status) {}
    public record OutboxView(String eventId, String state, int attempts, long nextAttemptMs) {}
}
