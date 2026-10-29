package lk.bookhive.notification.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.text.MessageBundles;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Turns a notification into an email in the member's language (NOTIF-1, I18N-3, step 309). One HTML
 * template per type ({@code templates/email/<type>.html}) is the single source: the subject is its
 * {@code <title>}, and the plain-text version is made from the same HTML, so the two can never say
 * different things.
 */
@Component
public class EmailRenderer {

  /** A rendered email. */
  public record RenderedEmail(String subject, String text, String html) {}

  private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

  private final ITemplateEngine engine;

  public EmailRenderer(@Qualifier("emailTemplateEngine") ITemplateEngine engine) {
    this.engine = engine;
  }

  /**
   * @param params the template's values (see each template's comment for what it uses)
   */
  public RenderedEmail render(
      NotificationType type, Language language, Map<String, Object> params) {
    Context context = new Context(MessageBundles.locale(language), params);
    context.setVariable("lang", language.code());
    String html = engine.process(type.templateName(), context);
    Matcher title = TITLE.matcher(html);
    String subject = title.find() ? HtmlUtils.htmlUnescape(title.group(1)).strip() : type.name();
    return new RenderedEmail(subject, HtmlText.toText(html), html);
  }
}
