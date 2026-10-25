package lk.bookhive.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.domain.Money;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Adult, Junior, Senior, Student... (table {@code membership_types}, PRD §4.1). The borrowing rules
 * per type: how many items, for how long, how many renewals and holds, and the fee.
 */
@Entity
@Table(name = "membership_types")
public class MembershipType extends BaseEntity {

  @Column(nullable = false, length = 30, unique = true)
  private String code;

  @Column(name = "name_en", nullable = false, length = 60)
  private String nameEn;

  @Column(name = "name_si", nullable = false, length = 60)
  private String nameSi;

  @Column(name = "name_ta", nullable = false, length = 60)
  private String nameTa;

  private Integer minAge;

  private Integer maxAge;

  @Column(nullable = false)
  private int maxItems;

  @Column(nullable = false)
  private int loanDays;

  @Column(nullable = false)
  private int maxRenewals;

  @Column(nullable = false)
  private int maxHolds;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal annualFee;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RequiredDocument requiredDocument;

  @Column(nullable = false)
  private boolean requiresGuardian;

  // NULL = every section
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "text[]")
  private List<String> allowedSections;

  @Column(nullable = false)
  private boolean active;

  protected MembershipType() {
    // for JPA
  }

  /**
   * The editable part of a type (admins, checklist step 145). The code is fixed once created,
   * because code and emails refer to it.
   */
  public record Rules(
      String nameEn,
      String nameSi,
      String nameTa,
      Integer minAge,
      Integer maxAge,
      int maxItems,
      int loanDays,
      int maxRenewals,
      int maxHolds,
      Money annualFee,
      RequiredDocument requiredDocument,
      boolean requiresGuardian,
      List<String> allowedSections,
      boolean active) {}

  public static MembershipType create(String code, Rules rules) {
    MembershipType type = new MembershipType();
    type.code = code;
    type.update(rules);
    return type;
  }

  /** Applies to new loans from now on; existing loans keep the due date they were given. */
  public void update(Rules rules) {
    this.nameEn = rules.nameEn();
    this.nameSi = rules.nameSi();
    this.nameTa = rules.nameTa();
    this.minAge = rules.minAge();
    this.maxAge = rules.maxAge();
    this.maxItems = rules.maxItems();
    this.loanDays = rules.loanDays();
    this.maxRenewals = rules.maxRenewals();
    this.maxHolds = rules.maxHolds();
    this.annualFee = rules.annualFee().amount();
    this.requiredDocument = rules.requiredDocument();
    this.requiresGuardian = rules.requiresGuardian();
    this.allowedSections =
        rules.allowedSections() == null || rules.allowedSections().isEmpty()
            ? null
            : List.copyOf(rules.allowedSections());
    this.active = rules.active();
  }

  public String getCode() {
    return code;
  }

  /** The name in the user's language. */
  public String getName(Language language) {
    return switch (language) {
      case EN -> nameEn;
      case SI -> nameSi;
      case TA -> nameTa;
    };
  }

  /** True if someone of this age (on the application date) may have this type. */
  public boolean acceptsAge(int age) {
    return (minAge == null || age >= minAge) && (maxAge == null || age <= maxAge);
  }

  /** True if members of this type may borrow from the given library section. */
  public boolean allowsSection(String section) {
    return allowedSections == null || allowedSections.contains(section);
  }

  public Integer getMinAge() {
    return minAge;
  }

  public Integer getMaxAge() {
    return maxAge;
  }

  public int getMaxItems() {
    return maxItems;
  }

  public int getLoanDays() {
    return loanDays;
  }

  public int getMaxRenewals() {
    return maxRenewals;
  }

  public int getMaxHolds() {
    return maxHolds;
  }

  public Money getAnnualFee() {
    return new Money(annualFee);
  }

  public RequiredDocument getRequiredDocument() {
    return requiredDocument;
  }

  public boolean isRequiresGuardian() {
    return requiresGuardian;
  }

  public List<String> getAllowedSections() {
    return allowedSections == null ? null : List.copyOf(allowedSections);
  }

  public boolean isActive() {
    return active;
  }
}
