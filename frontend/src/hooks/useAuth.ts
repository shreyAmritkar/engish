"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getAuthUser, saveAuth, clearAuth, AuthUser } from "@/lib/auth";
import { authApi } from "@/lib/api";

/**
 * Route guard hook.
 *
 * Two-phase verification:
 * 1. Check localStorage for a stored user profile (instant — no flicker)
 * 2. Verify the HttpOnly cookie is still valid via GET /api/auth/me
 *    If the server restarted or token expired, we catch the 401 here
 *    (api.ts already redirects on 401 — this is just belt-and-suspenders)
 *
 * ready=true only after BOTH checks pass so pages never render
 * with stale auth state.
 */
export function useAuth(requireAdmin = false) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const stored = getAuthUser();

    // No local profile → go to login immediately (no flicker)
    if (!stored) {
      router.replace("/login");
      return;
    }

    // Admin guard on local profile first (instant)
    if (requireAdmin && stored.role !== "ADMIN") {
      router.replace("/library");
      return;
    }

    // Verify cookie is still valid with the server
    authApi.me()
      .then((fresh) => {
        // Update local profile in case role changed server-side
        saveAuth({ userId: fresh.userId, email: fresh.email, role: fresh.role });
        setUser({ userId: fresh.userId, email: fresh.email, role: fresh.role });
        setReady(true);
      })
      .catch(() => {
        // 401 → api.ts already calls clearAuth() + redirects to /login
        // This catch just prevents unhandled promise rejection noise
      });
  }, [router, requireAdmin]);

  return { user, ready };
}