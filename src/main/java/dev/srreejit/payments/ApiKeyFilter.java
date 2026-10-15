package dev.srreejit.payments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private final byte[] expected;
    public ApiKeyFilter(@Value("${payment.api-key}") String key) {
        if (key.length() < 32) throw new IllegalArgumentException("API key must contain at least 32 characters");
        expected = key.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getServletPath().startsWith("/api/")) {
            String supplied = request.getHeader("X-Api-Key");
            if (supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401); response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"invalid API key\"}"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
