package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/**
 * An outside service we depend on (e.g. Open Library) is down or too slow. Maps to 503: the user
 * can try again later, or carry on without it (type the book's details by hand).
 */
public class ServiceUnavailableException extends BookHiveException {

  public ServiceUnavailableException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.SERVICE_UNAVAILABLE;
  }
}
