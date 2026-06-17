// Auth token storage — localStorage (simple, deployable)
const TOKEN_KEY = "sc_auth_token";
const USER_KEY  = "sc_auth_user";

export type AuthUser = {
  userId: string;
  email: string;
  role: string;
};

export function saveAuth(token: string, user: AuthUser) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getAuthUser(): AuthUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) : null;
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isAdmin(): boolean {
  return getAuthUser()?.role === "ADMIN";
}

export function isLoggedIn(): boolean {
  return !!getToken();
}
