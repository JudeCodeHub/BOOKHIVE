package lk.bookhive.common.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Cleans rich text from staff editors (news, event descriptions; checklist step 336) on the server,
 * before it's saved, so the website can show it as HTML safely: a script hidden in a post could
 * otherwise run in every reader's browser and take over their login (stored XSS).
 *
 * <p>Only simple formatting survives: paragraphs, headings, bold/italic/underline/strike, lists,
 * quotes and links (http, https, mailto; marked nofollow and opened without access to this page).
 * Scripts, styles, frames, forms, images, event handlers ({@code onclick}...) and {@code
 * javascript:} links are removed. Cleaning is done even though the editor only produces these tags:
 * a request can always be sent without the editor.
 */
public final class RichText {

  private static final Safelist SAFE =
      new Safelist()
          .addTags(
              "p",
              "br",
              "strong",
              "b",
              "em",
              "i",
              "u",
              "s",
              "h2",
              "h3",
              "ul",
              "ol",
              "li",
              "blockquote",
              "a")
          .addAttributes("a", "href", "target")
          .addProtocols("a", "href", "http", "https", "mailto")
          .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

  private RichText() {}

  /** Safe HTML; empty for null or blank input. */
  public static String clean(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
    String cleaned = Jsoup.clean(html, "", SAFE, output);
    // Links may only open a new tab, never the parent frame
    return cleaned.replaceAll("\\s*target=\"(?!_blank\")[^\"]*\"", "");
  }

  /** The words only, for previews and search (all tags removed). */
  public static String text(String html) {
    return html == null ? "" : Jsoup.parse(html).text();
  }
}
