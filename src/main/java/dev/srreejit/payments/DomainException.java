package dev.srreejit.payments;

public class DomainException extends RuntimeException {
    private final int httpStatus;
    public DomainException(int httpStatus, String message) { super(message); this.httpStatus = httpStatus; }
    public int httpStatus() { return httpStatus; }
}
