package lk.bookhive.report.export;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lk.bookhive.common.text.SpreadsheetCells;
import lk.bookhive.report.export.ReportDocument.Column;
import lk.bookhive.report.export.ReportDocument.Table;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

/**
 * A report as CSV: UTF-8 with a byte order mark, so Excel shows Sinhala and Tamil. One table is
 * plain rows; with more, each starts with its title on a line of its own and a blank line between.
 * Text cells are made safe to open in a spreadsheet (no formulas).
 */
@Component
public class CsvExporter {

  public byte[] write(ReportDocument report) {
    StringWriter out = new StringWriter().append('﻿');
    try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT)) {
      boolean several = report.tables().size() > 1;
      for (int i = 0; i < report.tables().size(); i++) {
        Table table = report.tables().get(i);
        if (i > 0) {
          csv.println();
        }
        if (several && table.title() != null) {
          csv.printRecord(SpreadsheetCells.safe(table.title()));
        }
        csv.printRecord(table.columns().stream().map(Column::header).toList());
        for (List<String> row : table.rows()) {
          csv.printRecord(cells(table, row));
        }
        if (table.totals() != null) {
          csv.printRecord(cells(table, table.totals()));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Numbers stay numbers (a leading minus is fine); text is protected against formulas. */
  private static List<String> cells(Table table, List<String> row) {
    return java.util.stream.IntStream.range(0, row.size())
        .mapToObj(
            i ->
                table.columns().get(i).numeric()
                    ? nullToEmpty(row.get(i))
                    : SpreadsheetCells.safe(row.get(i)))
        .toList();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
