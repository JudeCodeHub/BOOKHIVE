package lk.bookhive.circulation.domain;

/**
 * Where a loan stands:
 *
 * <pre>
 *   ACTIVE ──(due date passes, nightly job)──> OVERDUE
 *     │  ▲ renew                                 │ renew (resets to ACTIVE)
 *     │  └───────────────────────────────────────┘
 *     ├──return──> RETURNED          OVERDUE ──return──> RETURNED
 *     └──lost────> LOST              OVERDUE ──lost────> LOST ──found──> RETURNED
 * </pre>
 */
public enum LoanStatus {
  ACTIVE,
  OVERDUE,
  RETURNED,
  LOST;

  /** Still out (the copy is with the member). */
  public boolean isOpen() {
    return this == ACTIVE || this == OVERDUE;
  }
}
