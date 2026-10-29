package lk.bookhive.report.domain;

/**
 * What a stock-take scan found.
 *
 * <ul>
 *   <li>FOUND: in scope and AVAILABLE: it belongs here
 *   <li>NOT_IN_SCOPE: a real copy, but of another category or shelf
 *   <li>NOT_ON_SHELF: the system says it's on loan, lost, withdrawn...: check it at the desk
 *   <li>UNKNOWN: the barcode isn't in the catalog
 * </ul>
 */
public enum ScanResult {
  FOUND,
  NOT_IN_SCOPE,
  NOT_ON_SHELF,
  UNKNOWN
}
