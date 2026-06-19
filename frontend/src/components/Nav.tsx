"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { getAuthUser, clearAuth, AuthUser } from "@/lib/auth";
import { authApi } from "@/lib/api";

const navLinks = [
  { href: "/library", label: "Library" },
  { href: "/progress", label: "Progress" },
];

export function Nav() {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    setUser(getAuthUser());
  }, [pathname]);

  async function logout() {
    try {
      await authApi.logout(); // clears HttpOnly cookie server-side
    } catch {
      // If the request fails (e.g. already expired), still clear locally
      clearAuth();
    }
    setUser(null);
    router.push("/login");
  }

  return (
    <header className="border-b border-slate-200 bg-white/80 backdrop-blur sticky top-0 z-20">
      <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
        <Link href="/library" className="font-semibold text-ink text-lg tracking-tight">
          Style Communicator
        </Link>

        <nav className="flex items-center gap-2">
          {user && navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={`px-3 py-1.5 rounded-full text-sm font-medium transition ${
                pathname?.startsWith(link.href)
                  ? "bg-accent text-white"
                  : "text-slate hover:bg-slate-100"
              }`}
            >
              {link.label}
            </Link>
          ))}

          {user?.role === "ADMIN" && (
            <Link
              href="/admin"
              className={`px-3 py-1.5 rounded-full text-sm font-medium transition ${
                pathname?.startsWith("/admin")
                  ? "bg-accent text-white"
                  : "text-slate hover:bg-slate-100"
              }`}
            >
              Admin
            </Link>
          )}

          {user ? (
            <div className="flex items-center gap-3 ml-2 pl-3 border-l border-slate-200">
              <span className="text-sm text-slate hidden sm:block">{user.email}</span>
              <button
                onClick={logout}
                className="px-3 py-1.5 rounded-full text-sm font-medium text-slate hover:bg-slate-100 transition"
              >
                Sign out
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2 ml-2 pl-3 border-l border-slate-200">
              <Link
                href="/login"
                className="px-3 py-1.5 rounded-full text-sm font-medium text-slate hover:bg-slate-100 transition"
              >
                Sign in
              </Link>
              <Link
                href="/register"
                className="px-3 py-1.5 rounded-full text-sm font-medium bg-accent text-white hover:bg-accent/90 transition"
              >
                Register
              </Link>
            </div>
          )}
        </nav>
      </div>
    </header>
  );
}
