package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is valid but breaks a library rule, e.g. {@code LOAN_LIMIT_REACHED} or {@code
 * FINES_BLOCK_BORROWING}. Maps to 422.
 */
public class BusinessRuleException extends BookHiveException {

  public BusinessRuleException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.UNPROCESSABLE_CONTENT;
  }
}
