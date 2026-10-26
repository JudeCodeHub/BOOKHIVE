package lk.bookhive.catalog.service;

import java.time.Duration;
import java.util.UUID;

/**
 * Keys and lifetimes of the catalog's public cache entries (PublicCache, step 195). Only data
 * without live counts is cached: lists with availability are always computed fresh.
 */
public final class CatalogCacheKeys {

  private CatalogCacheKeys() {}

  public static final String CATEGORY_TREE = "public:categories";
  public static final Duration CATEGORY_TREE_TTL = Duration.ofHours(1);

  public static final Duration BOOK_TTL = Duration.ofMinutes(30);

  public static final String SUGGEST_PREFIX = "public:suggest:";
  public static final Duration SUGGEST_TTL = Duration.ofMinutes(5);

  public static String book(UUID id) {
    return "public:book:" + id;
  }
}
