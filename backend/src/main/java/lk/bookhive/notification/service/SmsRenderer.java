package lk.bookhive.notification.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lk.bookhive.common.domain.Language;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Short SMS texts in the member's language (step 311), from {@code messages/sms*.properties}. Kept
 * short on purpose: a Sinhala or Tamil SMS fits only 70 characters per part, and each part costs
 * money. Uses the same values the email gets.
 */
@Component
public class SmsRenderer {

  private final MessageSource messages = MessageBundles.bundle("messages/sms");

  /** The text, or null when this type has no SMS wording. */
  public String render(NotificationType type, Language language, Map<String, Object> params) {
    Locale locale = MessageBundles.locale(language);
    DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM", locale);
    Object[] args =
        switch (type) {
          case DUE_SOON, OVERDUE -> {
            List<?> books = (List<?>) params.getOrDefault("books", List.of());
            Map<?, ?> first = books.isEmpty() ? Map.of() : (Map<?, ?>) books.getFirst();
            Object due = first.get("dueDate");
            yield new Object[] {
              books.size(), first.get("title"), due instanceof LocalDate d ? day.format(d) : ""
            };
          }
          case HOLD_READY -> {
            Object by = params.get("pickUpBy");
            yield new Object[] {
              params.get("title"), by instanceof LocalDate d ? day.format(d) : ""
            };
          }
          default -> null;
        };
    if (args == null) {
      return null;
    }
    return messages.getMessage("sms." + type.templateName(), args, locale);
  }
}
