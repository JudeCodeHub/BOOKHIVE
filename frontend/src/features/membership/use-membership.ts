"use client";

import { useQuery } from "@tanstack/react-query";
import { api, type Schema } from "@/lib/api";

export type Membership = Schema<"MembershipResponse">;

export const MEMBERSHIP_QUERY_KEY = ["me", "membership"] as const;

/** The logged-in user's membership or application; null if they never applied (204). */
export function useMembership() {
  return useQuery({
    queryKey: MEMBERSHIP_QUERY_KEY,
    // TanStack Query treats `undefined` as "no data yet", so a 204 becomes null
    queryFn: async () => (await api.get<Membership | undefined>("/me/membership")) ?? null,
  });
}
