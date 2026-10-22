import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const nextConfig: NextConfig = {
  // Build a self-contained server (.next/standalone) for the Docker image: no node_modules needed
  output: "standalone",
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
