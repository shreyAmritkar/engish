"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, Session } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { parseApiError, isRetryable } from "@/lib/errors";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";

function PracticeContent() {
  const router = useRouter();
  const { toast } = useToast();
  const params = useSearchParams();
  const styleId = params.get("styleId");

  const [session, setSession] = useState<Session | null>(null);
  const [response, setResponse] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const [canRetry, setCanRetry] = useState(false);
  const [intentNudge, setIntentNudge] = useState<string | null>(null);

  useEffect(() => {
    if (!styleId) {
      setError("No style selected. Pick one from the library.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    setCanRetry(false);
    api.startSession(styleId)
      .then(setSession)
      .catch((e) => {
        const msg = parseApiError(e);
        setError(msg);
        setCanRetry(isRetryable(e));
        if (!msg.includes("session has expired")) {
          toast("error", "Couldn't start session", msg);
        }
      })
      .finally(() => setLoading(false));
  }, [styleId, retryCount]); // eslint-disable-line react-hooks/exhaustive-deps

  const wordUsed = useMemo(() => {
    if (!session?.requiredWord) return false;
    return response.toLowerCase().includes(session.requiredWord.toLowerCase());
  }, [response, session?.requiredWord]);

  async function handleAnalyse() {
    if (!session) return;
    setSubmitting(true);
    setError(null);
    setIntentNudge(null);
    try {
      const result = await api.submitSession(session.id, response);
      router.push(`/feedback?sessionId=${result.id}`);
    } catch (e) {
      const msg = parseApiError(e);
      const errorCode = (e as { errorCode?: string })?.errorCode;
      // INTENT_VALIDATION_FAILED → amber nudge, not red error banner
      if (errorCode === "INTENT_VALIDATION_FAILED") {
        setIntentNudge(msg);
      } else {
        setError(msg);
        if (!msg.includes("session has expired")) {
          toast("error", "Submit failed", msg);
        }
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="max-w-3xl mx-auto space-y-6">
        <Skeleton className="h-9 w-48" />
        <div className="rounded-xl border bg-white p-6 space-y-3">
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-6 w-full" />
          <Skeleton className="h-6 w-3/4" />
          <Skeleton className="h-7 w-36" />
        </div>
        <Skeleton className="h-48 w-full rounded-xl" />
      </div>
    );
  }

  if (error && !session) {
    return (
      <div className="max-w-3xl mx-auto space-y-4">
        <ErrorBanner message={error} />
        {canRetry ? (
          <button
            onClick={() => setRetryCount((c) => c + 1)}
            className="px-4 py-2 rounded-lg border border-slate-300 text-sm font-medium hover:bg-slate-50"
          >
            Try again
          </button>
        ) : (
          <button
            onClick={() => router.push("/library")}
            className="px-4 py-2 rounded-lg border border-slate-300 text-sm font-medium hover:bg-slate-50"
          >
            Back to library
          </button>
        )}
      </div>
    );
  }

  if (!session) return null;

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-ink">Practice Arena</h1>

      <div className="rounded-xl border bg-white p-6 shadow-sm space-y-3">
        <p className="text-xs uppercase tracking-wide text-slate font-medium">Situation</p>
        <p className="text-ink text-lg leading-relaxed">{session.situation}</p>
        <p className="text-sm text-slate">
          Emotional context:{" "}
          <span className="font-medium">{session.emotionalContext}</span>
        </p>
        <span
          className={`inline-block px-3 py-1 rounded-full text-sm font-medium ${
            wordUsed ? "bg-green-100 text-green-800" : "bg-amber-50 text-amber-800"
          }`}
        >
          Required word: {session.requiredWord}
        </span>
      </div>

      <div className="space-y-3">
        <label className="text-sm font-medium text-slate">Your response</label>
        <textarea
          className="w-full min-h-[200px] border rounded-xl px-4 py-3 bg-white focus:outline-none focus:ring-2 focus:ring-accent"
          value={response}
          onChange={(e) => { setResponse(e.target.value); setIntentNudge(null); }}
          placeholder="Write how you would respond in this situation…"
        />
        <p className="text-xs text-slate text-right">
          {response.trim().split(/\s+/).filter(Boolean).length} words
        </p>

        {/* Amber nudge for intent validation failures */}
        {intentNudge && (
          <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 flex gap-2">
            <span>💬</span>
            <span>{intentNudge}</span>
          </div>
        )}

        <ErrorBanner message={error} onDismiss={() => setError(null)} />

        <button
          type="button"
          onClick={handleAnalyse}
          disabled={submitting || response.trim().length < 20}
          className="px-6 py-3 rounded-lg bg-accent text-white font-semibold disabled:opacity-50"
        >
          {submitting ? "Analysing…" : "Analyse"}
        </button>
      </div>
    </div>
  );
}

export default function PracticeArenaPage() {
  const { ready } = useAuth();
  if (!ready) return null;
  return (
    <Suspense fallback={
      <div className="max-w-3xl mx-auto space-y-6">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    }>
      <PracticeContent />
    </Suspense>
  );
}
