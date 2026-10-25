package lk.bookhive.member.event;

import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.Language;

/** Published when staff approve an application; the notification module emails the member. */
public record MembershipApproved(
    UUID userId,
    String email,
    String fullName,
    Language language,
    String memberNo,
    String membershipType,
    LocalDate expiresAt) {}
