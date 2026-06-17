"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getToken, getAuthUser, AuthUser } from "@/lib/auth";

export function useAuth(requireAdmin = false) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const token = getToken();
    const u = getAuthUser();

    if (!token || !u) {
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
