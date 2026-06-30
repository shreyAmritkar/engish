import { saveAuth, clearAuth, AuthUser } from "./auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, '') || "https://engish-gpsx.onrender.com";

// ── Types ─────────────────────────────────────────────────────────────────────

export type StyleProfile = {
  id: string;
  name: string;
  source: string;
  vocabularyTier: string;
  sentenceStructure: string;
  emotionalRange: string;
  powerDynamic: string;
  formalityLevel: number;
  keyPatterns: string[];
  avoidPatterns: string[];
  samplePhrases: string[];
  rawDescription?: string;
};

export type Session = {
  id: string;
  userId: string;
  styleProfileId: string;
  situation: string;
  requiredWord: string;
  emotionalContext: string;
  userResponse?: string;
  feedback?: FeedbackPayload;
};

export type FeedbackPayload = {
  scores: Record<string, number>;
  grammar_notes: string[];
  misinterpretation_warnings: string[];
  rewrites?: { assertive: string; diplomatic: string };
  coaching_tip?: string;
};

export type ProgressData = {
  userId: string;
  currentLevel: number;
  totalSessions: number;
  averageScores: Record<string, number>;
  weakAreas: string[];
  strongAreas: string[];
  habitFlags: string[];
  recentSessions: Array<{
    id: string;
    styleProfileId: string;
    situation: string;
    feedback?: FeedbackPayload;
    createdAt: string;
  }>;
};

/**
 * Token is no longer in the response body — it's in an HttpOnly cookie.
 * We only receive the user profile for UI rendering.
 */
export type AuthResponse = {
  userId: string;
  email: string;
  role: string;
};

export type AdminUser = {
  id: string;
  email: string;
  role: string;
  createdAt: string;
};

export type RivalEntry = {
  rivalUserId: string;
  email: string;
  dimension: string;
  sharedBestScore: number;
};

// ── HTTP helper ───────────────────────────────────────────────────────────────

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    // credentials: "include" tells the browser to send the HttpOnly cookie
    // automatically on every request — no manual token handling needed
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers as Record<string, string> || {}),
    },
  });

  if (res.status === 401 || res.status === 403 ) {
    // Cookie expired or invalid — clear local profile and go to login
    clearAuth();
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
    throw new Error("Session expired. Please sign in again.");
  }

  if (!res.ok) {
    try {
      const errJson = await res.json();
      const message = errJson?.message || errJson?.error || `Request failed: ${res.status}`;
      const details: string[] = errJson?.details ?? [];
      const errorCode: string | undefined = errJson?.errorCode;
      // Attach errorCode to the error object so callers can branch on it
      const err = new Error(details.length > 0 ? `${message}: ${details.join(", ")}` : message) as Error & { errorCode?: string };
      err.errorCode = errorCode;
      throw err;
    } catch (parseErr) {
      if (parseErr instanceof Error && !parseErr.message.startsWith("Request failed:")) {
        throw parseErr;
      }
      const text = await res.text().catch(() => "");
      throw new Error(text || `Request failed: ${res.status}`);
    }
  }

  // 204 No Content — don't try to parse body
  if (res.status === 204) return undefined as T;

  return res.json() as Promise<T>;
}

// ── Auth API ──────────────────────────────────────────────────────────────────

export const authApi = {
  register: async (email: string, password: string): Promise<AuthResponse> => {
    const res = await request<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    // Save user profile for UI — token is in the cookie, not here
    saveAuth({ userId: res.userId, email: res.email, role: res.role });
    return res;
  },

  login: async (email: string, password: string): Promise<AuthResponse> => {
    const res = await request<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    saveAuth({ userId: res.userId, email: res.email, role: res.role });
    return res;
  },

  /**
   * Real logout — hits the server so it clears the HttpOnly cookie.
   * Then clears the local user profile.
   */
  logout: async (): Promise<void> => {
    await request<void>("/api/auth/logout", { method: "POST" });
    clearAuth();
  },

  me: () => request<AuthResponse>("/api/auth/me"),
};

// ── Admin API ─────────────────────────────────────────────────────────────────

export const adminApi = {
  listUsers: () => request<AdminUser[]>("/api/admin/users"),

  changeRole: (userId: string, role: string) =>
    request<AdminUser>(`/api/admin/users/${userId}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role }),
    }),

  deleteUser: (userId: string) =>
    request<void>(`/api/admin/users/${userId}`, { method: "DELETE" }),
};

// ── Main API ──────────────────────────────────────────────────────────────────

export const api = {
  getStyles: (source = "PRESET,USER_DESCRIBED") =>
    request<StyleProfile[]>(`/api/styles/library?source=${encodeURIComponent(source)}`),

  createStyleFromDescription: (name: string, description: string) =>
    request<StyleProfile>("/api/styles/from-description", {
      method: "POST",
      body: JSON.stringify({ name, description }),
    }),

  startSession: (styleProfileId: string) =>
    request<Session>("/api/sessions/start", {
      method: "POST",
      body: JSON.stringify({ styleProfileId }),
    }),

  submitSession: (sessionId: string, userResponse: string) =>
    request<Session>(`/api/sessions/${sessionId}/submit`, {
      method: "POST",
      body: JSON.stringify({ userResponse }),
    }),

  getSession: (sessionId: string) => request<Session>(`/api/sessions/${sessionId}`),

  fetchRewrites: (sessionId: string) =>
    request<{ assertive: string; diplomatic: string }>(`/api/sessions/${sessionId}/rewrites`, {
      method: "POST",
    }),

  fetchCoachingTip: (sessionId: string) =>
    request<{ coaching_tip: string }>(`/api/sessions/${sessionId}/coaching-tip`, {
      method: "POST",
    }),

  getProgress: () => request<ProgressData>("/api/progress/me"),

  getRivals: () => request<RivalEntry[]>("/api/leaderboard/rivals"),
};