package lk.bookhive.common.text;

/**
 * Text going into CSV files that people open in Excel (attendee lists, report exports). Spreadsheet
 * programs run cells starting with = + - @ as formulas; a name or title typed by a member must
 * never do that, so such values get a leading apostrophe.
 */
public final class SpreadsheetCells {

  private SpreadsheetCells() {}

  /** The value, made safe to open in a spreadsheet; null becomes empty. */
  public static String safe(String value) {
    if (value == null) {
      return "";
    }
    return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
  }
}
