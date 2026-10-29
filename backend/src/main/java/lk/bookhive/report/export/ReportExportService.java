package lk.bookhive.report.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.text.MessageBundles;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.report.export.ReportDocument.Column;
import lk.bookhive.report.export.ReportDocument.Table;
import lk.bookhive.report.repository.CirculationReportQueries.Period;
import lk.bookhive.report.service.BookUsageReportService;
import lk.bookhive.report.service.CirculationReportService;
import lk.bookhive.report.service.CirculationReportService.GroupBy;
import lk.bookhive.report.service.MembershipReportService;
import lk.bookhive.report.service.OverdueFineReportService;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;

/**
 * The reports as downloadable documents (checklist step 366): the same numbers as the report pages,
 * with headings in English, Sinhala or Tamil. Lists that are paged on screen come whole, up to
 * {@value #MAX_ROWS} rows.
 */
@Service
public class ReportExportService {

  /** Longest list in one download. */
  static final int MAX_ROWS = 5000;

  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final ResourceBundleMessageSource messages = MessageBundles.bundle("messages/reports");
  private final CirculationReportService circulation;
  private final BookUsageReportService usage;
  private final OverdueFineReportService money;
  private final MembershipReportService membership;
  private final LibraryTime time;

  public ReportExportService(
      CirculationReportService circulation,
      BookUsageReportService usage,
      OverdueFineReportService money,
      MembershipReportService membership,
      LibraryTime time) {
    this.circulation = circulation;
    this.usage = usage;
    this.money = money;
    this.membership = membership;
    this.time = time;
  }

  public ReportDocument circulation(LocalDate from, LocalDate to, GroupBy groupBy, Language lang) {
    var r = circulation.report(from, to, groupBy);
    Text t = new Text(lang);
    var totals = r.totals();
    List<String> totalRow =
        List.of(
            t.get("report.total"),
            n(totals.checkouts()),
            n(totals.returns()),
            n(totals.borrowers()));
    Table table;
    if (groupBy == GroupBy.CATEGORY) {
      table =
          new Table(
              null,
              List.of(
                  Column.text(t.get("circulation.col.category"), 4),
                  Column.number(t.get("circulation.col.checkouts")),
                  Column.number(t.get("circulation.col.borrowers"))),
              r.categories().stream()
                  .map(
                      c ->
                          List.of(
                              join(c.ddcCode(), pick(lang, c.nameEn(), c.nameSi(), c.nameTa())),
                              n(c.checkouts()),
                              n(c.borrowers())))
                  .toList(),
              null);
    } else {
      Function<LocalDate, String> label =
          groupBy == GroupBy.MONTH ? MONTH::format : LocalDate::toString;
      table =
          new Table(
              null,
              List.of(
                  Column.text(t.get(groupBy == GroupBy.MONTH ? "report.month" : "report.day"), 2),
                  Column.number(t.get("circulation.col.checkouts")),
                  Column.number(t.get("circulation.col.returns")),
                  Column.number(t.get("circulation.col.borrowers"))),
              r.periods().stream()
                  .map(
                      p ->
                          List.of(
                              label.apply(p.start()),
                              n(p.checkouts()),
                              n(p.returns()),
                              n(p.borrowers())))
                  .toList(),
              totalRow);
    }
    List<String> lines = new ArrayList<>(List.of(t.get("report.range", from, to)));
    if (groupBy == GroupBy.CATEGORY) {
      lines.add(t.get("circulation.uncategorised", totals.uncategorised()));
    }
    lines.add(t.generated());
    return new ReportDocument(
        t.get("circulation.title"),
        lines,
        List.of(table),
        name("circulation-" + groupBy.name().toLowerCase(Locale.ROOT), from, to));
  }

