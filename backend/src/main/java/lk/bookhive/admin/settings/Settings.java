package lk.bookhive.admin.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * The typed setting groups (checklist step 215). Each record is one row of library_settings; field
 * names are the JSON keys. Other modules read them through SettingsService.
 */
public final class Settings {

  private Settings() {}

  /** Implemented by every group: its row key and a check of its values. */
  public sealed interface Group permits Loans, Fines, Holds, Membership, Notifications {
    String key();

    /** Throws IllegalArgumentException with the field name if a value makes no sense. */
    void check();
  }

  /**
   * Loan rules for everyone (per-membership-type limits are on the membership types).
   *
   * @param maxItemsPerCheckout most items lent in one checkout at the desk
   */
  @Schema(name = "LoanSettings")
  public record Loans(@JsonProperty(required = true) int maxItemsPerCheckout) implements Group {

    /** The highest value an admin can choose (and the size limit of a checkout request). */
    public static final int MOST_PER_CHECKOUT = 50;

    @Override
    public String key() {
      return "loans";
    }

    @Override
    public void check() {
      between("maxItemsPerCheckout", maxItemsPerCheckout, 1, MOST_PER_CHECKOUT);
    }
  }

  /**
   * Amounts in LKR (PRD §4.4).
   *
   * @param graceDays days after the due date before fines start
   * @param blockBorrowingAt unpaid balance from which borrowing is blocked
   * @param librarianWaiveLimit largest waiver a librarian may give (admins: any amount)
   */
  @Schema(name = "FineSettings")
  public record Fines(
      @JsonProperty(required = true) BigDecimal perDay,
      @JsonProperty(required = true) int graceDays,
      @JsonProperty(required = true) BigDecimal maxPerItem,
      @JsonProperty(required = true) BigDecimal lostDefaultPrice,
      @JsonProperty(required = true) BigDecimal lostProcessingFee,
      @JsonProperty(required = true) BigDecimal blockBorrowingAt,
      @JsonProperty(required = true) BigDecimal librarianWaiveLimit)
      implements Group {

    @Override
    public String key() {
      return "fines";
    }

    @Override
    public void check() {
      atLeastZero("perDay", perDay);
      atLeastZero("maxPerItem", maxPerItem);
      atLeastZero("lostDefaultPrice", lostDefaultPrice);
      atLeastZero("lostProcessingFee", lostProcessingFee);
      atLeastZero("blockBorrowingAt", blockBorrowingAt);
      atLeastZero("librarianWaiveLimit", librarianWaiveLimit);
      between("graceDays", graceDays, 0, 30);
    }
  }

  /**
   * @param pickupOpenDays open days a reserved book waits on the hold shelf (PRD §4.3)
   */
  @Schema(name = "HoldSettings")
  public record Holds(@JsonProperty(required = true) int pickupOpenDays) implements Group {

    @Override
    public String key() {
      return "holds";
    }

    @Override
    public void check() {
      between("pickupOpenDays", pickupOpenDays, 1, 30);
    }
  }

  /**
   * @param renewalWindowDays renewing opens this many days before expiry
   * @param documentRetentionDays ID scans are deleted this long after the decision (PDPA)
   * @param historyAnonymiseDays returned loans are anonymised after this long when the member has
   *     switched reading history off (PRD §4.5)
   */
  @Schema(name = "MembershipSettings")
  public record Membership(
      @JsonProperty(required = true) int validityYears,
      @JsonProperty(required = true) int renewalWindowDays,
      @JsonProperty(required = true) int documentRetentionDays,
      @JsonProperty(required = true) int historyAnonymiseDays)
      implements Group {

    @Override
    public String key() {
      return "membership";
    }

    @Override
    public void check() {
      between("validityYears", validityYears, 1, 10);
      between("renewalWindowDays", renewalWindowDays, 0, 365);
      between("documentRetentionDays", documentRetentionDays, 1, 3650);
      between("historyAnonymiseDays", historyAnonymiseDays, 0, 365);
    }
  }

  /** When reminder emails go out (NOTIF-4). */
  @Schema(name = "ReminderSettings")
  public record Notifications(
      @JsonProperty(required = true) int dueSoonDaysBefore,
      @JsonProperty(required = true) int holdExpiringDaysBefore,
      @JsonProperty(required = true) int membershipExpiringDaysBefore)
      implements Group {

    @Override
    public String key() {
      return "notifications";
    }

    @Override
    public void check() {
      between("dueSoonDaysBefore", dueSoonDaysBefore, 0, 30);
      between("holdExpiringDaysBefore", holdExpiringDaysBefore, 0, 30);
      between("membershipExpiringDaysBefore", membershipExpiringDaysBefore, 0, 365);
    }
  }

  private static void atLeastZero(String field, BigDecimal value) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException(field);
    }
  }

  private static void between(String field, int value, int min, int max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field);
    }
  }
}
