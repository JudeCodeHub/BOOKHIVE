package lk.bookhive.admin.repository;

import java.util.List;
import lk.bookhive.admin.domain.OpeningHours;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningHoursRepository extends JpaRepository<OpeningHours, Short> {

  List<OpeningHours> findAllByOrderByDayOfWeek();
}
