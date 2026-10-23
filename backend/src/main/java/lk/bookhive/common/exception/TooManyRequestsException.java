package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/** The client sent too many requests in a short time ({@code TOO_MANY_REQUESTS}). Maps to 429. */
public class TooManyRequestsException extends BookHiveException {

  public TooManyRequestsException(String message) {
    super("TOO_MANY_REQUESTS", message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.TOO_MANY_REQUESTS;
  }
}
