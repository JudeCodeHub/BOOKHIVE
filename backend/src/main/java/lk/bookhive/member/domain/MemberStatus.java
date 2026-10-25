package lk.bookhive.member.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Membership states and the only allowed moves between them (PRD §4.1, checklist step 132):
 *
 * <pre>
 *   PENDING ──approve──> ACTIVE ──suspend──> SUSPENDED ──reactivate──> ACTIVE
 *      │                  │  ▲                   │
 *    reject            expire │ renew          expire
 *      ▼                  ▼  │                   ▼
 *   REJECTED ─reapply─> PENDING   EXPIRED <──────┘
 * </pre>
 *
 * Anything else (EXPIRED → PENDING, REJECTED → ACTIVE...) is refused.
 */
public enum MemberStatus {
  PENDING,
  ACTIVE,
  SUSPENDED,
  EXPIRED,
  REJECTED;

  private static final Map<MemberStatus, Set<MemberStatus>> ALLOWED =
      Map.of(
          PENDING, EnumSet.of(ACTIVE, REJECTED),
          REJECTED, EnumSet.of(PENDING),
          ACTIVE, EnumSet.of(SUSPENDED, EXPIRED, ACTIVE), // ACTIVE -> ACTIVE: early renewal
          SUSPENDED, EnumSet.of(ACTIVE, EXPIRED),
          EXPIRED, EnumSet.of(ACTIVE));

  public boolean canMoveTo(MemberStatus next) {
    return ALLOWED.get(this).contains(next);
  }
}
