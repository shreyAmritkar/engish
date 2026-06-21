/**
 * Auth state management.
 *
 * The JWT token now lives exclusively in an HttpOnly cookie set by the backend.
 * JavaScript cannot read it — that's the whole point.
 *
 * We only store non-sensitive user info (email, role) in localStorage
 * so the UI can render the nav, guard routes, and show the admin panel
 * without any API round-trip on every page load.
 */

const USER_KEY = "sc_auth_user";

export type AuthUser = {
  userId: string;
  email: string;
  role: string;
};

/** Save the non-sensitive user profile after login/register. */
export function saveAuth(user: AuthUser) {
  if (typeof window === "undefined") return;
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** Get the stored user profile (for UI only — not a security check). */
export function getAuthUser(): AuthUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as AuthUser) : null;
}

/**
 * Clear the stored user profile (called on logout).
 * The actual token cookie is cleared server-side by POST /api/auth/logout.
 */
export function clearAuth() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(USER_KEY);
}

export function isAdmin(): boolean {
  return getAuthUser()?.role === "ADMIN";
}

/** True if we have a locally stored user profile. Used for UI-only route guards. */
export function isLoggedIn(): boolean {
  return !!getAuthUser();
}
