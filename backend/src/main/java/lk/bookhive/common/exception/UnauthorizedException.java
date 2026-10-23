package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Who you are couldn't be confirmed, e.g. {@code INVALID_CREDENTIALS} or {@code
 * INVALID_REFRESH_TOKEN}. Maps to 401.
 */
public class UnauthorizedException extends BookHiveException {

  public UnauthorizedException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.UNAUTHORIZED;
  }
}
