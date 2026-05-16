"use client";

import { useEffect, useState } from "react";
import { api, ProgressData } from "@/lib/api";
import { ScoreRadar } from "@/components/ScoreRadar";

const LEVEL_LABELS = ["Novice", "Developing", "Competent", "Advanced", "Expert"];

export default function ProgressDashboard() {
  const [data, setData] = useState<ProgressData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .getProgress()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-slate">Loading progress…</p>;
  if (error) return <p className="text-red-600">{error}</p>;
  if (!data) return null;

  const level = data.currentLevel;
  const levelLabel = LEVEL_LABELS[Math.min(level - 1, LEVEL_LABELS.length - 1)] || "Novice";
  const xpPercent = Math.min(100, (data.totalSessions % 10) * 10);

  const avgScores = Object.fromEntries(
    Object.entries(data.averageScores || {}).map(([k, v]) => [k, Math.round(v)])
  );

  return (
    <div className="space-y-8">
      <section>
        <h1 className="text-3xl font-bold text-ink">Your Progress</h1>
        <p className="text-slate mt-1">Level {level} — {levelLabel}</p>
        <div className="mt-4 h-3 rounded-full bg-slate-200 overflow-hidden max-w-md">
          <div className="h-full bg-accent transition-all" style={{ width: `${xpPercent}%` }} />
        </div>
        <p className="text-xs text-slate mt-1">{data.totalSessions} sessions completed</p>
      </section>

      {Object.keys(avgScores).length > 0 && (
        <section className="rounded-xl border bg-white p-6 max-w-xl">
          <h2 className="font-semibold mb-4">Overall scores</h2>
          <ScoreRadar scores={avgScores} />
        </section>
      )}

      {data.habitFlags?.length > 0 && (
        <section className="grid sm:grid-cols-2 gap-4">
          {data.habitFlags.map((flag) => (
            <div key={flag} className="rounded-xl border-l-4 border-accent bg-white p-4 shadow-sm">
              <p className="text-sm font-medium text-ink">Insight</p>
              <p className="text-slate mt-1">{flag}</p>
            </div>
          ))}
        </section>
      )}

      <section className="grid sm:grid-cols-2 gap-6">
        <div>
          <h3 className="font-semibold text-ink mb-2">Weak areas</h3>
          <ul className="list-disc list-inside text-slate text-sm space-y-1">
            {(data.weakAreas?.length ? data.weakAreas : ["None yet"]).map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="font-semibold text-ink mb-2">Strong areas</h3>
          <ul className="list-disc list-inside text-slate text-sm space-y-1">
            {(data.strongAreas?.length ? data.strongAreas : ["Keep practicing"]).map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </div>
      </section>

      <section>
        <h2 className="font-semibold text-ink mb-4">Recent sessions</h2>
        <div className="space-y-3">
          {data.recentSessions?.length ? (
            data.recentSessions.map((s) => {
              const avg = s.feedback?.scores
                ? Math.round(
                    Object.values(s.feedback.scores).reduce((a, b) => a + b, 0) /
                      Object.values(s.feedback.scores).length
                  )
                : null;
              return (
                <article key={s.id} className="rounded-lg border bg-white p-4 text-sm">
                  <p className="text-ink font-medium line-clamp-1">{s.situation}</p>
                  <p className="text-slate mt-1">
                    {avg != null ? `Avg score: ${avg}` : "No scores"} · {s.createdAt?.slice(0, 10)}
                  </p>
                </article>
              );
            })
          ) : (
            <p className="text-slate text-sm">No sessions yet. Start practicing from the library.</p>
          )}
        </div>
      </section>
    </div>
  );
}
