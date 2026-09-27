"use client";

import { useEffect, useState } from "react";
import { api, ProgressData, TrendResult } from "@/lib/api";
import { ScoreRadar } from "@/components/ScoreRadar";
import { TrendCard } from "@/components/TrendCard";
import { useAuth } from "@/hooks/useAuth";
import { parseApiError } from "@/lib/errors";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Skeleton } from "@/components/ui/Skeleton";

const LEVEL_LABELS = ["Novice", "Developing", "Competent", "Advanced", "Expert"];

export default function ProgressDashboard() {
  const { ready } = useAuth();
  const [data, setData] = useState<ProgressData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [trend, setTrend] = useState<TrendResult | null>(null);

  useEffect(() => {
    if (!ready) return;
    api.getProgress()
      .then(setData)
      .catch((e) => setError(parseApiError(e)))
      .finally(() => setLoading(false));

    // Fetched independently on purpose: this is a nice-to-have on top of
    // the core progress page, so a failure here should never block or
    // error out the rest of the dashboard.
    api.getTrend()
      .then(setTrend)
      .catch(() => setTrend(null));
  }, [ready]);

  if (!ready) return null;

  if (loading) {
    return (
      <div className="space-y-8">
        <div>
          <Skeleton className="h-9 w-48" />
          <Skeleton className="h-4 w-32 mt-2" />
          <Skeleton className="h-3 max-w-md mt-4 rounded-full" />
        </div>
        <Skeleton className="h-64 w-full rounded-xl" />
        <div className="grid sm:grid-cols-2 gap-4">
          <Skeleton className="h-32 rounded-xl" />
          <Skeleton className="h-32 rounded-xl" />
        </div>
      </div>
    );
  }

  if (error) return <ErrorBanner message={error} onDismiss={() => setError(null)} />;
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

      {trend && <TrendCard trend={trend} />}

      {Object.keys(avgScores).length > 0 && (
        <section className="rounded-xl border bg-white p-6">
          <h2 className="font-semibold text-ink mb-4">Average scores</h2>
          <ScoreRadar scores={avgScores} />
        </section>
      )}

      <div className="grid sm:grid-cols-2 gap-4">
        {data.strongAreas.length > 0 && (
          <div className="rounded-xl border border-green-200 bg-green-50 p-4">
            <h3 className="font-semibold text-green-900 mb-2">Strengths</h3>
            <ul className="space-y-1">
              {data.strongAreas.map((a) => (
                <li key={a} className="text-sm text-green-800">✓ {a}</li>
              ))}
            </ul>
          </div>
        )}
        {data.weakAreas.length > 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
            <h3 className="font-semibold text-amber-900 mb-2">Areas to improve</h3>
            <ul className="space-y-1">
              {data.weakAreas.map((a) => (
                <li key={a} className="text-sm text-amber-800">→ {a}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {data.recentSessions.length > 0 && (
        <section>
          <h2 className="font-semibold text-ink mb-3">Recent sessions</h2>
          <div className="space-y-2">
            {data.recentSessions.map((s) => (
              <div key={s.id} className="rounded-lg border bg-white px-4 py-3 text-sm flex justify-between items-center">
                <span className="text-slate line-clamp-1 flex-1">{s.situation}</span>
                {s.feedback?.scores && (
                  <span className="ml-4 text-xs font-medium text-accent flex-shrink-0">
                    avg {Math.round(Object.values(s.feedback.scores).reduce((a, b) => a + b, 0) / Object.values(s.feedback.scores).length)}
                  </span>
                )}
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
