package lk.bookhive.admin.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.admin.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

  /** Holidays from a date on, soonest first (e.g. the next few, or a date range). */
  List<Holiday> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);
}
