"use client";

import {
  PolarAngleAxis,
  PolarGrid,
  Radar,
  RadarChart,
  ResponsiveContainer,
} from "recharts";

const DIMENSIONS = [
  "confidence",
  "tone",
  "persuasion",
  "emotional_control",
  "professionalism",
  "style_match",
];

type Props = {
  scores: Record<string, number>;
};

export function ScoreRadar({ scores }: Props) {
  const data = DIMENSIONS.map((key) => ({
    dimension: key.replace("_", " "),
    score: scores[key] ?? 0,
  }));

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <RadarChart data={data}>
          <PolarGrid />
          <PolarAngleAxis dataKey="dimension" tick={{ fontSize: 11 }} />
          <Radar
            name="Score"
            dataKey="score"
            stroke="#2563eb"
            fill="#2563eb"
            fillOpacity={0.35}
          />
        </RadarChart>
      </ResponsiveContainer>
    </div>
  );
}
