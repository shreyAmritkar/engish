import { DimensionTrend, TrendResult } from "@/lib/api";

const DIMENSION_DISPLAY: Record<string, string> = {
  confidence: "Confidence",
  tone: "Tone",
  persuasion: "Persuasion",
  emotional_control: "Emotional control",
  professionalism: "Professionalism",
  style_match: "Style match",
};

function directionStyles(direction: DimensionTrend["direction"]) {
  switch (direction) {
    case "IMPROVED":
      return { dot: "bg-green-500", text: "text-green-800", glyph: "↑" };
    case "DECLINED":
      return { dot: "bg-amber-500", text: "text-amber-800", glyph: "↓" };
    default:
      return { dot: "bg-slate-400", text: "text-slate", glyph: "→" };
  }
}

export function TrendCard({ trend }: { trend: TrendResult }) {
  if (!trend.hasEnoughHistory) {
    return (
      <section className="rounded-xl border border-slate-200 bg-white p-6">
        <p className="text-ink text-lg leading-relaxed">{trend.headline}</p>
        <div className="flex items-center gap-1.5 mt-4">
          {Array.from({ length: trend.sessionsNeeded }).map((_, i) => (
            <span
              key={i}
              className={`h-1.5 w-1.5 rounded-full ${
                i < trend.sessionCount ? "bg-accent" : "bg-slate-200"
              }`}
            />
          ))}
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6">
      <p className="text-ink text-lg leading-relaxed">{trend.headline}</p>

      {trend.dimensions.length > 0 && (
        <div className="mt-5 pt-5 border-t border-slate-100 flex flex-wrap gap-x-6 gap-y-2">
          {trend.dimensions.map((d) => {
            const s = directionStyles(d.direction);
            const label = DIMENSION_DISPLAY[d.dimension] ?? d.dimension;
            return (
              <span key={d.dimension} className="flex items-center gap-1.5 text-xs text-slate">
                <span className={`h-1.5 w-1.5 rounded-full ${s.dot}`} />
                {label}
                {d.direction !== "STEADY" && (
                  <span className={`font-medium ${s.text}`}>{s.glyph}</span>
                )}
              </span>
            );
          })}
        </div>
      )}
    </section>
  );
}
