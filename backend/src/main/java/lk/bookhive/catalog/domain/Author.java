package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.bookhive.common.domain.BaseEntity;

/** A person who wrote, translated or illustrated books (table {@code authors}). */
@Entity
@Table(name = "authors")
public class Author extends BaseEntity {

  @Column(nullable = false, length = 200)
  private String name;

  @Column(columnDefinition = "text")
  private String bio;

  protected Author() {
    // for JPA
  }

  public Author(String name, String bio) {
    rename(name, bio);
  }

  public void rename(String name, String bio) {
    this.name = name.strip();
    this.bio = bio == null || bio.isBlank() ? null : bio.strip();
  }

  public String getName() {
    return name;
  }

  public String getBio() {
    return bio;
  }
}
