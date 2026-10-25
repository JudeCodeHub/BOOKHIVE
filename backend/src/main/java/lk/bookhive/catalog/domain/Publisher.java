package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.bookhive.common.domain.BaseEntity;

/** A publisher (table {@code publishers}); names are unique, ignoring case. */
@Entity
@Table(name = "publishers")
public class Publisher extends BaseEntity {

  @Column(nullable = false, columnDefinition = "citext")
  private String name;

  protected Publisher() {
    // for JPA
  }

  public Publisher(String name) {
    rename(name);
  }

  public void rename(String name) {
    this.name = name.strip();
  }

  public String getName() {
    return name;
  }
}
