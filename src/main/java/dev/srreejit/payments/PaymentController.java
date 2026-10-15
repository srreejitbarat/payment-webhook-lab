package dev.srreejit.payments;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import static dev.srreejit.payments.Models.*;

@RestController
public class PaymentController {
    private final PaymentService payments;
    private final SignatureVerifier signatures;
    private final ObjectMapper json;
    public PaymentController(PaymentService payments, Clock clock, ObjectMapper json,
                             @Value("${payment.webhook-secret}") String secret) {
        this.payments = payments; this.json = json; this.signatures = new SignatureVerifier(secret, clock);
    }
    @PostMapping("/api/payments") @ResponseStatus(HttpStatus.CREATED)
    public Payment create(@RequestBody CreatePayment input) { return payments.create(input); }
    @GetMapping("/api/payments/{id}") public Payment get(@PathVariable UUID id) { return payments.get(id); }
    @GetMapping("/api/outbox") public List<OutboxView> outbox() { return payments.outbox(); }
    @PostMapping(path="/webhooks/payments", consumes="application/json")
    public Ack webhook(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(65_537);
        if (body.length > 65_536) throw new DomainException(413, "webhook body exceeds 64 KiB");
        signatures.verify(body, request.getHeader("X-Webhook-Timestamp"), request.getHeader("X-Webhook-Signature"));
        WebhookEvent event;
        try { event = json.readValue(body, WebhookEvent.class); }
        catch (RuntimeException ex) { throw new DomainException(400, "invalid webhook JSON"); }
        if (event == null) throw new DomainException(400, "webhook must be an object");
        return payments.accept(event);
    }
}
