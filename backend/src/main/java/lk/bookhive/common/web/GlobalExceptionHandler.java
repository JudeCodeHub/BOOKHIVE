package lk.bookhive.common.web;

import lk.bookhive.common.exception.BookHiveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an RFC 7807 {@link ProblemDetail} with an extra {@code code} field:
 *
 * <pre>{"type":"about:blank","title":"Not Found","status":404,
 *  "detail":"Book 42 was not found","instance":"/api/v1/books/42","code":"BOOK_NOT_FOUND"}</pre>
 *
 * <p>Spring's own errors (bad JSON, wrong method, unknown URL...) are handled by the parent class
 * and get a code from their HTTP status, e.g. {@code METHOD_NOT_ALLOWED}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  public static final String CODE = "code";

  @ExceptionHandler(BookHiveException.class)
  public ProblemDetail handleBookHive(BookHiveException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setProperty(CODE, ex.getCode());
    return problem;
  }

  // Last resort: log the real error, but never send internal details to the client.
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) throws Exception {
    // Let Spring Security turn these into 401/403 instead of a 500.
    if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
      throw ex;
    }
    log.error("Unexpected error", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
    problem.setProperty(CODE, "INTERNAL_ERROR");
    return problem;
  }

  // Called for every error the parent class handles; adds the code if it's missing.
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
      HttpStatus status = HttpStatus.resolve(statusCode.value());
      problem.setProperty(CODE, status != null ? status.name() : "ERROR");
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }
}
