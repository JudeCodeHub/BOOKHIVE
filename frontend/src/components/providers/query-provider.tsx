"use client";

import type { ReactNode } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { getQueryClient } from "@/lib/query-client";

/**
 * Makes TanStack Query available to client components:
 *
 *   const { data, isPending, error } = useQuery({
 *     queryKey: ["loans"],
 *     queryFn: () => api.get<Loan[]>("/me/loans"),
 *   });
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const queryClient = getQueryClient();

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {/* Floating cache inspector; only included in development builds */}
      <ReactQueryDevtools initialIsOpen={false} buttonPosition="bottom-right" />
    </QueryClientProvider>
  );
}
