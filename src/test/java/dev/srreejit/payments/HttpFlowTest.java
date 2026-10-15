package dev.srreejit.payments;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "payment.api-key=test-only-api-key-with-at-least-32-characters","payment.webhook-secret="+SignatureVerifierTest.SECRET,"outbox.enabled=false"})
class HttpFlowTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    final HttpClient http=HttpClient.newHttpClient();
    static final String KEY="test-only-api-key-with-at-least-32-characters";
    HttpResponse<String> post(String path,String body,String key) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(key!=null)builder.header("X-Api-Key",key);
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> webhook(String body,String sig) throws Exception {
        String ts=""+Instant.now().getEpochSecond();
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/webhooks/payments"))
            .header("Content-Type","application/json").header("X-Webhook-Timestamp",ts)
            .header("X-Webhook-Signature",sig==null?SignatureVerifier.sign(SignatureVerifierTest.SECRET,ts,body.getBytes(StandardCharsets.UTF_8)):sig)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(req,HttpResponse.BodyHandlers.ofString());
    }
    @Test void authenticatesAdminAndHandlesRealSignedDuplicateOverHttp() throws Exception {
        assertEquals(401,post("/api/payments","{\"amountMinor\":1000,\"currency\":\"INR\"}",null).statusCode());
        var created=post("/api/payments","{\"amountMinor\":1000,\"currency\":\"INR\"}",KEY);
        assertEquals(201,created.statusCode()); String id=json.readTree(created.body()).get("paymentId").asString();
        String body="{\"eventId\":\"http-success\",\"paymentId\":\""+id+"\",\"amountMinor\":1000,\"currency\":\"INR\",\"status\":\"SUCCEEDED\"}";
        assertEquals("APPLIED",json.readTree(webhook(body,null).body()).get("disposition").asString());
        assertEquals("DUPLICATE",json.readTree(webhook(body,null).body()).get("disposition").asString());
    }
    @Test void rejectsForgedAndMalformedBodies() throws Exception {
        assertEquals(401,webhook("{}","00".repeat(32)).statusCode());
        assertEquals(400,webhook("invalid-json",null).statusCode());
        assertEquals(400,webhook("null",null).statusCode());
        assertEquals(400,post("/api/payments","{\"amountMinor\":-1,\"currency\":\"INR\"}",KEY).statusCode());
    }
    @Test void enforcesWebhookBodyLimit() throws Exception {
        assertEquals(413,webhook("x".repeat(65537),null).statusCode());
    }
    @Test void rejectsUnknownWebhookFieldsAndNullPayment() throws Exception {
        assertEquals(400,webhook("{\"eventId\":\"x\",\"unexpected\":true}",null).statusCode());
        assertEquals(400,post("/api/payments","null",KEY).statusCode());
    }
}
