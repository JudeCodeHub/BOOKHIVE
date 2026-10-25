package lk.bookhive.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lk.bookhive.common.domain.BaseEntity;

/**
 * An uploaded proof document (table {@code member_documents}). The file itself is in object storage
 * under {@link #getStorageKey()}; this row only points to it.
 */
@Entity
@Table(name = "member_documents")
public class MemberDocument extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false, updatable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DocumentKind kind;

  @Column(nullable = false, length = 300, unique = true)
  private String storageKey;

  @Column(nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false)
  private long sizeBytes;

  private String originalFilename;

  private LocalDate deleteAfter;

  protected MemberDocument() {
    // for JPA
  }

  public MemberDocument(
      Member member,
      DocumentKind kind,
      String storageKey,
      String contentType,
      long sizeBytes,
      String originalFilename) {
    this.member = member;
    this.kind = kind;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.originalFilename = originalFilename;
  }

  public Member getMember() {
    return member;
  }

  public DocumentKind getKind() {
    return kind;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public LocalDate getDeleteAfter() {
    return deleteAfter;
  }
}
