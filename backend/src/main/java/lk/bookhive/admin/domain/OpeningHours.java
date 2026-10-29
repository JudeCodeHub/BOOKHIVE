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
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** The library's hours on one weekday (table {@code opening_hours}). No times = closed. */
@Entity
@Table(name = "opening_hours")
@EntityListeners(AuditingEntityListener.class)
public class OpeningHours {

  /** ISO weekday: 1 = Monday ... 7 = Sunday */
  @Id private short dayOfWeek;

  // A time of day, not a moment: passed to the database as it is. Without this, the UTC JDBC
  // time zone (application.yml, meant for timestamps) shifted 08:00 to 13:30 when read back.
  @JdbcType(LocalTimeJdbcType.class)
  private LocalTime opensAt;

  @JdbcType(LocalTimeJdbcType.class)
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

  /** New hours for this weekday (admin, step 357); both null = closed all day. */
  public void change(LocalTime opensAt, LocalTime closesAt) {
    this.opensAt = opensAt;
    this.closesAt = closesAt;
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
