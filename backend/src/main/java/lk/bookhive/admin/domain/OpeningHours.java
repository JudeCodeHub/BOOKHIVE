package lk.bookhive.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** The library's hours on one weekday (table {@code opening_hours}). No times = closed. */
@Entity
@Table(name = "opening_hours")
@EntityListeners(AuditingEntityListener.class)
public class OpeningHours {

  /** ISO weekday: 1 = Monday ... 7 = Sunday */
  @Id private short dayOfWeek;

  private LocalTime opensAt;

  private LocalTime closesAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected OpeningHours() {
    // for JPA
  }

  public DayOfWeek getDay() {
    return DayOfWeek.of(dayOfWeek);
  }

  public LocalTime getOpensAt() {
    return opensAt;
  }

  public LocalTime getClosesAt() {
    return closesAt;
  }

  public boolean isClosed() {
    return opensAt == null;
  }

  /** Open at this time of day? (Opening time inclusive, closing time exclusive.) */
  public boolean isOpenAt(LocalTime time) {
    return !isClosed() && !time.isBefore(opensAt) && time.isBefore(closesAt);
  }
}
