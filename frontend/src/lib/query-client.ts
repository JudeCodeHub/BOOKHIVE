import { isServer, QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Data counts as fresh for a minute: no refetch on every mount / tab switch
        staleTime: 60 * 1000,
        // Retry network hiccups and server errors, but not 4xx: a 404 or 403 won't fix itself
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
            return false;
          }
          return failureCount < 2;
        },
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

/**
 * Server: a new client per request, so one user's data never leaks into another's page.
 * Browser: one client for the whole session, so the cache survives page navigation.
 */
export function getQueryClient() {
  if (isServer) {
    return makeQueryClient();
  }
  browserQueryClient ??= makeQueryClient();
  return browserQueryClient;
}
