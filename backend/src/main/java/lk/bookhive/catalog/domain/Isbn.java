package lk.bookhive.catalog.domain;

import java.util.Optional;

/**
 * An ISBN in both forms. Accepts what people type or scan ("978-955-21-0454-0", "955 21 0454 X")
 * and checks the check digit, so a mistyped digit is caught before it reaches the catalog.
 *
 * <p>Every ISBN-10 has an ISBN-13 (prefix 978). An ISBN-13 starting with 979 has no ISBN-10.
 *
 * @param isbn13 always set
 * @param isbn10 null for 979 ISBNs
 */
public record Isbn(String isbn13, String isbn10) {

  /** Empty if the text isn't a valid ISBN-10 or ISBN-13. */
  public static Optional<Isbn> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String digits = text.replaceAll("[\\s-]", "").toUpperCase();
    if (digits.matches("\\d{9}[\\dX]") && validIsbn10(digits)) {
      String isbn13 = withIsbn13CheckDigit("978" + digits.substring(0, 9));
      return Optional.of(new Isbn(isbn13, digits));
    }
    if (digits.matches("97[89]\\d{10}") && validIsbn13(digits)) {
      String isbn10 =
          digits.startsWith("978") ? withIsbn10CheckDigit(digits.substring(3, 12)) : null;
      return Optional.of(new Isbn(digits, isbn10));
    }
    return Optional.empty();
  }

  // ISBN-10: digits weighted 10..1 must sum to a multiple of 11 (X = 10)
  private static boolean validIsbn10(String isbn) {
    int sum = 0;
    for (int i = 0; i < 10; i++) {
      int value = isbn.charAt(i) == 'X' ? 10 : isbn.charAt(i) - '0';
      sum += value * (10 - i);
    }
    return sum % 11 == 0;
  }

  // ISBN-13: digits weighted 1,3,1,3... must sum to a multiple of 10
  private static boolean validIsbn13(String isbn) {
    return withIsbn13CheckDigit(isbn.substring(0, 12)).equals(isbn);
  }

  private static String withIsbn13CheckDigit(String first12) {
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      sum += (first12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
    }
    return first12 + (10 - sum % 10) % 10;
  }

  private static String withIsbn10CheckDigit(String first9) {
    int sum = 0;
    for (int i = 0; i < 9; i++) {
      sum += (first9.charAt(i) - '0') * (10 - i);
    }
    int check = (11 - sum % 11) % 11;
    return first9 + (check == 10 ? "X" : String.valueOf(check));
  }
}
