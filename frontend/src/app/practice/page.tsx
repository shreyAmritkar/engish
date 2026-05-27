"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, Session } from "@/lib/api";
import { ConversationView } from "@/components/ConversationView";

type Mode = "conversation" | "single";

function PracticeContent() {
  const router = useRouter();
  const params = useSearchParams();
  const styleId = params.get("styleId");

  const [session, setSession] = useState<Session | null>(null);
  const [mode, setMode] = useState<Mode>("conversation");
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!styleId) {
      setError("No style selected. Pick one from the library.");
      setLoading(false);
      return;
    }
    api
      .startSession(styleId)
      .then(setSession)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [styleId]);

  // Single-response mode: track required word usage
  const wordUsed = useMemo(() => {
    if (!session?.requiredWord) return false;
    return response.toLowerCase().includes(session.requiredWord.toLowerCase());
  }, [response, session?.requiredWord]);

  // Single-response mode: submit
  async function handleAnalyse() {
    if (!session) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await api.submitSession(session.id, response);
      router.push(`/feedback?sessionId=${result.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Submit failed");
    } finally {
      setSubmitting(false);
    }
  }

  // Conversation mode: called by ConversationView when scoring is done
  function handleConversationFinished(sessionId: string) {
    router.push(`/feedback?sessionId=${sessionId}`);
  }

  if (loading) return <p className="text-slate">Starting practice session…</p>;
  if (error && !session) return <p className="text-red-600">{error}</p>;
  if (!session) return null;

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-start justify-between gap-4">
        <h1 className="text-3xl font-bold text-ink">Practice Arena</h1>

        {/* Mode toggle */}
        <div className="flex rounded-lg border overflow-hidden text-sm font-medium shrink-0">
          <button
            type="button"
            onClick={() => setMode("conversation")}
            className={`px-4 py-2 transition-colors ${
              mode === "conversation"
                ? "bg-accent text-white"
                : "bg-white text-slate hover:bg-slate-50"
            }`}
          >
            Conversation
          </button>
          <button
            type="button"
            onClick={() => setMode("single")}
            className={`px-4 py-2 transition-colors ${
              mode === "single"
                ? "bg-accent text-white"
                : "bg-white text-slate hover:bg-slate-50"
            }`}
          >
            Single response
          </button>
        </div>
      </div>

      {/* Situation card — shared between modes */}
      <div className="rounded-xl border bg-white p-6 shadow-sm space-y-3">
        <p className="text-xs uppercase tracking-wide text-slate font-medium">Situation</p>
        <p className="text-ink text-lg leading-relaxed">{session.situation}</p>
        <p className="text-sm text-slate">
          Emotional context:{" "}
          <span className="font-medium">{session.emotionalContext}</span>
        </p>

        {mode === "single" && (
          <span
            className={`inline-block px-3 py-1 rounded-full text-sm font-medium ${
              wordUsed ? "bg-green-100 text-green-800" : "bg-amber-50 text-amber-800"
            }`}
          >
            Required word: {session.requiredWord}
          </span>
        )}
      </div>

      {/* Conversation mode */}
      {mode === "conversation" && (
        <ConversationView
          session={session}
          onFinished={handleConversationFinished}
        />
      )}

      {/* Single-response mode (original flow) */}
      {mode === "single" && (
        <div className="space-y-3">
          <label className="text-sm font-medium text-slate">Your response</label>
          <textarea
            className="w-full min-h-[200px] border rounded-xl px-4 py-3 bg-white"
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            placeholder="Write how you would respond in this situation…"
          />
          <p className="text-xs text-slate text-right">
            {response.trim().split(/\s+/).filter(Boolean).length} words
          </p>

          {error && <p className="text-red-600 text-sm">{error}</p>}

          <button
            type="button"
            onClick={handleAnalyse}
            disabled={submitting || response.trim().length < 20}
            className="px-6 py-3 rounded-lg bg-accent text-white font-semibold disabled:opacity-50"
          >
            {submitting ? "Analysing…" : "Analyse"}
          </button>
        </div>
      )}
    </div>
  );
}

export default function PracticeArenaPage() {
  return (
    <Suspense fallback={<p>Loading…</p>}>
      <PracticeContent />
    </Suspense>
  );
}