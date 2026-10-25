package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.service.MembershipService;
import lk.bookhive.member.service.MembershipService.Renewal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's own membership. */
@RestController
@RequestMapping("/me/membership")
@Tag(name = "Membership")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MembershipController {

  private final MembershipService memberships;

  public MembershipController(MembershipService memberships) {
    this.memberships = memberships;
  }

  /**
   * @param feeDue membership fee now owed in LKR (0 for free types); paid later like a fine
   */
  public record RenewalResponse(
      String memberNo, MemberStatus status, LocalDate expiresAt, BigDecimal feeDue) {

    static RenewalResponse from(Renewal renewal) {
      Member member = renewal.member();
      return new RenewalResponse(
          member.getMemberNo(), member.getStatus(), member.getExpiresAt(), renewal.fee().amount());
    }
  }

  @PostMapping("/renew")
  @Operation(summary = "Renew for another year (from 30 days before expiry)")
  public RenewalResponse renew(@AuthenticationPrincipal Jwt jwt) {
    return RenewalResponse.from(memberships.renewOwn(UUID.fromString(jwt.getSubject())));
  }
}
