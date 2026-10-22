package lk.bookhive.auth.domain;

/**
 * What a user may do (PRD §3). Stored by name in {@code user_roles.role}, so never rename a
 * constant without a migration.
 */
public enum Role {
  /** Approved library member: borrow, renew, reserve, pay fines. */
  MEMBER,
  /** Library staff: desk checkout/return, cataloging, approvals, small fine waivers. */
  LIBRARIAN,
  /** Head librarian / IT: everything, plus library rules, staff accounts and reports. */
  ADMIN
}
