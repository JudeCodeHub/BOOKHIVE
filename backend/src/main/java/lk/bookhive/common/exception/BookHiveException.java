package lk.bookhive.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected domain errors. The {@code code} (e.g. {@code BOOK_NOT_FOUND}) is stable
 * and machine-readable: the frontend translates it, so never change an existing code.
 */
public abstract class BookHiveException extends RuntimeException {

  private final String code;

  protected BookHiveException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public abstract HttpStatus getStatus();
}
