package dev.jagadeesh.oms.common.web;

/** A request that is well formed but breaks a rule (duplicate email, idempotency key reused with another body...). */
public class BusinessRuleException extends RuntimeException {
    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
