package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.domain.Language;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public account endpoints under /api/v1/auth (no login needed, see SecurityConfig). */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Register, verify email, login")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create an account; a verification link is emailed")
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    Language language =
        request.preferredLanguage() == null
            ? Language.EN
            : Language.fromCode(request.preferredLanguage());
    User user =
        authService.register(
            request.email(), request.password(), request.fullName(), request.phone(), language);
    return new RegisterResponse(user.getId(), user.getEmail(), user.getStatus());
  }

  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Confirm the email address with the token from the email link")
  public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request.token());
  }
}
