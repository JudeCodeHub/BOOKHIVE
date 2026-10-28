package lk.bookhive.notification.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lk.bookhive.common.domain.Language;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * The words of in-app notifications (step 312), from {@code messages/inapp*.properties}.
 *
 * <ul>
 *   <li>{@link #facts}: when a notification is created, keeps only plain values as text (a list of
 *       books becomes its count, the first title and the first due date).
 *   <li>{@link #title} / {@link #body}: when it's read, writes the text in the reader's language,
 *       with dates in that language too.
 * </ul>
 */
@Component
public class InAppTexts {

  /** Which facts fill {0}, {1}... of each type's body. */
  private static final Map<NotificationType, List<String>> BODY_ARGS =
      Map.ofEntries(
          Map.entry(NotificationType.MEMBERSHIP_APPROVED, List.of("memberNo", "expiresAt")),
          Map.entry(NotificationType.MEMBERSHIP_REJECTED, List.of("reason")),
          Map.entry(NotificationType.MEMBERSHIP_EXPIRING, List.of("expiresAt")),
          Map.entry(NotificationType.DUE_SOON, List.of("count", "title", "dueDate")),
          Map.entry(NotificationType.OVERDUE, List.of("count", "title", "dueDate")),
          Map.entry(NotificationType.HOLD_READY, List.of("title", "pickUpBy")),
          Map.entry(NotificationType.HOLD_EXPIRING, List.of("title", "pickUpBy")),
          Map.entry(NotificationType.HOLD_EXPIRED, List.of("title")),
          Map.entry(NotificationType.PAYMENT_RECEIVED, List.of("amount")),
          Map.entry(NotificationType.EVENT_REMINDER, List.of("title", "date")));

  private final ResourceBundleMessageSource messages = MessageBundles.bundle("messages/inapp");

  /** The values to store with a new notification: plain text only. */
  public Map<String, String> facts(Map<String, Object> params) {
    Map<String, String> facts = new HashMap<>();
    params.forEach(
        (key, value) -> {
          if (value instanceof List<?> list) {
            facts.put("count", String.valueOf(list.size()));
            if (!list.isEmpty() && list.getFirst() instanceof Map<?, ?> first) {
              first.forEach(
                  (k, v) -> {
                    if (v != null) {
                      facts.putIfAbsent(k.toString(), text(v));
                    }
                  });
            }
          } else if (value != null && !key.endsWith("Link") && !key.equals("link")) {
            facts.put(key, text(value));
          }
        });
    return facts;
  }

  public String title(NotificationType type, Language language) {
    return messages.getMessage(
        "inapp." + type.templateName() + ".title", null, MessageBundles.locale(language));
  }

  public String body(NotificationType type, Map<String, String> facts, Language language) {
    Locale locale = MessageBundles.locale(language);
    Object[] args =
        BODY_ARGS.getOrDefault(type, List.of()).stream()
            .map(name -> shown(facts.get(name), locale))
            .toArray();
    return messages.getMessage("inapp." + type.templateName() + ".body", args, locale);
  }

  private static String text(Object value) {
    return value instanceof BigDecimal amount ? amount.toPlainString() : value.toString();
  }

  /** Dates stored as 2026-11-09 are shown as "9 Nov 2026" in the reader's language. */
  private static Object shown(String value, Locale locale) {
    if (value == null) {
      return "";
    }
    if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
      try {
        TemporalAccessor date = LocalDate.parse(value);
        return DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date);
      } catch (DateTimeParseException e) {
        return value;
      }
    }
    // Counts stay numbers so {0,choice,...} plurals work
    return value.matches("\\d+") ? Integer.valueOf(value) : value;
  }
}
