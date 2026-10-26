package lk.bookhive.devdata;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import lk.bookhive.common.time.LibraryTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sample members and a year of loans for local development (checklist step 248), so the desk, the
 * loans lists, "My loans" and reading history have something real to show:
 *
 * <ul>
 *   <li>12 members (log in as member01@bookhive.local ... member12@bookhive.local with the dev
 *       password below): adults, a senior, a student, one expired, one expiring soon, one with
 *       reading history switched off.
 *   <li>Returned loans over the last 12 months, some returned late; for the member with history
 *       off, old ones are already anonymised (PRD §4.5).
 *   <li>Current loans: on time, due in the next few days, overdue, and one lost.
 * </ul>
 *
 * Plain SQL on purpose: loans in the past can't go through the checkout service (it lends "now"),
 * and dev data must not reach into other modules' repositories. Runs once, when no loans exist yet;
 * the fixed random seed gives the same data on every fresh database.
 */
@Component
class DevLoanSeeder {

  private static final Logger log = LoggerFactory.getLogger(DevLoanSeeder.class);

  /** Same as the dev admin's, so there's one password to remember locally. */
  static final String DEV_PASSWORD = "honey-hive-dev-2026";

  private static final int LOAN_DAYS = 14;

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final PasswordEncoder passwords;
  private final LibraryTime time;
  private final Random random = new Random(7);

