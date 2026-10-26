package lk.bookhive.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Language;

/** A day the library is closed (Poya, public holidays): table {@code holidays}. */
@Entity
@Table(name = "holidays")
public class Holiday extends BaseEntity {

  @Column(nullable = false, unique = true)
  private LocalDate date;

  @Column(name = "name_en", nullable = false, length = 120)
  private String nameEn;

  @Column(name = "name_si", nullable = false, length = 120)
  private String nameSi;

  @Column(name = "name_ta", nullable = false, length = 120)
  private String nameTa;

  protected Holiday() {
    // for JPA
  }

  public LocalDate getDate() {
    return date;
  }

  public String getName(Language language) {
    return switch (language) {
      case EN -> nameEn;
      case SI -> nameSi;
      case TA -> nameTa;
    };
  }
}
