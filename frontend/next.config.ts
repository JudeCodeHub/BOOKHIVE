import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const nextConfig: NextConfig = {
  // Build a self-contained server (.next/standalone) for the Docker image: no node_modules needed
  output: "standalone",
  cacheComponents: true,
  partialPrefetching: true,
  experimental: {
    // Dev-time "instant navigation" checks only for pages that ask for them (export const
    // instant = true): the public pages. The member/staff/admin pages wait for the login, which
    // lives only in the browser, so they can't be rendered ahead of time and aren't checked.
    instantInsights: {
      validationLevel: "manual-warning",
    },
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

// Connects next-intl to src/i18n/request.ts (language + messages for each request)
const withNextIntl = createNextIntlPlugin();
export default withNextIntl(nextConfig);
