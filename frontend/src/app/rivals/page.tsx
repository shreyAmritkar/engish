"use client";

import { useEffect, useState } from "react";
import { api, RivalEntry } from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { parseApiError } from "@/lib/errors";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Skeleton } from "@/components/ui/Skeleton";

export default function RivalsPage() {
  const { ready } = useAuth();
  const [rivals, setRivals] = useState<RivalEntry[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    api.getRivals()
      .then(setRivals)
      .catch((e) => setError(parseApiError(e)))
      .finally(() => setLoading(false));
  }, [ready]);

  if (!ready) return null;

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-4 w-64 mt-2" />
        <div className="space-y-2 mt-4">
          <Skeleton className="h-12 w-full rounded-lg" />
          <Skeleton className="h-12 w-full rounded-lg" />
          <Skeleton className="h-12 w-full rounded-lg" />
        </div>
      </div>
    );
  }

  if (error) return <ErrorBanner message={error} onDismiss={() => setError(null)} />;

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-3xl font-bold text-ink">Rivals</h1>
        <p className="text-slate mt-1">
          Other users who score on the same dimensions as you, ranked by their best shared score.
        </p>
      </section>

      {!rivals || rivals.length === 0 ? (
        <p className="text-slate text-sm">
          No rivals yet — practice a few sessions and check back tomorrow.
        </p>
      ) : (
        <ol className="space-y-2">
          {rivals.map((r, i) => (
            <li
              key={r.rivalUserId}
              className="rounded-lg border bg-white px-4 py-3 text-sm flex items-center justify-between"
            >
              <span className="flex items-center gap-3">
                <span className="text-slate font-medium w-5">{i + 1}</span>
                <span className="text-ink font-medium">{r.email}</span>
                <span className="text-xs text-slate bg-slate-100 px-2 py-0.5 rounded-full">
                  {r.dimension.replace("_", " ")}
                </span>
              </span>
              <span className="text-accent font-semibold">{Math.round(r.sharedBestScore)}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
