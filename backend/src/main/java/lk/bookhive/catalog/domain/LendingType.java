package lk.bookhive.catalog.domain;

/** Whether a copy may leave the library (CAT-7). */
public enum LendingType {
  LENDABLE,
  REFERENCE_ONLY,
  RESTRICTED
}
