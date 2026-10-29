package lk.bookhive.report.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.report.domain.ScanResult;
import lk.bookhive.report.domain.StocktakeScan;
import lk.bookhive.report.domain.StocktakeSession;
import lk.bookhive.report.domain.StocktakeStatus;
import lk.bookhive.report.repository.StocktakeQueries;
import lk.bookhive.report.repository.StocktakeQueries.CopyLabel;
import lk.bookhive.report.repository.StocktakeQueries.MissingCopy;
import lk.bookhive.report.repository.StocktakeQueries.ScannedCopy;
import lk.bookhive.report.repository.StocktakeScanRepository;
import lk.bookhive.report.repository.StocktakeSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock-take (RPT-6, checklist step 365): start a session for a part of the library, scan every
 * copy on its shelves, then see what's missing (should be there, wasn't scanned) and what needs a
 * look (on the shelf although the system says it's out, lost, or belongs elsewhere). Nothing in the
 * catalog is changed here: staff decide what to do with each missing copy.
 */
@Service
public class StocktakeService {

  /**
   * The answer to one scan, shown straight away at the shelf.
   *
   * @param alreadyScanned this copy (or unknown barcode) was scanned before in this session
   * @param copyStatus the catalog status (e.g. ON_LOAN for NOT_ON_SHELF); null when unknown
   */
  public record ScanOutcome(
      String barcode,
      ScanResult result,
      boolean alreadyScanned,
      UUID copyId,
      String title,
      String shelfLocation,
      String copyStatus,
      Counts counts) {}

  /**
   * Live while scanning; frozen when completed (expected/found/missing).
   *
   * @param expected AVAILABLE copies in scope (what the shelves should hold)
   * @param found expected copies that were scanned
   * @param missing expected copies not scanned (yet)
   * @param needsLook scans that weren't FOUND (wrong shelf, wrong status, unknown)
   */
  public record Counts(long expected, long found, long missing, long scanned, long needsLook) {}

  public record SessionView(StocktakeSession session, Counts counts) {}

  /** A scan that wasn't FOUND, with the book's title and shelf when it's a known copy. */
  public record IssueView(StocktakeScan scan, String title, String shelfLocation) {}

  private final StocktakeSessionRepository sessions;
  private final StocktakeScanRepository scans;
  private final StocktakeQueries queries;
  private final AuditService audit;
  private final LibraryTime time;

  public StocktakeService(
      StocktakeSessionRepository sessions,
      StocktakeScanRepository scans,
      StocktakeQueries queries,
      AuditService audit,
      LibraryTime time) {
    this.sessions = sessions;
    this.scans = scans;
    this.queries = queries;
    this.audit = audit;
    this.time = time;
  }

  @Transactional
  public SessionView start(String name, UUID categoryId, String shelfPrefix, UUID staffId) {
    String prefix = shelfPrefix == null || shelfPrefix.isBlank() ? null : shelfPrefix.strip();
    StocktakeSession session =
        sessions.save(
            StocktakeSession.start(name.strip(), categoryId, prefix, staffId, time.now()));
    audit.record(
        "stocktake.started",
        staffId,
        "stocktake",
        session.getId(),
        Map.of(
            "name", session.getName(),
            "categoryId", String.valueOf(categoryId),
            "shelfPrefix", String.valueOf(prefix)));
    return view(session);
  }

  @Transactional(readOnly = true)
  public Page<SessionView> list(int page, int size) {
    return sessions.findAllByOrderByStartedAtDesc(PageRequest.of(page, size)).map(this::view);
  }

  @Transactional(readOnly = true)
  public SessionView get(UUID id) {
    return view(require(id));
  }

  /** Records one barcode and says straight away what it is. */
  @Transactional
  public ScanOutcome scan(UUID id, String rawBarcode, UUID staffId) {
    StocktakeSession session = require(id);
    session.requireOpen();
    String barcode = rawBarcode.strip().toUpperCase(Locale.ROOT);
    Optional<ScannedCopy> copy =
        queries.findByBarcode(barcode, session.getCategoryId(), session.getShelfPrefix());

    if (copy.isEmpty()) {
      boolean again = scans.existsBySessionIdAndBarcode(id, barcode);
      if (!again) {
        scans.save(
            StocktakeScan.of(id, barcode, null, ScanResult.UNKNOWN, null, staffId, time.now()));
      }
      return outcome(session, barcode, ScanResult.UNKNOWN, again, null, null);
    }

    ScannedCopy c = copy.get();
    // A wrong status matters more than a wrong shelf: it's a desk problem (missed checkout, a
    // "lost" book turning up)
    ScanResult result =
        !"AVAILABLE".equals(c.status())
            ? ScanResult.NOT_ON_SHELF
            : c.inScope() ? ScanResult.FOUND : ScanResult.NOT_IN_SCOPE;
    if (scans.existsBySessionIdAndCopyId(id, c.copyId())) {
      return outcome(session, barcode, result, true, c.copyId(), c.status());
    }
    try {
      scans.saveAndFlush(
          StocktakeScan.of(id, barcode, c.copyId(), result, c.status(), staffId, time.now()));
    } catch (DataIntegrityViolationException e) {
      // Two people scanned the same copy at once: it's in, once
      return outcome(session, barcode, result, true, c.copyId(), c.status());
    }
    return outcome(session, barcode, result, false, c.copyId(), c.status());
  }

  /** Expected copies that weren't scanned, in shelf order. */
  @Transactional(readOnly = true)
  public List<MissingCopy> missing(UUID id, int page, int size) {
    StocktakeSession s = require(id);
    if (s.getStatus() == StocktakeStatus.CANCELLED) {
      return List.of();
    }
    return queries.missing(id, s.getCategoryId(), s.getShelfPrefix(), size, page * size);
  }

  /** Scans that need a look: wrong shelf, wrong status, unknown barcode. Newest first. */
  @Transactional(readOnly = true)
  public List<IssueView> issues(UUID id) {
    require(id);
    List<StocktakeScan> found =
        scans.findBySessionIdAndResultNotOrderByScannedAtDesc(id, ScanResult.FOUND);
    Map<UUID, CopyLabel> labels =
        queries
            .labels(found.stream().map(StocktakeScan::getCopyId).filter(x -> x != null).toList())
            .stream()
            .collect(Collectors.toMap(CopyLabel::copyId, Function.identity()));
    return found.stream()
        .map(
            scan -> {
              CopyLabel label = scan.getCopyId() == null ? null : labels.get(scan.getCopyId());
              return new IssueView(
                  scan,
                  label == null ? null : label.title(),
                  label == null ? null : label.shelfLocation());
            })
        .toList();
  }

  /** Done: the counts are frozen so the result can be read later. */
  @Transactional
  public SessionView complete(UUID id, UUID staffId) {
    StocktakeSession session = require(id);
    Counts counts = liveCounts(session);
    session.complete(
        (int) counts.expected(), (int) counts.found(), (int) counts.missing(), staffId, time.now());
    audit.record(
        "stocktake.completed",
        staffId,
        "stocktake",
        id,
        Map.of(
            "expected", counts.expected(),
            "found", counts.found(),
            "missing", counts.missing()));
    return view(session);
  }

  @Transactional
  public SessionView cancel(UUID id, UUID staffId) {
    StocktakeSession session = require(id);
    session.cancel(staffId, time.now());
    audit.record("stocktake.cancelled", staffId, "stocktake", id, Map.of());
    return view(session);
  }

  private ScanOutcome outcome(
      StocktakeSession session,
      String barcode,
      ScanResult result,
      boolean again,
      UUID copyId,
      String copyStatus) {
    CopyLabel label =
        copyId == null ? null : queries.labels(List.of(copyId)).stream().findFirst().orElse(null);
    return new ScanOutcome(
        barcode,
        result,
        again,
        copyId,
        label == null ? null : label.title(),
        label == null ? null : label.shelfLocation(),
        copyStatus,
        liveCounts(session));
  }

  private SessionView view(StocktakeSession s) {
    if (s.getStatus() == StocktakeStatus.COMPLETED) {
      long scanned = scans.countBySessionId(s.getId());
      return new SessionView(
          s,
          new Counts(
              s.getExpectedCount(),
              s.getFoundCount(),
              s.getMissingCount(),
              scanned,
              scanned - scans.countBySessionIdAndResult(s.getId(), ScanResult.FOUND)));
    }
    return new SessionView(s, liveCounts(s));
  }

  private Counts liveCounts(StocktakeSession s) {
    long expected = queries.countExpected(s.getCategoryId(), s.getShelfPrefix());
    long found = queries.countFound(s.getId(), s.getCategoryId(), s.getShelfPrefix());
    long scanned = scans.countBySessionId(s.getId());
    long needsLook = scanned - scans.countBySessionIdAndResult(s.getId(), ScanResult.FOUND);
    return new Counts(expected, found, expected - found, scanned, needsLook);
  }

  private StocktakeSession require(UUID id) {
    return sessions
        .findById(id)
        .orElseThrow(() -> new NotFoundException("STOCKTAKE_NOT_FOUND", "Stock-take not found."));
  }
}
