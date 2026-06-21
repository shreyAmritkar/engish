"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getAuthUser, AuthUser } from "@/lib/auth";

/**
 * Route guard hook.
 *
 * The token is in an HttpOnly cookie — we can't read it in JS.
 * We use the stored user profile (email, role) to guard routes and
 * render the UI. The backend enforces real security on every request
 * via the cookie automatically.
 *
 * If there's no user profile, we redirect to /login.
 * If requireAdmin is true and the user isn't ADMIN, we redirect to /library.
 */
export function useAuth(requireAdmin = false) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const u = getAuthUser();

    if (!u) {
      router.replace("/login");
      return;
    }

    if (requireAdmin && u.role !== "ADMIN") {
      router.replace("/library");
      return;
    }

    setUser(u);
    setReady(true);
  }, [router, requireAdmin]);

  return { user, ready };
}
