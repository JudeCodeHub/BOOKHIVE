package lk.bookhive.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.NotificationType;
import lk.bookhive.notification.service.TemplatePreviewService;
import lk.bookhive.notification.service.TemplatePreviewService.Preview;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Message template previews for admins (step 324): sample data, nothing sent. */
@RestController
@RequestMapping("/admin/notifications/templates")
@Tag(name = "Admin: notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminTemplateController {

  private final TemplatePreviewService previews;

  public AdminTemplateController(TemplatePreviewService previews) {
    this.previews = previews;
  }

  /**
   * @param channels where this type goes by default
   * @param optional members may switch it off
   */
  public record TemplateTypeSummary(
      NotificationType type, List<Channel> channels, boolean optional) {}

  /**
   * @param emailHtml show it in a sandboxed frame only
   */
  public record TemplatePreviewResponse(
      NotificationType type,
      String language,
      String emailSubject,
      String emailText,
      String emailHtml,
      String sms,
      String inAppTitle,
      String inAppBody) {

    static TemplatePreviewResponse from(Preview p) {
      return new TemplatePreviewResponse(
          p.type(),
          p.language().code(),
          p.email() == null ? null : p.email().subject(),
          p.email() == null ? null : p.email().text(),
          p.email() == null ? null : p.email().html(),
          p.sms(),
          p.inAppTitle(),
          p.inAppBody());
    }
  }

  @GetMapping
  @Operation(summary = "Every message type with its channels")
  public List<TemplateTypeSummary> types() {
    return previews.types().stream()
        .map(
            type ->
                new TemplateTypeSummary(
                    type, type.defaultChannels().stream().sorted().toList(), type.optional()))
        .toList();
  }

  public record TestEmailResponse(String sentTo) {}

  @PostMapping("/{type}/test")
  @Operation(summary = "Send the sample email of this type to me ([TEST], via the outbox)")
  public TestEmailResponse sendTest(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable NotificationType type,
      @RequestParam(defaultValue = "en") @Pattern(regexp = "en|si|ta") String lang) {
    return new TestEmailResponse(
        previews.sendTestEmail(type, Language.fromCode(lang), UUID.fromString(jwt.getSubject())));
  }

  @GetMapping("/{type}/preview")
  @Operation(summary = "One message type rendered with sample data in a language")
  public TemplatePreviewResponse preview(
      @PathVariable NotificationType type,
      @RequestParam(defaultValue = "en") @Pattern(regexp = "en|si|ta") String lang) {
    return TemplatePreviewResponse.from(previews.preview(type, Language.fromCode(lang)));
  }
}
