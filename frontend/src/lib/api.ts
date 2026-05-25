import { getUserId } from "./user";

const API_URL = process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, '')

export type StyleProfile = {
  id: string;
  name: string;
  source: string;
  communityVotes: number;
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

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const userId = getUserId();
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(userId ? { "X-User-Id": userId } : {}),
    ...(options.headers || {}),
  };
  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `Request failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  getStyles: (source = "COMMUNITY,PRESET,USER_DESCRIBED") =>
    request<StyleProfile[]>(`/api/styles/library?source=${encodeURIComponent(source)}`),

  createStyleFromDescription: (name: string, description: string) =>
    request<StyleProfile>("/api/styles/from-description", {
      method: "POST",
      body: JSON.stringify({ userId: getUserId(), name, description }),
    }),

  submitCommunityStyle: (characterName: string, excerpts: string[]) =>
    request<{ cardId: string; status: string; profile: StyleProfile }>(
      "/api/styles/community/submit",
      {
        method: "POST",
        body: JSON.stringify({
          userId: getUserId(),
          characterName,
          excerpts,
        }),
      }
    ),

  voteCommunityCard: (cardId: string) =>
    request<{ cardId: string; status: string }>(`/api/styles/community/${cardId}/vote`, {
      method: "POST",
    }),

  startSession: (styleProfileId: string) =>
    request<Session>("/api/sessions/start", {
      method: "POST",
      body: JSON.stringify({ userId: getUserId(), styleProfileId }),
    }),

  submitSession: (sessionId: string, userResponse: string) =>
    request<Session>(`/api/sessions/${sessionId}/submit`, {
      method: "POST",
      body: JSON.stringify({ userId: getUserId(), userResponse }),
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

  getProgress: () => request<ProgressData>(`/api/progress/${getUserId()}`),
};
