package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Language;

/**
 * A node in the category tree (table {@code categories}), based on the Dewey Decimal
 * Classification: 800 Literature > 891.48 Sinhala literature. Names in all three languages.
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Category parent;

  @Column(name = "name_en", nullable = false, length = 120)
  private String nameEn;

  @Column(name = "name_si", nullable = false, length = 120)
  private String nameSi;

  @Column(name = "name_ta", nullable = false, length = 120)
  private String nameTa;

  @Column(length = 20)
  private String ddcCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LibrarySection section;

  protected Category() {
    // for JPA
  }

  public Category(
      Category parent,
      String nameEn,
      String nameSi,
      String nameTa,
      String ddcCode,
      LibrarySection section) {
    this.parent = parent;
    rename(nameEn, nameSi, nameTa, ddcCode, section);
  }

  public void rename(
      String nameEn, String nameSi, String nameTa, String ddcCode, LibrarySection section) {
    this.nameEn = nameEn.strip();
    this.nameSi = nameSi.strip();
    this.nameTa = nameTa.strip();
    this.ddcCode = ddcCode == null || ddcCode.isBlank() ? null : ddcCode.strip();
    this.section = section;
  }

  /**
   * Moves the category under another one. The caller (CategoryService) must make sure the new
   * parent isn't this category or one of its descendants, which would create a loop.
   */
  public void moveTo(Category newParent) {
    this.parent = newParent;
  }

  public String getName(Language language) {
    return switch (language) {
      case EN -> nameEn;
      case SI -> nameSi;
      case TA -> nameTa;
    };
  }

  public Category getParent() {
    return parent;
  }

  public String getDdcCode() {
    return ddcCode;
  }

  public LibrarySection getSection() {
    return section;
  }
}
