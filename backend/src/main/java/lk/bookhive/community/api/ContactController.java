package lk.bookhive.community.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.bookhive.common.domain.Language;
import lk.bookhive.community.service.ContactService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public contact form (rate limited per IP, and protected by Cloudflare Turnstile). */
@RestController
@RequestMapping("/contact")
@Tag(name = "Library")
public class ContactController {

  private final ContactService contact;

  public ContactController(ContactService contact) {
    this.contact = contact;
  }

  /**
   * @param turnstileToken from the Turnstile widget on the form
   */
  public record ContactRequest(
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String subject,
      @NotBlank @Size(min = 10, max = 5000) String message,
      @Pattern(regexp = "^(en|si|ta)$") String language,
      @Size(max = 2048) String turnstileToken) {}

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Send a message to the library")
  public void send(@Valid @RequestBody ContactRequest request, HttpServletRequest http) {
    contact.submit(
        request.name(),
        request.email(),
        request.subject(),
        request.message(),
        request.language() == null ? Language.EN : Language.fromCode(request.language()),
        request.turnstileToken(),
        http.getRemoteAddr());
  }
}
