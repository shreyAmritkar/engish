"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { api, Session } from "@/lib/api";
import { ScoreRadar } from "@/components/ScoreRadar";
import { parseApiError } from "@/lib/errors";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";

function FeedbackContent() {
  const params = useSearchParams();
  const { toast } = useToast();
  const sessionId = params.get("sessionId");

  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rewritesLoading, setRewritesLoading] = useState(false);
  const [tipLoading, setTipLoading] = useState(false);
  const [rewritesError, setRewritesError] = useState<string | null>(null);
  const [tipError, setTipError] = useState<string | null>(null);

  useEffect(() => {
    if (!sessionId) {
      setError("Missing session ID.");
      setLoading(false);
      return;
    }
    api.getSession(sessionId)
      .then(setSession)
      .catch((e) => setError(parseApiError(e)))
      .finally(() => setLoading(false));
  }, [sessionId]);

  const feedback = session?.feedback;
  const scores = feedback?.scores || {};

  async function loadRewrites() {
    if (!sessionId) return;
    setRewritesLoading(true);
    setRewritesError(null);
    try {
      const rewrites = await api.fetchRewrites(sessionId);
      setSession((prev) =>
        prev ? { ...prev, feedback: { ...prev.feedback!, rewrites } } : prev
      );
    } catch (e) {
      const msg = parseApiError(e);
      setRewritesError(msg);
      toast("error", "Couldn't load rewrites", msg);
    } finally {
      setRewritesLoading(false);
    }
  }

  async function loadTip() {
    if (!sessionId) return;
    setTipLoading(true);
    setTipError(null);
    try {
      const { coaching_tip } = await api.fetchCoachingTip(sessionId);
      setSession((prev) =>
        prev ? { ...prev, feedback: { ...prev.feedback!, coaching_tip } } : prev
      );
    } catch (e) {
      const msg = parseApiError(e);
      setTipError(msg);
      toast("error", "Couldn't load tip", msg);
    } finally {
      setTipLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-64 w-full rounded-xl" />
        <div className="grid sm:grid-cols-2 gap-4">
          <Skeleton className="h-20 rounded-lg" />
          <Skeleton className="h-20 rounded-lg" />
        </div>
      </div>
    );
  }

  if (error) return <ErrorBanner message={error} />;
  if (!session || !feedback) return <p className="text-slate">No feedback yet.</p>;

  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold text-ink">Session Feedback</h1>

      <div className="rounded-xl border bg-white p-6">
        <ScoreRadar scores={scores} />
      </div>

      <div className="grid sm:grid-cols-2 gap-4">
        {(feedback.grammar_notes || []).map((note) => (
          <div key={note} className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            {note}
          </div>
        ))}
        {(feedback.misinterpretation_warnings || []).map((warn) => (
          <div key={warn} className="rounded-lg border border-orange-200 bg-orange-50 p-4 text-sm text-orange-900">
            {warn}
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          type="button"
          onClick={loadRewrites}
          disabled={rewritesLoading || !!feedback.rewrites}
          className="px-4 py-2 rounded-lg bg-accent text-white font-medium disabled:opacity-60"
        >
          {rewritesLoading ? "Loading…" : "Show stronger versions"}
        </button>
        <button
          type="button"
          onClick={loadTip}
          disabled={tipLoading || !!feedback.coaching_tip}
          className="px-4 py-2 rounded-lg border border-slate-300 font-medium disabled:opacity-60"
        >
          {tipLoading ? "Loading…" : "Coach's tip"}
        </button>
      </div>

      <ErrorBanner message={rewritesError} onDismiss={() => setRewritesError(null)} />
      <ErrorBanner message={tipError} onDismiss={() => setTipError(null)} />

      {feedback.rewrites && (
        <div className="grid sm:grid-cols-2 gap-4">
          <div className="rounded-xl border bg-white p-5">
            <h3 className="font-semibold text-ink mb-2">Assertive</h3>
            <p className="text-slate text-sm leading-relaxed">{feedback.rewrites.assertive}</p>
          </div>
          <div className="rounded-xl border bg-white p-5">
            <h3 className="font-semibold text-ink mb-2">Diplomatic</h3>
            <p className="text-slate text-sm leading-relaxed">{feedback.rewrites.diplomatic}</p>
          </div>
        </div>
      )}

      {feedback.coaching_tip && (
        <div className="rounded-xl border border-blue-200 bg-blue-50 p-5">
          <h3 className="font-semibold text-ink mb-2">Coach&apos;s tip</h3>
          <p className="text-slate">{feedback.coaching_tip}</p>
        </div>
      )}

      <div className="flex gap-4 pt-4">
        <Link href={`/practice?styleId=${session.styleProfileId}`} className="px-4 py-2 rounded-lg bg-accent text-white font-medium">
          Try again with same style
        </Link>
        <Link href="/library" className="px-4 py-2 rounded-lg border font-medium">
          Browse all styles
        </Link>
        <Link href="/progress" className="px-4 py-2 rounded-lg border font-medium">
          View progress
        </Link>
      </div>
    </div>
  );
}

export default function FeedbackPage() {
  return (
    <Suspense fallback={
      <div className="space-y-6 max-w-4xl mx-auto">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    }>
      <FeedbackContent />
    </Suspense>
  );
}
