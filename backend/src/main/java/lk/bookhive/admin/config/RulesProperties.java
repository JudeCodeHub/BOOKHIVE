package lk.bookhive.admin.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.rules.*}: the library-wide borrowing rules (PRD §4), defaults from the PRD. The
 * public rules page reads these, so it always says what the system enforces. They move into the
 * admin-editable settings table later (library_settings, checklist step 260); the page stays.
 *
 * @param fineGraceDays days after the due date before fines start
 * @param fineMaxPerItem fines on one item never go above this
 * @param blockBorrowingAt unpaid balance from which borrowing is blocked
 * @param holdPickupOpenDays open days a reserved book waits on the hold shelf
 * @param documentRetentionDays ID scans are deleted this many days after approval (PDPA)
 */
@ConfigurationProperties("bookhive.rules")
public record RulesProperties(
    BigDecimal finePerDay,
    int fineGraceDays,
    BigDecimal fineMaxPerItem,
    BigDecimal lostDefaultPrice,
    BigDecimal lostProcessingFee,
    BigDecimal blockBorrowingAt,
    int holdPickupOpenDays,
    int membershipValidityYears,
    int documentRetentionDays) {}
