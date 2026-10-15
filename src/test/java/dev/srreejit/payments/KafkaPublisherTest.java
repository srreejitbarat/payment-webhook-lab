package dev.srreejit.payments;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaPublisherTest {
    @SuppressWarnings("unchecked")
    @Test void usesPaymentPartitionKeyAndWaitsForAcknowledgement() throws Exception {
        KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        when(kafka.send("events","payment-id","payload")).thenReturn(CompletableFuture.completedFuture(null));
        new PublisherConfiguration().kafkaPublisher(kafka,"events").publish("event-id","payment-id","payload");
        verify(kafka).send("events","payment-id","payload");
    }
    @SuppressWarnings("unchecked")
    @Test void doesNotTreatBrokerFailureAsDelivered() {
        KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        when(kafka.send("events","p","body")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        assertThrows(Exception.class,()->new PublisherConfiguration().kafkaPublisher(kafka,"events").publish("e","p","body"));
    }
}
