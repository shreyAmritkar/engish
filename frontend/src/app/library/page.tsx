"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, StyleProfile } from "@/lib/api";

export default function StyleLibraryPage() {
  const [styles, setStyles] = useState<StyleProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDesc, setCreateDesc] = useState("");
  const [extracted, setExtracted] = useState<StyleProfile | null>(null);
  const [creating, setCreating] = useState(false);

  const [showCommunity, setShowCommunity] = useState(false);
  const [characterName, setCharacterName] = useState("");
  const [excerpts, setExcerpts] = useState("");
  const [communityStatus, setCommunityStatus] = useState<string | null>(null);

  useEffect(() => {
    api
      .getStyles()
      .then(setStyles)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  async function handleCreateStyle(e: React.FormEvent) {
    e.preventDefault();
    setCreating(true);
    setError(null);
    try {
      const profile = await api.createStyleFromDescription(createName, createDesc);
      setExtracted(profile);
      setStyles((prev) => [profile, ...prev.filter((s) => s.id !== profile.id)]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create style");
    } finally {
      setCreating(false);
    }
  }

  async function handleCommunitySubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const lines = excerpts.split("\n").map((l) => l.trim()).filter(Boolean);
      const result = await api.submitCommunityStyle(characterName, lines);
      setCommunityStatus(`Submitted "${characterName}" — status: ${result.status}`);
      setShowCommunity(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Community submit failed");
    }
  }

  if (loading) {
    return <p className="text-slate">Loading styles…</p>;
  }

  return (
    <div className="space-y-8">
      <section>
        <h1 className="text-3xl font-bold text-ink">Style Library</h1>
        <p className="text-slate mt-2">
          Pick a communication style to practice, or create your own from a description or character dialogue.
        </p>
        <div className="mt-4 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={() => setShowCreate(true)}
            className="px-4 py-2 rounded-lg bg-accent text-white font-medium hover:bg-blue-700"
          >
            Create your own
          </button>
          <button
            type="button"
            onClick={() => setShowCommunity(true)}
            className="px-4 py-2 rounded-lg border border-slate-300 text-ink font-medium hover:bg-white"
          >
            Submit community style
          </button>
        </div>
      </section>

      {error && (
        <div className="rounded-lg bg-red-50 border border-red-200 text-red-800 px-4 py-3 text-sm">
          {error}
        </div>
      )}
      {communityStatus && (
        <div className="rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-900 px-4 py-3 text-sm">
          {communityStatus}
        </div>
      )}

      <section className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {styles.map((style) => (
          <article
            key={style.id}
            className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm hover:shadow-md transition"
          >
            <h2 className="font-semibold text-lg text-ink">{style.name}</h2>
            <div className="mt-2 flex flex-wrap gap-2 text-xs">
              <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate">
                {style.powerDynamic}
              </span>
              <span className="px-2 py-0.5 rounded-full bg-blue-50 text-accent">
                Formal {style.formalityLevel}/10
              </span>
              {style.communityVotes > 0 && (
                <span className="px-2 py-0.5 rounded-full bg-amber-50 text-warning">
                  {style.communityVotes} votes
                </span>
              )}
            </div>
            <p className="text-sm text-slate mt-3 line-clamp-2">
              {style.keyPatterns?.slice(0, 2).join(" · ") || "Practice this style"}
            </p>
            <Link
              href={`/practice?styleId=${style.id}`}
              className="mt-4 inline-block text-sm font-medium text-accent hover:underline"
            >
              Practice →
            </Link>
          </article>
        ))}
      </section>

      {showCreate && (
        <Modal title="Create style from description" onClose={() => setShowCreate(false)}>
          <form onSubmit={handleCreateStyle} className="space-y-4">
            <input
              className="w-full border rounded-lg px-3 py-2"
              placeholder="Style name (e.g. Harvey Specter)"
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              required
            />
            <textarea
              className="w-full border rounded-lg px-3 py-2 min-h-[120px]"
              placeholder="Describe the style or paste character dialogue…"
              value={createDesc}
              onChange={(e) => setCreateDesc(e.target.value)}
              required
            />
            <button
              type="submit"
              disabled={creating}
              className="px-4 py-2 rounded-lg bg-accent text-white font-medium disabled:opacity-60"
            >
              {creating ? "Extracting…" : "Extract style"}
            </button>
            {extracted && (
              <div className="rounded-lg bg-surface border p-4 text-sm space-y-2">
                <p className="font-medium text-ink">Extracted summary</p>
                <p>
                  {extracted.powerDynamic} · Formal {extracted.formalityLevel} ·{" "}
                  {extracted.sentenceStructure}
                </p>
                <p className="text-slate">{extracted.keyPatterns?.join(", ")}</p>
                <Link
                  href={`/practice?styleId=${extracted.id}`}
                  className="inline-block text-accent font-medium"
                >
                  Use this style →
                </Link>
              </div>
            )}
          </form>
        </Modal>
      )}

      {showCommunity && (
        <Modal title="Submit community style" onClose={() => setShowCommunity(false)}>
          <form onSubmit={handleCommunitySubmit} className="space-y-4">
            <input
              className="w-full border rounded-lg px-3 py-2"
              placeholder="Character name"
              value={characterName}
              onChange={(e) => setCharacterName(e.target.value)}
              required
            />
            <textarea
              className="w-full border rounded-lg px-3 py-2 min-h-[140px]"
              placeholder="Paste dialogue excerpts (one per line)"
              value={excerpts}
              onChange={(e) => setExcerpts(e.target.value)}
              required
            />
            <button
              type="submit"
              className="px-4 py-2 rounded-lg bg-accent text-white font-medium"
            >
              Submit for approval
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}

function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl max-w-lg w-full p-6 shadow-xl">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-semibold">{title}</h3>
          <button type="button" onClick={onClose} className="text-slate hover:text-ink">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
