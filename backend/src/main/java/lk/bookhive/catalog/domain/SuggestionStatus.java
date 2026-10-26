package lk.bookhive.catalog.domain;

/** Where a purchase suggestion stands (CAT-11). */
public enum SuggestionStatus {
  /** Waiting for staff to look at it */
  SUBMITTED,
  /** The library has ordered it */
  ORDERED,
  /** It's in the catalog now (linked to the book) */
  ACQUIRED,
  /** Not buying it; the staff note says why */
  DECLINED
}
