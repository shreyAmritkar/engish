"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { api, Session } from "@/lib/api";
import { ScoreRadar } from "@/components/ScoreRadar";

function FeedbackContent() {
  const params = useSearchParams();
  const sessionId = params.get("sessionId");

  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rewritesLoading, setRewritesLoading] = useState(false);
  const [tipLoading, setTipLoading] = useState(false);

  useEffect(() => {
    if (!sessionId) {
      setError("Missing session");
      setLoading(false);
      return;
    }
    api
      .getSession(sessionId)
      .then(setSession)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [sessionId]);

  const feedback = session?.feedback;
  const scores = feedback?.scores || {};

  async function loadRewrites() {
    if (!sessionId) return;
    setRewritesLoading(true);
    try {
      const rewrites = await api.fetchRewrites(sessionId);
      setSession((prev) =>
        prev ? { ...prev, feedback: { ...prev.feedback!, rewrites } } : prev
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load rewrites");
    } finally {
      setRewritesLoading(false);
    }
  }

  async function loadTip() {
    if (!sessionId) return;
    setTipLoading(true);
    try {
      const { coaching_tip } = await api.fetchCoachingTip(sessionId);
      setSession((prev) =>
        prev ? { ...prev, feedback: { ...prev.feedback!, coaching_tip } } : prev
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load tip");
    } finally {
      setTipLoading(false);
    }
  }

  if (loading) return <p className="text-slate">Loading feedback…</p>;
  if (error && !session) return <p className="text-red-600">{error}</p>;
  if (!session || !feedback) return <p className="text-slate">No feedback yet.</p>;

  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold text-ink">Session Feedback</h1>

      {/* Scores */}
      <div className="rounded-xl border bg-white p-6">
        <ScoreRadar scores={scores} />
      </div>

      {/* Grammar / warning notes */}
      <div className="grid sm:grid-cols-2 gap-4">
        {(feedback.grammar_notes || []).map((note) => (
          <div
            key={note}
            className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
          >
            {note}
          </div>
        ))}
        {(feedback.misinterpretation_warnings || []).map((warn) => (
          <div
            key={warn}
            className="rounded-lg border border-orange-200 bg-orange-50 p-4 text-sm text-orange-900"
          >
            {warn}
          </div>
        ))}
      </div>

      {/* Action buttons */}
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

      {/* Rewrites */}
      {feedback.rewrites && (
        <div className="grid sm:grid-cols-2 gap-4">
          <div className="rounded-xl border bg-white p-5">
            <h3 className="font-semibold text-ink mb-2">Assertive</h3>
            <p className="text-slate text-sm leading-relaxed">
              {feedback.rewrites.assertive}
            </p>
          </div>
          <div className="rounded-xl border bg-white p-5">
            <h3 className="font-semibold text-ink mb-2">Diplomatic</h3>
            <p className="text-slate text-sm leading-relaxed">
              {feedback.rewrites.diplomatic}
            </p>
          </div>
        </div>
      )}

      {/* Coach tip */}
      {feedback.coaching_tip && (
        <div className="rounded-xl border border-blue-200 bg-blue-50 p-5">
          <h3 className="font-semibold text-ink mb-2">Coach&apos;s tip</h3>
          <p className="text-slate">{feedback.coaching_tip}</p>
        </div>
      )}

      {/* Navigation */}
      <div className="flex gap-4 pt-4">
        <Link
          href={`/practice?styleId=${session.styleProfileId}`}
          className="px-4 py-2 rounded-lg bg-accent text-white font-medium"
        >
          Try again
        </Link>
        <Link href="/library" className="px-4 py-2 rounded-lg border font-medium">
          Try new style
        </Link>
      </div>
    </div>
  );
}

export default function FeedbackPage() {
  return (
    <Suspense fallback={<p>Loading…</p>}>
      <FeedbackContent />
    </Suspense>
  );
}
