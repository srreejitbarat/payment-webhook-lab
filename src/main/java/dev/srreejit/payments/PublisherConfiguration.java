package dev.srreejit.payments;

import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class PublisherConfiguration {
    @Bean @ConditionalOnProperty(name="outbox.publisher", havingValue="log", matchIfMissing=true)
    EventPublisher logPublisher() {
        return (id, payment, payload) -> LoggerFactory.getLogger("payment-events")
            .info("Demo sink acknowledged eventId={} paymentId={}", id, payment);
    }
    @Bean @ConditionalOnProperty(name="outbox.publisher", havingValue="kafka")
    EventPublisher kafkaPublisher(KafkaTemplate<String,String> kafka, @Value("${outbox.topic}") String topic) {
        return (id, payment, payload) -> kafka.send(topic, payment, payload).get(5, TimeUnit.SECONDS);
    }
}
