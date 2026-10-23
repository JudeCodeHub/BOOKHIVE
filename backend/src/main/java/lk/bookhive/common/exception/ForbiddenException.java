package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/**
 * We know who you are, but you may not do this right now, e.g. {@code EMAIL_NOT_VERIFIED} or {@code
 * ACCOUNT_DISABLED}. Maps to 403.
 */
public class ForbiddenException extends BookHiveException {

  public ForbiddenException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.FORBIDDEN;
  }
}
