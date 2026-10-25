package lk.bookhive.catalog.service;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.bookhive.catalog.config.IsbnLookupProperties;
import lk.bookhive.catalog.domain.Isbn;
import lk.bookhive.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fills in a book's details from its ISBN using Open Library (CAT-3, checklist steps 162-163), so
 * cataloguing a new book is "scan, check, save". Answers are cached in Redis: scanning the same
 * ISBN again (a second copy, a correction) doesn't call Open Library again.
 */
@Service
@EnableConfigurationProperties(IsbnLookupProperties.class)
public class IsbnLookupService {

  private static final Logger log = LoggerFactory.getLogger(IsbnLookupService.class);
  private static final String CACHE_PREFIX = "isbn-lookup:";
  private static final Pattern YEAR = Pattern.compile("\\b(1[5-9]\\d{2}|20\\d{2})\\b");

  /**
   * What Open Library knows. Names only: the cataloguer picks or creates the matching authors and
   * publisher in our catalog.
   *
   * @param found false = Open Library doesn't have this ISBN
   * @param coverImageUrl Open Library's cover picture, if any (staff can upload it as our cover)
   */
  public record IsbnLookupResult(
      boolean found,
      String isbn13,
      String isbn10,
      String title,
      String subtitle,
      List<String> authors,
      String publisher,
      Short publishYear,
      Integer pages,
      String coverImageUrl) {

    static IsbnLookupResult notFound(Isbn isbn) {
      return new IsbnLookupResult(
          false, isbn.isbn13(), isbn.isbn10(), null, null, List.of(), null, null, null, null);
    }
  }

  private final IsbnLookupProperties properties;
  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final RestClient openLibrary;

  public IsbnLookupService(
      IsbnLookupProperties properties, StringRedisTemplate redis, JsonMapper json) {
    this.properties = properties;
    this.redis = redis;
    this.json = json;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    requestFactory.setReadTimeout(properties.timeout());
    this.openLibrary =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("User-Agent", properties.userAgent())
            .requestFactory(requestFactory)
            .build();
  }

  /**
   * @throws ServiceUnavailableException ISBN_LOOKUP_UNAVAILABLE when Open Library is down or slow
   */
  public IsbnLookupResult lookup(Isbn isbn) {
    String key = CACHE_PREFIX + isbn.isbn13();
    IsbnLookupResult cached = readCache(key);
    if (cached != null) {
      return cached;
    }
    IsbnLookupResult result = fetch(isbn);
    writeCache(key, result);
    return result;
  }

  private IsbnLookupResult fetch(Isbn isbn) {
    // Ask for both forms at once: some records are only filed under one of them
    String bibkeys =
        "ISBN:" + isbn.isbn13() + (isbn.isbn10() == null ? "" : ",ISBN:" + isbn.isbn10());
    JsonNode body;
    try {
      body =
          openLibrary
              .get()
              .uri(
                  uri ->
                      uri.path("/api/books")
                          .queryParam("bibkeys", bibkeys)
                          .queryParam("format", "json")
                          .queryParam("jscmd", "data")
                          .build())
              .retrieve()
              .body(JsonNode.class);
    } catch (RestClientException e) {
      log.warn("Open Library lookup for {} failed: {}", isbn.isbn13(), e.getMessage());
      throw new ServiceUnavailableException(
          "ISBN_LOOKUP_UNAVAILABLE",
          "The book lookup service isn't answering. Please type the details, or try again later.");
    }
    if (body == null || body.isEmpty()) {
      return IsbnLookupResult.notFound(isbn);
    }
    // The answer is keyed by whichever bibkey matched
    return parse(isbn, body.values().iterator().next());
  }

  private static IsbnLookupResult parse(Isbn isbn, JsonNode data) {
    List<String> authors = new ArrayList<>();
    for (JsonNode author : data.path("authors")) {
      String name = text(author.path("name"));
      if (name != null) {
        authors.add(name);
      }
    }
    JsonNode publishers = data.path("publishers");
    String publisher = publishers.isEmpty() ? null : text(publishers.get(0).path("name"));
    // publish_date is free text: "1944", "March 2005", "2005-03-01"...
    Short year = null;
    String date = text(data.path("publish_date"));
    if (date != null) {
      Matcher matcher = YEAR.matcher(date);
      if (matcher.find()) {
        year = Short.valueOf(matcher.group(1));
      }
    }
    JsonNode pages = data.path("number_of_pages");
    JsonNode cover = data.path("cover");
    String coverUrl = text(cover.path("large"));
    return new IsbnLookupResult(
        true,
        isbn.isbn13(),
        isbn.isbn10(),
        text(data.path("title")),
        text(data.path("subtitle")),
        List.copyOf(authors),
        publisher,
        year,
        pages.isNumber() && pages.asInt() > 0 ? pages.asInt() : null,
        coverUrl != null ? coverUrl : text(cover.path("medium")));
  }

  private static String text(JsonNode node) {
    return node.isString() && !node.asString().isBlank() ? node.asString().strip() : null;
  }

  // --- Redis cache (step 163). Redis trouble never breaks the lookup: we just skip the cache. --

  private IsbnLookupResult readCache(String key) {
    try {
      String cached = redis.opsForValue().get(key);
      return cached == null ? null : json.readValue(cached, IsbnLookupResult.class);
    } catch (RuntimeException e) {
      log.debug("ISBN cache read skipped: {}", e.getMessage());
      return null;
    }
  }

  private void writeCache(String key, IsbnLookupResult result) {
    try {
      redis
          .opsForValue()
          .set(
              key,
              json.writeValueAsString(result),
              result.found() ? properties.cacheTtl() : properties.notFoundTtl());
    } catch (RuntimeException e) {
      log.debug("ISBN cache write skipped: {}", e.getMessage());
    }
  }
}
