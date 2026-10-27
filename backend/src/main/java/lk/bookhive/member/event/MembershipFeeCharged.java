package lk.bookhive.member.event;

import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.Money;

/**
 * A membership fee is owed (renewal of a paid type). The fine module turns it into a fine the
 * member pays online or at the desk (FIN-2). Also published when a paid type is first approved.
 *
 * @param validUntil the end of the year this fee pays for
 */
public record MembershipFeeCharged(
    UUID memberId, UUID userId, String memberNo, Money amount, LocalDate validUntil) {}