  DevLoanSeeder(
      JdbcClient jdbc, TransactionTemplate tx, PasswordEncoder passwords, LibraryTime time) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.passwords = passwords;
    this.time = time;
  }

  /**
   * @param historyLoans returned loans over the past year
   * @param current open loans now: "ok", "soon", "overdue" or "lost" each
   */
  private record SampleMember(
      String name,
      String phone,
      String language,
      String type,
      LocalDate dateOfBirth,
      String city,
      int expiresInDays,
      boolean historyOn,
      int historyLoans,
      List<String> current) {}

  void seed() {
    if (jdbc.sql("SELECT count(*) FROM loans").query(Long.class).single() > 0) {
      log.info("Dev loans: loans already there, nothing to seed");
      return;
    }
    List<UUID> copyIds =
        jdbc.sql(
                "SELECT id FROM copies WHERE status = 'AVAILABLE' AND lending_type = 'LENDABLE'"
                    + " ORDER BY barcode")
            .query(UUID.class)
            .list();
    if (copyIds.size() < 60) {
      log.info("Dev loans: not enough copies to lend ({}), skipped", copyIds.size());
      return;
    }
    tx.executeWithoutResult(status -> seed(copyIds));
  }

  private void seed(List<UUID> copyIds) {
    UUID staffId =
        jdbc.sql("SELECT user_id FROM user_roles WHERE role = 'ADMIN' LIMIT 1")
            .query(UUID.class)
            .optional()
            .orElse(null);
    String hash = passwords.encode(DEV_PASSWORD);
    LocalDate today = time.today();

    List<UUID> copies = new ArrayList<>(copyIds);
    Collections.shuffle(copies, random);
    // The current loans take copies from the end of the list, history from the start
    int nextCurrent = copies.size() - 1;
    Map<UUID, Instant> freeFrom = new HashMap<>();

    int memberNo = 0;
    int history = 0;
    int open = 0;
    for (SampleMember sample : members(today)) {
      memberNo++;
      UUID memberId = createMember(memberNo, sample, hash, staffId, today);

      // Returned loans: spread over the last year, oldest first
      List<Integer> daysAgo = new ArrayList<>();
      for (int i = 0; i < sample.historyLoans(); i++) {
        daysAgo.add(20 + random.nextInt(345));
      }
      daysAgo.sort(Collections.reverseOrder());
      for (int ago : daysAgo) {
        UUID copyId = copies.get(random.nextInt(nextCurrent - 40));
        Instant out = at(today.minusDays(ago), 9 + random.nextInt(8));
        // A copy can't be out twice at once: start after its previous loan came back
        Instant free = freeFrom.getOrDefault(copyId, Instant.EPOCH);
        if (out.isBefore(free)) {
          out = free.plusSeconds(86_400);
        }
        LocalDate due = date(out).plusDays(LOAN_DAYS);
        // Most come back on time; about one in five is a few days late
        int lateDays = random.nextInt(5) == 0 ? 1 + random.nextInt(9) : -random.nextInt(10);
        LocalDate back = due.plusDays(lateDays);
        if (!back.isBefore(today)) {
          continue;
        }
        Instant returned = at(back, 10 + random.nextInt(7));
        freeFrom.put(copyId, returned);
        // History off: loans returned longer ago than the library keeps them lose their name
        boolean anonymised = !sample.historyOn() && back.isBefore(today.minusDays(30));
        insertLoan(
            copyId,
            anonymised ? null : memberId,
            out,
            due,
            random.nextInt(6) == 0 ? 1 : 0,
            "RETURNED",
            returned,
            staffId);
        history++;
      }

      for (String kind : sample.current()) {
        UUID copyId = copies.get(nextCurrent--);
        int outDaysAgo =
            switch (kind) {
              case "soon" -> LOAN_DAYS - 1 - random.nextInt(3);
              case "overdue", "lost" -> LOAN_DAYS + 2 + random.nextInt(20);
              default -> 1 + random.nextInt(8);
            };
        Instant out = at(today.minusDays(outDaysAgo), 9 + random.nextInt(8));
        LocalDate due = date(out).plusDays(LOAN_DAYS);
        String loanStatus =
            switch (kind) {
              case "overdue" -> "OVERDUE";
              case "lost" -> "LOST";
              default -> "ACTIVE";
            };
        insertLoan(copyId, memberId, out, due, 0, loanStatus, null, staffId);
        String copyStatus = kind.equals("lost") ? "LOST" : "ON_LOAN";
        moveCopy(copyId, copyStatus, staffId);
        open++;
      }
    }
    log.info(
        "Dev loans: seeded {} members (password {}), {} returned and {} current loans",
        memberNo,
        DEV_PASSWORD,
        history,
        open);
  }

  private UUID createMember(
      int n, SampleMember sample, String hash, UUID staffId, LocalDate today) {
    String email = "member%02d@bookhive.local".formatted(n);
    Timestamp joined = Timestamp.from(at(today.minusDays(330 + random.nextInt(300)), 11));
    UUID userId =
        jdbc.sql(
                """
                INSERT INTO users (email, password_hash, full_name, phone, preferred_language,
                                   status, email_verified_at, created_at)
                VALUES (:email, :hash, :name, :phone, :language, 'ACTIVE', :joined, :joined)
                RETURNING id
                """)
            .param("email", email)
            .param("hash", hash)
            .param("name", sample.name())
            .param("phone", sample.phone())
            .param("language", sample.language())
            .param("joined", joined)
            .query(UUID.class)
            .single();
    jdbc.sql("INSERT INTO user_roles (user_id, role) VALUES (:id, 'MEMBER')")
        .param("id", userId)
        .update();

    LocalDate expires = today.plusDays(sample.expiresInDays());
    return jdbc.sql(
            """
            INSERT INTO members (user_id, member_no, membership_type_id, status, date_of_birth,
                                 address_line, city, applied_at, approved_by, approved_at,
                                 expires_at, reading_history_enabled)
            VALUES (:userId, 'BH-' || lpad(nextval('member_no_seq')::text, 6, '0'),
                    (SELECT id FROM membership_types WHERE code = :type), :status, :dob,
                    :address, :city, :joined, :staffId, :joined, :expires, :historyOn)
            RETURNING id
            """)
        .param("userId", userId)
        .param("type", sample.type())
        .param("status", expires.isBefore(today) ? "EXPIRED" : "ACTIVE")
        .param("dob", sample.dateOfBirth())
        .param("address", (10 + random.nextInt(190)) + ", Temple Road")
        .param("city", sample.city())
        .param("joined", joined)
        .param("staffId", staffId)
        .param("expires", expires)
        .param("historyOn", sample.historyOn())
        .query(UUID.class)
        .single();
  }

  private void insertLoan(
      UUID copyId,
      UUID memberId,
      Instant out,
      LocalDate due,
      int renewCount,
      String status,
      Instant returned,
      UUID staffId) {
    jdbc.sql(
            """
            INSERT INTO loans (copy_id, member_id, checked_out_at, due_date, renew_count, status,
                               returned_at, checked_out_by, returned_by, created_at)
            VALUES (:copyId, :memberId, :out, :due, :renewCount, :status, :returned, :staffId,
                    :returnedBy, :out)
            """)
        .param("copyId", copyId)
        .param("memberId", memberId)
        .param("out", Timestamp.from(out))
        .param("due", due)
        .param("renewCount", renewCount)
        .param("status", status)
        .param("returned", returned == null ? null : Timestamp.from(returned))
        .param("staffId", staffId)
        .param("returnedBy", returned == null ? null : staffId)
        .update();
  }

  /** The copy goes out (or is lost), with its history line like every status change. */
  private void moveCopy(UUID copyId, String status, UUID staffId) {
    jdbc.sql(
            """
            INSERT INTO copy_status_history (copy_id, from_status, to_status, reason, changed_by)
            VALUES (:copyId, 'AVAILABLE', :status, 'Sample loan (dev data)', :staffId)
            """)
        .param("copyId", copyId)
        .param("status", status)
        .param("staffId", staffId)
        .update();
    jdbc.sql(
            "UPDATE copies SET status = :status, updated_at = now(), version = version + 1"
                + " WHERE id = :copyId")
        .param("status", status)
        .param("copyId", copyId)
        .update();
  }

  private static Instant at(LocalDate date, int hour) {
    return ZonedDateTime.of(date, LocalTime.of(hour, 0), LibraryTime.ZONE).toInstant();
  }

  private static LocalDate date(Instant instant) {
    return LocalDate.ofInstant(instant, LibraryTime.ZONE);
  }

  private static List<SampleMember> members(LocalDate today) {
    return List.of(
        new SampleMember(
            "Nimali Perera",
            "0771234501",
            "si",
            "ADULT",
            LocalDate.of(1990, 4, 12),
            "Colombo",
            200,
            true,
            14,
            List.of("ok", "soon")),
        new SampleMember(
            "Kavindu Jayasinghe",
            "0712345602",
            "si",
            "STUDENT",
            LocalDate.of(2005, 9, 3),
            "Kandy",
            150,
            true,
            10,
            List.of("ok", "ok", "overdue")),
        new SampleMember(
            "Tharshini Raveendran",
            "0763456703",
            "ta",
            "ADULT",
            LocalDate.of(1987, 1, 22),
            "Jaffna",
            240,
            true,
            12,
            List.of("soon")),
        new SampleMember(
            "Mohamed Rizwan",
            "0754567804",
            "en",
            "ADULT",
            LocalDate.of(1979, 11, 30),
            "Kalmunai",
            90,
            true,
            6,
            List.of("overdue")),
        new SampleMember(
            "Sunil Wickramasinghe",
            "0725678905",
            "si",
            "SENIOR",
            LocalDate.of(1955, 6, 8),
            "Galle",
            300,
            true,
            15,
            List.of("ok", "ok")),
        new SampleMember(
            "Anjali Fernando",
            "0776789006",
            "en",
            "ADULT",
            LocalDate.of(1995, 2, 14),
            "Negombo",
            18,
            true,
            8,
            List.of("ok")),
        new SampleMember(
            "Priya Shanmugam",
            "0717890107",
            "ta",
            "STUDENT",
            LocalDate.of(2004, 12, 1),
            "Batticaloa",
            120,
            true,
            9,
            List.of("lost")),
        new SampleMember(
            "Dinesh Bandara",
            "0768901208",
            "si",
            "ADULT",
            LocalDate.of(1983, 7, 19),
            "Kurunegala",
            60,
            false,
            10,
            List.of("ok")),
        new SampleMember(
            "Fathima Nazeer",
            "0759012309",
            "en",
            "ADULT",
            LocalDate.of(1992, 3, 27),
            "Puttalam",
            270,
            true,
            5,
            List.of()),
        new SampleMember(
            "Ravi Kumar",
            "0720123410",
            "ta",
            "ADULT",
            LocalDate.of(1975, 10, 5),
            "Trincomalee",
            -20,
            true,
            7,
            List.of()),
        new SampleMember(
            "Ishara Gunasekara",
            "0771122311",
            "si",
            "ADULT",
            LocalDate.of(1998, 8, 16),
            "Matara",
            180,
            true,
            4,
            List.of("soon", "ok")),
        new SampleMember(
            "Hasini Silva",
            "0712233412",
            "en",
            "SENIOR",
            LocalDate.of(1958, 5, 25),
            "Ratnapura",
            210,
            true,
            11,
            List.of()));
  }
}
