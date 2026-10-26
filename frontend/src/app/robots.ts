import type { MetadataRoute } from "next";
import { BOOK_SITEMAPS, SITE_URL } from "@/lib/seo/site";

/**
 * robots.txt (checklist step 209): search engines may crawl the public site, but not the
 * private areas (which need a login anyway) or the API. Points them to the sitemaps.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/*/account", "/*/staff", "/*/admin", "/api/"],
    },
    sitemap: [
      `${SITE_URL}/sitemap.xml`,
      ...Array.from({ length: BOOK_SITEMAPS }, (_, i) => `${SITE_URL}/books/sitemap/${i}.xml`),
    ],
  };
}
