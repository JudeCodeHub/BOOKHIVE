package lk.bookhive.report.export;

import java.util.List;

/**
 * A report ready to download (checklist step 366): a title, a few lines about it (dates, notes),
 * and one or more tables. Every cell is already text in the chosen language; the CSV and PDF
 * writers only lay it out.
 *
 * @param fileName without extension, e.g. "bookhive-circulation-2026-10-01-2026-10-31"
 */
public record ReportDocument(
    String title, List<String> lines, List<Table> tables, String fileName) {

  /**
   * @param title shown above the table (null for a report's only table)
   * @param totals a last, bold row; null for none
   */
  public record Table(
      String title, List<Column> columns, List<List<String>> rows, List<String> totals) {}

  /**
   * @param numeric right-aligned (amounts, counts)
   * @param width relative width in the PDF
   */
  public record Column(String header, boolean numeric, float width) {

    public static Column text(String header, float width) {
      return new Column(header, false, width);
    }

    public static Column number(String header) {
      return new Column(header, true, 1.5f);
    }
  }
}
