package lk.bookhive.common.domain;

import java.util.Arrays;

/**
 * The three languages BookHive speaks (PRD I18N-1). The code matches the frontend URL prefix (/en,
 * /si, /ta) and is what the database stores.
 */
public enum Language {
  EN("en"),
  SI("si"),
  TA("ta");

  private final String code;

  Language(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** {@code Language.fromCode("si")}; throws for unknown codes. */
  public static Language fromCode(String code) {
    return Arrays.stream(values())
        .filter(language -> language.code.equalsIgnoreCase(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown language: " + code));
  }
}
