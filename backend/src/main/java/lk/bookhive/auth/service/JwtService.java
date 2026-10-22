package lk.bookhive.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import lk.bookhive.auth.config.JwtProperties;
import lk.bookhive.auth.domain.Role;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Creates the short-lived access tokens (ADR 0001). Checking them is done by Spring Security. */
@Service
public class JwtService {

  /** Claim holding the user's roles, e.g. ["MEMBER"]; turned into ROLE_MEMBER authorities. */
  public static final String ROLES_CLAIM = "roles";

  private final JwtEncoder encoder;
  private final JwtProperties properties;
  private final Clock clock;

  public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * A signed RS256 JWT for this user. It holds only the user id and roles: no email or name, since
   * anyone holding the token can read its contents (it's signed, not encrypted).
   */
  public AccessToken issueAccessToken(UUID userId, Collection<Role> roles) {
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(properties.accessTokenTtl());

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(ROLES_CLAIM, roles.stream().map(Role::name).sorted().toList())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();

    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new AccessToken(token, expiresAt);
  }

  /** The token string sent as "Authorization: Bearer ...", and when it stops working. */
  public record AccessToken(String value, Instant expiresAt) {}
}
