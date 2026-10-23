package lk.bookhive.common.exception;

/**
 * A request field broke a rule that annotations can't express (e.g. "password is too common").
 * Answered exactly like a {@code @Valid} failure: 400 VALIDATION_FAILED with this field in the
 * {@code errors} list, so the frontend shows it under the right input.
 */
public class FieldValidationException extends RuntimeException {

  private final String field;
  private final String code;

  /**
   * @param field the request field name, e.g. "newPassword"
   * @param code machine-readable reason, e.g. "PASSWORD_TOO_COMMON"
   */
  public FieldValidationException(String field, String code, String message) {
    super(message);
    this.field = field;
    this.code = code;
  }

  public String getField() {
    return field;
  }

  public String getCode() {
    return code;
  }
}
