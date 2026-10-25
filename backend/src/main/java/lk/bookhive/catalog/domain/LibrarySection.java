package lk.bookhive.catalog.domain;

/**
 * The shelf area a category's books belong to (ADR 0003). Membership types may limit borrowing to
 * some sections (Junior: CHILDREN and YOUNG_ADULT).
 */
public enum LibrarySection {
  ADULT,
  CHILDREN,
  YOUNG_ADULT,
  REFERENCE
}
