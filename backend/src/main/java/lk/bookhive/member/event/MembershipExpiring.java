package lk.bookhive.member.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A membership ends on {@code expiresAt}, a few weeks from now (daily job, checklist step 317):
 * time to remind the member to renew. Once per member and expiry date.
 */
public record MembershipExpiring(
    UUID memberId, UUID userId, String memberNo, LocalDate expiresAt) {}
