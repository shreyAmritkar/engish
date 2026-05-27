"use client";

import { useEffect, useRef, useState } from "react";
import { api, ConversationTurn, Session } from "@/lib/api";

const MAX_USER_TURNS = 5;

interface ConversationViewProps {
  session: Session;
  onFinished: (sessionId: string) => void;
}

export function ConversationView({ session, onFinished }: ConversationViewProps) {
  const [turns, setTurns] = useState<ConversationTurn[]>(
    session.conversationHistory ?? []
  );
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const [maxReached, setMaxReached] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const userTurnCount = turns.filter((t) => t.role === "user").length;
  const requiredWord = session.requiredWord;
  const wordUsed = turns
    .filter((t) => t.role === "user")
    .some((t) => t.text.toLowerCase().includes(requiredWord?.toLowerCase() ?? ""));

  // Restore max-reached state if the session was loaded mid-conversation
  useEffect(() => {
    if (userTurnCount >= MAX_USER_TURNS) setMaxReached(true);
  }, [userTurnCount]);

  // Scroll to the latest message
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [turns, sending]);

  async function handleSend() {
    const message = draft.trim();
    if (!message || sending || maxReached) return;

    setSending(true);
    setError(null);

    // Optimistically show the user's message
    const optimisticUser: ConversationTurn = {
      role: "user",
      text: message,
      timestamp: new Date().toISOString(),
    };
    setTurns((prev) => [...prev, optimisticUser]);
    setDraft("");

    try {
      const result = await api.addTurn(session.id, message);
      // Replace optimistic with server-confirmed history
      setTurns(result.history);
      setMaxReached(result.maxReached);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to send — please retry.");
      // Roll back the optimistic message
      setTurns((prev) => prev.filter((t) => t !== optimisticUser));
      setDraft(message);
    } finally {
      setSending(false);
      textareaRef.current?.focus();
    }
  }

  async function handleFinish() {
    if (userTurnCount === 0) return;
    setFinishing(true);
    setError(null);
    try {
      await api.finishConversation(session.id);
      onFinished(session.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to score. Try again.");
      setFinishing(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Conversation transcript */}
      <div className="rounded-xl border bg-white overflow-y-auto max-h-[480px] p-4 flex flex-col gap-3">
        {turns.length === 0 && (
          <p className="text-slate text-sm text-center py-8">
            Start the conversation — type your first response below.
          </p>
        )}

        {turns.map((turn, i) => (
          <ChatBubble key={i} turn={turn} />
        ))}

        {/* Typing indicator while waiting for character reply */}
        {sending && (
          <div className="flex items-end gap-2">
            <CharacterAvatar />
            <div className="bg-slate-100 rounded-2xl rounded-bl-sm px-4 py-3">
              <TypingDots />
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Status bar */}
      <div className="flex items-center justify-between text-sm text-slate">
        <span>
          Turn{" "}
          <span className="font-semibold text-ink">{userTurnCount}</span>{" "}
          of {MAX_USER_TURNS}
        </span>

        <span
          className={`px-3 py-1 rounded-full text-xs font-medium ${
            wordUsed
              ? "bg-green-100 text-green-800"
              : "bg-amber-50 text-amber-800"
          }`}
        >
          {wordUsed ? "✓" : "○"} required word: <em>{requiredWord}</em>
        </span>
      </div>

      {/* Error */}
      {error && (
        <p className="text-red-600 text-sm">{error}</p>
      )}

      {/* Input area */}
      {!maxReached ? (
        <div className="flex gap-3 items-end">
          <textarea
            ref={textareaRef}
            rows={3}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type your response… (Enter to send, Shift+Enter for new line)"
            disabled={sending}
            className="flex-1 border rounded-xl px-4 py-3 bg-white resize-none text-sm disabled:opacity-60"
          />
          <button
            type="button"
            onClick={handleSend}
            disabled={sending || draft.trim().length === 0}
            className="px-5 py-3 rounded-xl bg-accent text-white font-semibold disabled:opacity-50 self-end"
          >
            Send
          </button>
        </div>
      ) : (
        <p className="text-sm text-slate text-center bg-slate-50 rounded-xl p-3">
          You&apos;ve reached the turn limit — finish the conversation to see your score.
        </p>
      )}

      {/* Finish button — visible once at least one turn has been sent */}
      {userTurnCount > 0 && (
        <div className="flex gap-3 pt-1">
          <button
            type="button"
            onClick={handleFinish}
            disabled={finishing}
            className="px-6 py-3 rounded-xl bg-accent text-white font-semibold disabled:opacity-50"
          >
            {finishing ? "Scoring…" : "Finish & score"}
          </button>
          <p className="text-xs text-slate self-center">
            You can also keep going — score whenever you feel the conversation is complete.
          </p>
        </div>
      )}
    </div>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────────

function ChatBubble({ turn }: { turn: ConversationTurn }) {
  const isUser = turn.role === "user";

  return (
    <div className={`flex items-end gap-2 ${isUser ? "flex-row-reverse" : ""}`}>
      {!isUser && <CharacterAvatar />}

      <div
        className={`max-w-[75%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? "bg-accent text-white rounded-br-sm"
            : "bg-slate-100 text-ink rounded-bl-sm"
        }`}
      >
        {turn.text}
      </div>

      {isUser && (
        <div className="w-7 h-7 rounded-full bg-accent flex items-center justify-center text-white text-xs font-bold shrink-0">
          You
        </div>
      )}
    </div>
  );
}

function CharacterAvatar() {
  return (
    <div className="w-7 h-7 rounded-full bg-slate-300 flex items-center justify-center text-xs font-bold text-white shrink-0">
      OP
    </div>
  );
}

function TypingDots() {
  return (
    <span className="flex gap-1 items-center h-4">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce"
          style={{ animationDelay: `${i * 150}ms` }}
        />
      ))}
    </span>
  );
}