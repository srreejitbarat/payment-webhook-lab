package dev.srreejit.payments;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String,String>> domain(DomainException error) {
        return ResponseEntity.status(error.httpStatus()).body(Map.of("error", error.getMessage()));
    }
}
