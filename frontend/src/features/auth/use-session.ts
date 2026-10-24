"use client";

import { useSyncExternalStore } from "react";
import {
  getAuthState,
  getServerAuthState,
  hasRole,
  subscribe,
  type Role,
} from "@/features/auth/session";

/**
 * Who is logged in, for any client component. Re-renders when the session changes (login,
 * logout, refresh, logout in another tab).
 *
 *   const { status, user, hasRole } = useSession();
 *   if (status === "loading") return <Skeleton />;
 *   if (hasRole("LIBRARIAN")) ...
 *
 * The server always renders "loading" (it can't see the in-memory session), the browser then
 * settles on "authenticated" or "anonymous".
 */
export function useSession() {
  const state = useSyncExternalStore(subscribe, getAuthState, getServerAuthState);
  return {
    status: state.status,
    session: state.session,
    user: state.session?.user ?? null,
    hasRole: (role: Role) => hasRole(state.session, role),
  };
}
