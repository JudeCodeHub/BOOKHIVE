package lk.bookhive.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/login}. */
public record LoginRequest(
    @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {

  // Never print the password into logs
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + "]";
  }
}
