package lk.bookhive.notification.service;

import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * The plain-text version of one of our email templates (step 309). Not a general HTML converter: it
 * relies on the simple markup the templates use (headings, paragraphs, lists, links, a table of
 * details) and on everything decorative being inside {@code data-no-text} blocks.
 */
final class HtmlText {

  private static final Pattern HEAD = Pattern.compile("(?is)<head.*?</head>");
  private static final Pattern NO_TEXT =
      Pattern.compile("(?is)<(div|span|td|p)[^>]*data-no-text[^>]*>.*?</\\1>");
  private static final Pattern LINK =
      Pattern.compile("(?is)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
  private static final Pattern LINE_BREAKS =
      Pattern.compile("(?i)<br\\s*/?>|</p>|</h[1-6]>|</tr>|</div>");
  private static final Pattern LIST_ITEM = Pattern.compile("(?i)<li[^>]*>");
  private static final Pattern CELL_END = Pattern.compile("(?i)</td>");
  private static final Pattern TAGS = Pattern.compile("<[^>]+>");
  private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
  private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n(\\s*\\n)+");

  private HtmlText() {}

  static String toText(String html) {
    String text = HEAD.matcher(html).replaceAll("");
    text = NO_TEXT.matcher(text).replaceAll("");
    // "Open My loans (https://...)" so the link survives without HTML
    text = LINK.matcher(text).replaceAll("$2 ($1)");
    text = LIST_ITEM.matcher(text).replaceAll("\n- ");
    text = CELL_END.matcher(text).replaceAll(" ");
    text = LINE_BREAKS.matcher(text).replaceAll("\n");
    text = TAGS.matcher(text).replaceAll("");
    text = HtmlUtils.htmlUnescape(text);
    text = SPACES.matcher(text).replaceAll(" ");
    text = text.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
    text = BLANK_LINES.matcher(text).replaceAll("\n\n");
    return text.strip() + "\n";
  }
}
