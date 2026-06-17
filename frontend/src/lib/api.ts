import { getToken, getAuthUser, saveAuth, AuthUser } from "./auth";

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

export type AuthResponse = {
  token: string;
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

// ── HTTP helper ───────────────────────────────────────────────────────────────

async function request<T>(path: string, options: RequestInit = {}, requiresAuth = true): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> || {}),
  };

  if (requiresAuth && token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });

  if (res.status === 401) {
    // Token expired — clear and redirect to login
    if (typeof window !== "undefined") {
      import("./auth").then(m => m.clearAuth());
      window.location.href = "/login";
    }
    throw new Error("Session expired. Please log in again.");
  }

  if (!res.ok) {
    // Try to parse the backend ErrorResponse JSON
    try {
      const errJson = await res.json();
      const message = errJson?.message || errJson?.error || `Request failed: ${res.status}`;
      const details: string[] = errJson?.details ?? [];
      const full = details.length > 0 ? `${message}: ${details.join(", ")}` : message;
      throw new Error(full);
    } catch (parseErr) {
      if (parseErr instanceof Error && parseErr.message !== `Request failed: ${res.status}`) {
        throw parseErr;
      }
      const text = await res.text().catch(() => "");
      throw new Error(text || `Request failed: ${res.status}`);
    }
  }

  return res.json() as Promise<T>;
}

// ── Auth API ──────────────────────────────────────────────────────────────────

export const authApi = {
  register: async (email: string, password: string): Promise<AuthResponse> => {
    const res = await request<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }, false);
    saveAuth(res.token, { userId: res.userId, email: res.email, role: res.role });
    return res;
  },

  login: async (email: string, password: string): Promise<AuthResponse> => {
    const res = await request<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }, false);
    saveAuth(res.token, { userId: res.userId, email: res.email, role: res.role });
    return res;
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
  // styles — library is public, creation requires auth
  getStyles: (source = "PRESET,USER_DESCRIBED") =>
    request<StyleProfile[]>(`/api/styles/library?source=${encodeURIComponent(source)}`, {}, false),

  createStyleFromDescription: (name: string, description: string) =>
    request<StyleProfile>("/api/styles/from-description", {
      method: "POST",
      body: JSON.stringify({ name, description }),
    }),

  // sessions
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

  // progress
  getProgress: () => request<ProgressData>("/api/progress/me"),
};
