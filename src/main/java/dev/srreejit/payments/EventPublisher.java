package dev.srreejit.payments;

public interface EventPublisher {
    void publish(String eventId, String paymentId, String payload) throws Exception;
}
