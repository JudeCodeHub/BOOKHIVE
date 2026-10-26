package lk.bookhive.hold.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.service.HoldListService.HoldRow;
import lk.bookhive.hold.service.HoldListService.ShelfRow;
import lk.bookhive.hold.service.HoldService.HoldProblem;
import lk.bookhive.hold.service.HoldService.HoldRefusal;
import lk.bookhive.hold.service.HoldService.HoldRefusedException;
import org.springframework.http.ProblemDetail;

/** What the member and staff hold endpoints return. */
public final class HoldResponses {

  private HoldResponses() {}

  /**
   * A hold.
   *
   * @param position 1 = next to get a copy; null when READY (a copy is waiting)
   * @param queueLength members waiting for the title
   * @param expiresAt READY: the last day to pick it up
   * @param copyBarcode READY: the copy set aside
   * @param placedByStaff placed at the desk for the member
   */
  public record HoldSummary(
      UUID holdId,
      UUID bookId,
      String title,
      String coverVersion,
      HoldStatus status,
      Instant placedAt,
      Integer position,
      long queueLength,
      Instant readyAt,
      LocalDate expiresAt,
      String copyBarcode,
      boolean placedByStaff) {

    static HoldSummary from(HoldRow row) {
      Hold hold = row.view().hold();
      return new HoldSummary(
          hold.getId(),
          hold.getBookId(),
          row.title() == null ? null : row.title().title(),
          row.title() == null ? null : row.title().coverVersion(),
          hold.getStatus(),
          hold.getPlacedAt(),
          row.view().position(),
          row.view().queueLength(),
          hold.getReadyAt(),
          hold.getExpiresAt(),
          row.copy() == null ? null : row.copy().barcode(),
          hold.getPlacedBy() != null);
    }
  }

  public record HoldProblemSummary(HoldRefusal reason, String message) {

    static List<HoldProblemSummary> from(List<HoldProblem> problems) {
      return problems.stream().map(p -> new HoldProblemSummary(p.reason(), p.message())).toList();
    }
  }

  /**
   * Can a hold be placed now, and if not, why ("Reserve" button, step 261).
   *
   * @param queueLength members already waiting for the title
   */
  public record HoldCheckResponse(
      boolean canPlace, long queueLength, List<HoldProblemSummary> problems) {}

  /**
   * One copy on the hold shelf.
   *
   * @param pastDeadline the last pickup day has passed; tonight's job expires it
   */
  public record HoldShelfSummary(
      UUID holdId,
      UUID bookId,
      String title,
      String copyBarcode,
      String callNumber,
      UUID memberId,
      String memberNo,
      String fullName,
      String phone,
      Instant readyAt,
      LocalDate expiresAt,
      boolean pastDeadline) {

    static HoldShelfSummary from(ShelfRow row) {
      Hold hold = row.hold();
      return new HoldShelfSummary(
          hold.getId(),
          hold.getBookId(),
          row.copy() == null ? null : row.copy().title(),
          row.copy() == null ? null : row.copy().barcode(),
          row.copy() == null ? null : row.copy().callNumber(),
          hold.getMemberId(),
          row.borrower() == null ? null : row.borrower().memberNo(),
          row.fullName(),
          row.phone(),
          hold.getReadyAt(),
          hold.getExpiresAt(),
          row.pastDeadline());
    }
  }

  /** 422 HOLD_REFUSED with every reason in {@code hold}. */
  static ProblemDetail refused(HoldRefusedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setProperty("code", ex.getCode());
    problem.setProperty(
        "hold",
        new HoldCheckResponse(
            false, ex.getQueueLength(), HoldProblemSummary.from(ex.getProblems())));
    return problem;
  }
}
