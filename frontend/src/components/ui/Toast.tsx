"use client";

import React, { createContext, useCallback, useContext, useState } from "react";

// ── Types ──────────────────────────────────────────────────────────────────────

type ToastType = "error" | "success" | "warning" | "info";

interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
}

interface ToastContextValue {
  toasts: Toast[];
  toast: (type: ToastType, title: string, message?: string) => void;
  dismiss: (id: string) => void;
}

// ── Context ───────────────────────────────────────────────────────────────────

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const toast = useCallback((type: ToastType, title: string, message?: string) => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, type, title, message }]);
    // Auto-dismiss after 5s (errors stay longer)
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, type === "error" ? 7000 : 4000);
  }, []);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, toast, dismiss }}>
      {children}
      <ToastRenderer toasts={toasts} dismiss={dismiss} />
    </ToastContext.Provider>
  );
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}

// ── Renderer ──────────────────────────────────────────────────────────────────

const icons: Record<ToastType, string> = {
  error:   "✕",
  success: "✓",
  warning: "⚠",
  info:    "ℹ",
};

const styles: Record<ToastType, string> = {
  error:   "bg-red-50 border-red-200 text-red-900",
  success: "bg-green-50 border-green-200 text-green-900",
  warning: "bg-amber-50 border-amber-200 text-amber-900",
  info:    "bg-blue-50 border-blue-200 text-blue-900",
};

const iconStyles: Record<ToastType, string> = {
  error:   "text-red-500",
  success: "text-green-500",
  warning: "text-amber-500",
  info:    "text-blue-500",
};

function ToastRenderer({ toasts, dismiss }: { toasts: Toast[]; dismiss: (id: string) => void }) {
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`flex items-start gap-3 rounded-xl border px-4 py-3 shadow-lg pointer-events-auto transition-all ${styles[t.type]}`}
        >
          <span className={`text-sm font-bold mt-0.5 ${iconStyles[t.type]}`}>{icons[t.type]}</span>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold">{t.title}</p>
            {t.message && <p className="text-xs mt-0.5 opacity-80">{t.message}</p>}
          </div>
          <button
            onClick={() => dismiss(t.id)}
            className="text-xs opacity-50 hover:opacity-100 ml-1 flex-shrink-0"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
