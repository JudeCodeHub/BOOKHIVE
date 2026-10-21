package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/** The requested thing doesn't exist, e.g. {@code BOOK_NOT_FOUND}. Maps to 404. */
public class NotFoundException extends BookHiveException {

  public NotFoundException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus getStatus() {
    return HttpStatus.NOT_FOUND;
  }
}
