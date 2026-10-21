package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The request clashes with the current state, e.g. {@code EMAIL_ALREADY_REGISTERED} or {@code
 * COPY_ALREADY_ON_LOAN}. Maps to 409.
 */
public class ConflictException extends BookHiveException {

  public ConflictException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.CONFLICT;
  }
}
