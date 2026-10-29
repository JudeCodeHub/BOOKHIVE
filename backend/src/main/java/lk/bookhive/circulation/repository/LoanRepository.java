package lk.bookhive.circulation.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.domain.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

  /** Statuses of a loan that is still out. */
  List<LoanStatus> OPEN = List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);

  /** The open loan of a copy, if it's out (returns at the desk: scan the copy). */
  Optional<Loan> findFirstByCopyIdAndStatusIn(UUID copyId, Collection<LoanStatus> statuses);

  /** A member's open loans, soonest due first ("My loans", the desk summary). */
  List<Loan> findByMemberIdAndStatusInOrderByDueDate(
      UUID memberId, Collection<LoanStatus> statuses);

  /** For the loan limit: how many items the member has out now. */
  long countByMemberIdAndStatusIn(UUID memberId, Collection<LoanStatus> statuses);

  /** Statuses a member still has to deal with: out, or lost and charged. */
  List<LoanStatus> CURRENT = List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

  /** Reading history: a member's loans in one status (RETURNED), newest first. */
  Page<Loan> findByMemberIdAndStatusOrderByCheckedOutAtDesc(
      UUID memberId, LoanStatus status, Pageable pageable);

  /** A copy's loans, newest first (staff). */
  Page<Loan> findByCopyIdOrderByCheckedOutAtDesc(UUID copyId, Pageable pageable);

  /** Nightly job: open loans whose due date is before today and not yet marked overdue. */
  @Query(
      "SELECT l FROM Loan l WHERE l.status = lk.bookhive.circulation.domain.LoanStatus.ACTIVE"
          + " AND l.dueDate < :today")
  List<Loan> findBecomingOverdue(@Param("today") LocalDate today);

  /** Does the member have a copy of this book out right now? (Hold rules: no hold on it then.) */
  @Query(
      value =
          """
          SELECT EXISTS (SELECT 1 FROM loans l JOIN copies c ON c.id = l.copy_id
                         WHERE l.member_id = :memberId AND c.book_id = :bookId
                           AND l.status IN ('ACTIVE', 'OVERDUE'))
          """,
      nativeQuery = true)
  boolean memberHasBookOut(@Param("memberId") UUID memberId, @Param("bookId") UUID bookId);

  /** Reminder jobs: open loans due on exactly this date. */
  List<Loan> findByStatusInAndDueDate(Collection<LoanStatus> statuses, LocalDate dueDate);

  /** Has the member ever borrowed a copy of this book (out now or returned)? Reviews, step 337. */
  @Query(
      value =
          """
          SELECT EXISTS (SELECT 1 FROM loans l JOIN copies c ON c.id = l.copy_id
                         WHERE l.member_id = :memberId AND c.book_id = :bookId)
          """,
      nativeQuery = true)
  boolean memberEverBorrowed(@Param("memberId") UUID memberId, @Param("bookId") UUID bookId);

  /** One "readers also borrowed" suggestion: a book and how many different readers. */
  interface CoBorrow {
    UUID getBookId();

    long getReaders();
  }

  /**
   * Books borrowed by the readers of {@code bookId} (step 339), ranked by how many different
   * readers borrowed both. Only books shared by at least {@code minReaders} readers, so no single
   * person's reading shows through; members with reading history switched off are left out.
   */
  @Query(
      value =
          """
          WITH readers AS (
              SELECT DISTINCT l.member_id
              FROM loans l
              JOIN copies c ON c.id = l.copy_id
              JOIN members m ON m.id = l.member_id
              WHERE c.book_id = :bookId AND m.reading_history_enabled
          )
          SELECT c.book_id AS bookId, count(DISTINCT l.member_id) AS readers
          FROM loans l
          JOIN copies c ON c.id = l.copy_id
          JOIN readers r ON r.member_id = l.member_id
          WHERE c.book_id <> :bookId
          GROUP BY c.book_id
          HAVING count(DISTINCT l.member_id) >= :minReaders
          ORDER BY readers DESC, c.book_id
          LIMIT :limit
          """,
      nativeQuery = true)
  List<CoBorrow> alsoBorrowed(
      @Param("bookId") UUID bookId, @Param("minReaders") int minReaders, @Param("limit") int limit);

  /** Loans still to come back from {@code from} on (due dates to move off new closed days). */
  List<Loan> findByStatusAndDueDateGreaterThanEqual(LoanStatus status, LocalDate from);

  /** The desk's day on the staff dashboard (step 360). */
  interface DeskCounts {
    long getCheckedOut();

    long getReturned();

    long getDueToday();

    long getOverdue();
  }

  @Query(
      value =
          """
          SELECT
              (SELECT count(*) FROM loans
               WHERE checked_out_at >= :start AND checked_out_at < :end) AS checkedOut,
              (SELECT count(*) FROM loans
               WHERE returned_at >= :start AND returned_at < :end) AS returned,
              (SELECT count(*) FROM loans
               WHERE status IN ('ACTIVE', 'OVERDUE') AND due_date = :today) AS dueToday,
              (SELECT count(*) FROM loans
               WHERE status IN ('ACTIVE', 'OVERDUE') AND due_date < :today) AS overdue
          """,
      nativeQuery = true)
  DeskCounts deskCounts(
      @Param("start") Instant start, @Param("end") Instant end, @Param("today") LocalDate today);

  /** Loans and returns per library day (staff dashboard chart); days without any are 0. */
  interface DayActivity {
    String getDay();

    long getCheckouts();

    long getReturns();
  }

  @Query(
      value =
          """
          WITH days AS (
              SELECT generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day')::date
                  AS day
          )
          SELECT to_char(days.day, 'YYYY-MM-DD') AS day,
                 COALESCE(c.n, 0) AS checkouts,
                 COALESCE(r.n, 0) AS returns
          FROM days
          LEFT JOIN (SELECT (checked_out_at AT TIME ZONE :zone)::date AS day, count(*) AS n
                     FROM loans WHERE checked_out_at >= :start GROUP BY 1) c ON c.day = days.day
          LEFT JOIN (SELECT (returned_at AT TIME ZONE :zone)::date AS day, count(*) AS n
                     FROM loans WHERE returned_at >= :start GROUP BY 1) r ON r.day = days.day
          ORDER BY days.day
          """,
      nativeQuery = true)
  List<DayActivity> dailyActivity(
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("start") Instant start,
      @Param("zone") String zone);

  /** Fine accrual job: every open loan past its due date. */
  List<Loan> findByStatusInAndDueDateBefore(Collection<LoanStatus> statuses, LocalDate today);

  /** Staff list: open loans, soonest due first. */
  Page<Loan> findByStatusInOrderByDueDate(Collection<LoanStatus> statuses, Pageable pageable);

  /**
   * Staff list: open loans due before {@code today}, longest overdue first. By date, not by the
   * OVERDUE status, so it's right even before the nightly job has run.
   */
  Page<Loan> findByStatusInAndDueDateBeforeOrderByDueDate(
      Collection<LoanStatus> statuses, LocalDate today, Pageable pageable);

  /** Staff list "due soon": open loans due between two dates (inclusive), soonest first. */
  Page<Loan> findByStatusInAndDueDateBetweenOrderByDueDate(
      Collection<LoanStatus> statuses, LocalDate from, LocalDate to, Pageable pageable);
}
