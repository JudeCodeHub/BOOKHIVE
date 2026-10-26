import type { Schema } from "@/lib/api";
import { ApiError } from "@/lib/api";

export type RenewalCheck = Schema<"RenewalCheckResponse">;

/** The reasons attached to a 422 RENEWAL_REFUSED, or null for any other error. */
export function refusedRenewal(error: unknown): RenewalCheck | null {
  if (error instanceof ApiError && error.code === "RENEWAL_REFUSED") {
    return (error.body.renewal as RenewalCheck | undefined) ?? null;
  }
  return null;
}
