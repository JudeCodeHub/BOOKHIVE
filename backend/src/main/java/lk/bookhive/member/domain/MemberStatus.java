package lk.bookhive.member.domain;

/** PRD §4.1: PENDING → ACTIVE → (SUSPENDED | EXPIRED) → ACTIVE, or PENDING → REJECTED. */
public enum MemberStatus {
  PENDING,
  ACTIVE,
  SUSPENDED,
  EXPIRED,
  REJECTED
}
