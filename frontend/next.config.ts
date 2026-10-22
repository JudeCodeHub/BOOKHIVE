import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
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
