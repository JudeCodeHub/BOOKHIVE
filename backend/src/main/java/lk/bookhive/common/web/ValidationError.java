package lk.bookhive.common.web;

/**
 * One invalid field in a request, e.g. {@code {"field":"email","code":"Email","message":"must be
 * a well-formed email address"}}. {@code code} is the constraint name (NotBlank, Size, Email...)
 * so the frontend can show a translated message.
 */
public record ValidationError(String field, String code, String message) {}
