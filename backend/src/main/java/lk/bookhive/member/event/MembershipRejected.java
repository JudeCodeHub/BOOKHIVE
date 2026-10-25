package lk.bookhive.member.event;

import java.util.UUID;
import lk.bookhive.common.domain.Language;

/** Published when staff reject an application; the applicant is emailed the reason. */
public record MembershipRejected(
    UUID userId, String email, String fullName, Language language, String reason) {}
