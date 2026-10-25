package lk.bookhive.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lk.bookhive.common.domain.BaseEntity;

/**
 * A title in the catalog (table {@code books}, ADR 0003): what's the same for every copy. The
 * physical items are copies. The {@code search_vector} column is computed by PostgreSQL and not
 * mapped here.
 */
@Entity
@Table(name = "books")
public class Book extends BaseEntity {

  @Column(nullable = false, length = 300)
  private String title;

  @Column(length = 300)
  private String subtitle;

  @Column(length = 10)
  private String isbn10;

  @Column(length = 13)
  private String isbn13;

  @Column(length = 50)
  private String edition;

  private Short publishYear;

  private Integer pages;

  @Column(nullable = false, length = 10)
  private String language;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BookFormat format;

  @Column(length = 300)
  private String coverKey;

  @Column(length = 50)
  private String callNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "publisher_id")
  private Publisher publisher;

  // Replaced as a whole when the cataloguer edits the list (orphans are deleted)
  @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position")
  private List<BookAuthor> contributors = new ArrayList<>();

  @ManyToMany
  @JoinTable(
      name = "book_categories",
      joinColumns = @JoinColumn(name = "book_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id"))
  private Set<Category> categories = new HashSet<>();

  protected Book() {
    // for JPA
  }

  /** The descriptive fields a cataloguer fills in (or the ISBN lookup fills for them). */
  public record Details(
      String title,
      String subtitle,
      String isbn10,
      String isbn13,
      String edition,
      Short publishYear,
      Integer pages,
      String language,
      String description,
      BookFormat format,
      String callNumber,
      Publisher publisher) {}

  public Book(Details details) {
    update(details);
  }

  public void update(Details details) {
    this.title = details.title().strip();
    this.subtitle = blankToNull(details.subtitle());
    this.isbn10 = details.isbn10();
    this.isbn13 = details.isbn13();
    this.edition = blankToNull(details.edition());
    this.publishYear = details.publishYear();
    this.pages = details.pages();
    this.language = details.language();
    this.description = blankToNull(details.description());
    this.format = details.format();
    this.callNumber = blankToNull(details.callNumber());
    this.publisher = details.publisher();
  }

  /** A contributor to add, in cover order. */
  public record Contributor(Author author, AuthorRole role) {}

  /** Replaces the contributor list; the list order becomes the order shown. */
  public void setContributors(List<Contributor> list) {
    contributors.clear();
    for (int i = 0; i < list.size(); i++) {
      contributors.add(new BookAuthor(this, list.get(i).author(), list.get(i).role(), i));
    }
  }

  public void setCategories(Collection<Category> newCategories) {
    categories.clear();
    categories.addAll(newCategories);
  }

  public void setCoverKey(String coverKey) {
    this.coverKey = coverKey;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public String getTitle() {
    return title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public String getIsbn10() {
    return isbn10;
  }

  public String getIsbn13() {
    return isbn13;
  }

  public String getEdition() {
    return edition;
  }

  public Short getPublishYear() {
    return publishYear;
  }

  public Integer getPages() {
    return pages;
  }

  public String getLanguage() {
    return language;
  }

  public String getDescription() {
    return description;
  }

  public BookFormat getFormat() {
    return format;
  }

  public String getCoverKey() {
    return coverKey;
  }

  public String getCallNumber() {
    return callNumber;
  }

  public Publisher getPublisher() {
    return publisher;
  }

  public List<BookAuthor> getContributors() {
    return List.copyOf(contributors);
  }

  public Set<Category> getCategories() {
    return Set.copyOf(categories);
  }
}
