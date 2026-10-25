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

/**
 * One contributor of a book, with their role and place in the order printed on the cover (table
 * {@code book_authors}). Created only through {@link Book#setContributors}.
 */
@Entity
@Table(name = "book_authors")
public class BookAuthor extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false, updatable = false)
  private Book book;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private Author author;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AuthorRole role;

  @Column(nullable = false)
  private short position;

  protected BookAuthor() {
    // for JPA
  }

  BookAuthor(Book book, Author author, AuthorRole role, int position) {
    this.book = book;
    this.author = author;
    this.role = role;
    this.position = (short) position;
  }

  public Book getBook() {
    return book;
  }

  public Author getAuthor() {
    return author;
  }

  public AuthorRole getRole() {
    return role;
  }

  public int getPosition() {
    return position;
  }
}
