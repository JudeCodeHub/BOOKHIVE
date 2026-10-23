package lk.bookhive.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for the password endpoints. toString() never prints passwords or tokens. */
final class PasswordRequests {

  private PasswordRequests() {}

  /** {@code POST /api/v1/auth/forgot-password} */
  record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}

  /** {@code POST /api/v1/auth/reset-password}: the token from the email link + new password. */
  record ResetPasswordRequest(
      @NotBlank @Size(max = 100) String token,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {

    @Override
    public String toString() {
      return "ResetPasswordRequest[***]";
    }
  }

  /** {@code POST /api/v1/me/password} */
  record ChangePasswordRequest(
      @NotBlank @Size(max = 200) String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {

    @Override
    public String toString() {
      return "ChangePasswordRequest[***]";
    }
  }
}
