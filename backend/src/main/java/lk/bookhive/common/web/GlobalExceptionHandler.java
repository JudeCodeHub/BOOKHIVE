package lk.bookhive.common.web;

import java.util.ArrayList;
import java.util.List;
import lk.bookhive.common.exception.BookHiveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception into an RFC 7807 {@link ProblemDetail} with an extra {@code code} field:
 *
 * <pre>{"type":"about:blank","title":"Not Found","status":404,
 *  "detail":"Book 42 was not found","instance":"/api/v1/books/42","code":"BOOK_NOT_FOUND"}</pre>
 *
 * <p>Validation failures get {@code code=VALIDATION_FAILED} plus an {@code errors} list with one
 * {@link ValidationError} per invalid field.
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

  // Not logged in, or the token is missing/invalid/expired. Also reached from Spring Security's
  // filters through SecurityConfig, so every 401 has the same JSON shape.
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleUnauthenticated(AuthenticationException ex) {
    return unauthorized();
  }

  // Logged in, but not allowed (e.g. a member calling a staff endpoint).
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    // An anonymous visitor hitting a protected method should be told to log in, not "forbidden"
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken) {
      return unauthorized();
    }
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You don't have permission to do this.");
    problem.setProperty(CODE, "FORBIDDEN");
    return problem;
  }

  private static ProblemDetail unauthorized() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
    problem.setProperty(CODE, "UNAUTHORIZED");
    return problem;
  }

  // Last resort: log the real error, but never send internal details to the client.
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
    problem.setProperty(CODE, "INTERNAL_ERROR");
    return problem;
  }

  // @Valid @RequestBody failed: list every invalid field.
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationError> errors =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                error ->
                    new ValidationError(
                        error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName(),
                        error.getCode(),
                        error.getDefaultMessage()))
            .toList();
    return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
  }

  // Constraints on @RequestParam / @PathVariable failed, e.g. @Min(0) int page.
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationError> errors = new ArrayList<>();
    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      String field = result.getMethodParameter().getParameterName();
      for (MessageSourceResolvable error : result.getResolvableErrors()) {
        errors.add(new ValidationError(field, constraintName(error), error.getDefaultMessage()));
      }
    }
    return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
  }

  private static ProblemDetail validationProblem(List<ValidationError> errors) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are invalid.");
    problem.setProperty(CODE, "VALIDATION_FAILED");
    problem.setProperty("errors", errors);
    return problem;
  }

  // Codes go from specific to general ("Min.page", "Min"); the last one is the constraint name.
  private static String constraintName(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    return codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
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
