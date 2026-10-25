package lk.bookhive.catalog.domain;

/**
 * Where a copy is right now (ADR 0003). Changes are recorded in copy_status_history (step 166).
 *
 * <ul>
 *   <li>ON_HOLD_SHELF: set aside for a member who reserved the title, waiting for pickup.
 *   <li>IN_TRANSIT: on its way back to the shelf (e.g. returned at the desk, not shelved yet).
 *   <li>WITHDRAWN: removed from the collection; kept for loan and fine history.
 * </ul>
 */
public enum CopyStatus {
  AVAILABLE,
  ON_LOAN,
  ON_HOLD_SHELF,
  IN_TRANSIT,
  IN_REPAIR,
  LOST,
  WITHDRAWN
}
