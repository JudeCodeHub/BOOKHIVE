package lk.bookhive.common.text;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lk.bookhive.common.domain.Language;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * The shared rules for wording in three languages (emails, SMS, in-app, report exports): one place
 * for how bundles are read and which locale each language uses.
 */
public final class MessageBundles {

  private MessageBundles() {}

  /**
   * A bundle such as {@code messages/sms} ({@code sms.properties}, {@code sms_si.properties}...),
   * read as UTF-8. A missing Sinhala or Tamil line falls back to English, never to the server's own
   * language.
   */
  public static ResourceBundleMessageSource bundle(String basename) {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename(basename);
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setFallbackToSystemLocale(false);
    return source;
  }

  /** Sinhala and Tamil as used in Sri Lanka, so dates come out with local month names. */
  public static Locale locale(Language language) {
    return switch (language) {
      case SI -> Locale.forLanguageTag("si-LK");
      case TA -> Locale.forLanguageTag("ta-LK");
      case EN -> Locale.forLanguageTag("en-LK");
    };
  }
}