  public ReportDocument mostBorrowed(
      LocalDate from, LocalDate to, UUID categoryId, int limit, Language lang) {
    var rows = usage.mostBorrowed(from, to, categoryId, limit);
    Text t = new Text(lang);
    List<List<String>> body = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      var r = rows.get(i);
      body.add(
          List.of(
              n(i + 1),
              r.title(),
              text(r.authors()),
              text(r.callNumber()),
              n(r.copies()),
              n(r.checkouts()),
              n(r.borrowers())));
    }
    return new ReportDocument(
        t.get("mostBorrowed.title"),
        List.of(t.get("report.range", from, to), t.generated()),
        List.of(
            new Table(
                null,
                List.of(
                    Column.number(t.get("mostBorrowed.col.rank")),
                    Column.text(t.get("mostBorrowed.col.title"), 5),
                    Column.text(t.get("mostBorrowed.col.authors"), 3),
                    Column.text(t.get("mostBorrowed.col.callNumber"), 2),
                    Column.number(t.get("mostBorrowed.col.copies")),
                    Column.number(t.get("mostBorrowed.col.loans")),
                    Column.number(t.get("mostBorrowed.col.readers"))),
                body,
                null)),
        name("most-borrowed", from, to));
  }

  public ReportDocument notBorrowed(LocalDate since, UUID categoryId, Language lang) {
    var page = usage.notBorrowedSince(since, categoryId, 0, MAX_ROWS);
    Text t = new Text(lang);
    return new ReportDocument(
        t.get("notBorrowed.title", page.since()),
        List.of(t.get("notBorrowed.note", page.since()), t.generated()),
        List.of(
            new Table(
                null,
                List.of(
                    Column.text(t.get("mostBorrowed.col.title"), 5),
                    Column.text(t.get("mostBorrowed.col.authors"), 3),
                    Column.text(t.get("mostBorrowed.col.callNumber"), 2),
                    Column.number(t.get("mostBorrowed.col.copies")),
                    Column.text(t.get("notBorrowed.col.inStockSince"), 2),
                    Column.text(t.get("notBorrowed.col.lastBorrowed"), 2)),
                page.books().stream()
                    .map(
                        b ->
                            List.of(
                                b.title(),
                                text(b.authors()),
                                text(b.callNumber()),
                                n(b.copies()),
                                b.inStockSince().toString(),
                                b.lastBorrowedOn() == null
                                    ? t.get("report.never")
                                    : b.lastBorrowedOn().toString()))
                    .toList(),
                null)),
        "bookhive-not-borrowed-since-" + page.since());
  }

  public ReportDocument overdue(int minDays, Language lang) {
    var page = money.overdue(minDays, 0, MAX_ROWS);
    var s = page.summary();
    Text t = new Text(lang);
    return new ReportDocument(
        t.get("overdue.title"),
        List.of(
            t.get("overdue.asOf", page.today()),
            t.get("overdue.buckets", s.upToWeek(), s.upToMonth(), s.overMonth(), m(s.finesOwed())),
            t.generated()),
        List.of(
            new Table(
                null,
                List.of(
                    Column.text(t.get("overdue.col.memberNo"), 2),
                    Column.text(t.get("overdue.col.member"), 3),
                    Column.text(t.get("overdue.col.phone"), 2),
                    Column.text(t.get("overdue.col.email"), 4),
                    Column.text(t.get("overdue.col.book"), 4),
                    Column.text(t.get("overdue.col.barcode"), 2),
                    Column.text(t.get("overdue.col.due"), 2),
                    Column.number(t.get("overdue.col.daysLate")),
                    Column.number(t.get("overdue.col.fineOwed"))),
                page.loans().stream()
                    .map(
                        l ->
                            List.of(
                                l.memberNo(),
                                l.memberName(),
                                text(l.phone()),
                                l.email(),
                                l.title(),
                                l.barcode(),
                                l.dueDate().toString(),
                                n(l.daysOverdue()),
                                m(l.fineOwed())))
                    .toList(),
                List.of(
                    t.get("report.total"),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    n(s.total()),
                    m(s.finesOwed())))),
        "bookhive-overdue-" + page.today());
  }

  public ReportDocument fines(LocalDate from, LocalDate to, Period groupBy, Language lang) {
    var r = money.fines(from, to, groupBy);
    var x = r.totals();
    Text t = new Text(lang);
    Function<LocalDate, String> label =
        groupBy == Period.MONTH ? MONTH::format : LocalDate::toString;
    Table periods =
        new Table(
            null,
            List.of(
                Column.text(t.get(groupBy == Period.MONTH ? "report.month" : "report.day"), 2),
                Column.number(t.get("fines.col.charged")),
                Column.number(t.get("fines.col.cash")),
                Column.number(t.get("fines.col.online")),
                Column.number(t.get("fines.col.refunded")),
                Column.number(t.get("fines.col.waived")),
                Column.number(t.get("fines.col.cancelled"))),
            r.periods().stream()
                .map(
                    p ->
                        List.of(
                            label.apply(p.start()),
                            m(p.charged()),
                            m(p.collectedCash()),
                            m(p.collectedOnline()),
                            m(p.refunded()),
                            m(p.waived()),
                            m(p.cancelled())))
                .toList(),
            List.of(
                t.get("report.total"),
                m(x.charged()),
                m(x.collectedCash()),
                m(x.collectedOnline()),
                m(x.refunded()),
                m(x.waived()),
                m(x.cancelled())));
    Table byType =
        new Table(
            t.get("fines.byType"),
            List.of(
                Column.text(t.get("fines.col.type"), 3),
                Column.number(t.get("fines.col.charged")),
                Column.number(t.get("fines.col.collected")),
                Column.number(t.get("fines.col.waived"))),
            r.byType().stream()
                .map(
                    k ->
                        List.of(
                            t.get("fines.type." + k.type()),
                            m(k.charged()),
                            m(k.collected()),
                            m(k.waived())))
                .toList(),
            null);
    return new ReportDocument(
        t.get("fines.title"),
        List.of(
            t.get("report.range", from, to),
            t.get("fines.collected", m(x.collected())),
            t.generated()),
        List.of(periods, byType),
        name("fines-" + groupBy.name().toLowerCase(Locale.ROOT), from, to));
  }

  public ReportDocument membership(LocalDate from, LocalDate to, Period groupBy, Language lang) {
    var r = membership.growth(from, to, groupBy);
    var x = r.totals();
    Text t = new Text(lang);
    Function<LocalDate, String> label =
        groupBy == Period.MONTH ? MONTH::format : LocalDate::toString;
    Table periods =
        new Table(
            null,
            List.of(
                Column.text(t.get(groupBy == Period.MONTH ? "report.month" : "report.day"), 2),
                Column.number(t.get("membership.col.applications")),
                Column.number(t.get("membership.col.newMembers")),
                Column.number(t.get("membership.col.membersAtEnd"))),
            r.periods().stream()
                .map(
                    p ->
                        List.of(
                            label.apply(p.start()),
                            n(p.applications()),
                            n(p.newMembers()),
                            n(p.membersAtEnd())))
                .toList(),
            List.of(t.get("report.total"), n(x.applications()), n(x.newMembers()), ""));
    Table byType =
        new Table(
            t.get("membership.byType"),
            List.of(
                Column.text(t.get("membership.col.type"), 3),
                Column.number(t.get("membership.col.newMembers")),
                Column.number(t.get("membership.col.activeNow"))),
            r.byType().stream()
                .map(
                    k ->
                        List.of(
                            pick(lang, k.nameEn(), k.nameSi(), k.nameTa()),
                            n(k.newMembers()),
                            n(k.activeNow())))
                .toList(),
            List.of(t.get("report.total"), n(x.newMembers()), n(x.activeNow())));
    return new ReportDocument(
        t.get("membership.title"),
        List.of(
            t.get("report.range", from, to),
            t.get("membership.summary", x.activeNow(), x.pendingNow()),
            t.get("membership.note"),
            t.generated()),
        List.of(periods, byType),
        name("membership-" + groupBy.name().toLowerCase(Locale.ROOT), from, to));
  }

  /** Headings in one language. */
  private final class Text {
    private final Locale locale;

    Text(Language language) {
      this.locale = MessageBundles.locale(language);
    }

    String get(String key, Object... args) {
      // Dates and numbers are passed as text, so they look the same in every language
      Object[] plain = new Object[args.length];
      for (int i = 0; i < args.length; i++) {
        plain[i] = String.valueOf(args[i]);
      }
      return messages.getMessage(key, plain, locale);
    }

    String generated() {
      return get(
          "report.generated", STAMP.format(LocalDateTime.ofInstant(time.now(), LibraryTime.ZONE)));
    }
  }

  private static String name(String report, LocalDate from, LocalDate to) {
    return "bookhive-" + report + "-" + from + "-" + to;
  }

  private static String pick(Language lang, String en, String si, String ta) {
    return switch (lang) {
      case SI -> si;
      case TA -> ta;
      case EN -> en;
    };
  }

  private static String join(String code, String name) {
    return code == null ? name : code + " " + name;
  }

  private static String n(long value) {
    return Long.toString(value);
  }

  /** Amounts with two decimals and no thousands separator, so spreadsheets read them as numbers. */
  private static String m(BigDecimal value) {
    return (value == null ? BigDecimal.ZERO : value)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString();
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
