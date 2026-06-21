/**
 * Parses an error thrown by `api.*` calls.
 * Backend sends:  { status, error, message, details[], path, timestamp }
 * Falls back gracefully for network errors, timeouts, etc.
 */
export function parseApiError(err: unknown): string {
  if (!err) return "An unexpected error occurred.";

  // Already a plain string
  if (typeof err === "string") return err;

  if (err instanceof Error) {
    const raw = err.message;

    // Try to parse backend JSON error body
    try {
      const parsed = JSON.parse(raw);
      if (parsed?.message) {
        const base = parsed.message as string;
        const details: string[] = parsed.details ?? [];
        return details.length > 0
          ? `${base}: ${details.join(", ")}`
          : base;
      }
    } catch {
      // Not JSON — use raw message
    }

    // Map common HTTP status phrases to friendlier messages
    if (raw.includes("401") || raw.toLowerCase().includes("unauthorized")) {
      return "Your session has expired. Please sign in again.";
    }
    if (raw.includes("403") || raw.toLowerCase().includes("forbidden")) {
      return "You don't have permission to do that.";
    }
    if (raw.includes("404")) {
      return "The requested resource was not found.";
    }
    if (raw.includes("409") || raw.toLowerCase().includes("conflict")) {
      return "That email is already registered.";
    }
    if (raw.includes("429")) {
      return "You've hit the daily limit. Try again tomorrow.";
    }
    if (raw.includes("503") || raw.toLowerCase().includes("unavailable")) {
      return "The AI service is temporarily unavailable. Please try again shortly.";
    }
    if (raw.toLowerCase().includes("failed to fetch") || raw.toLowerCase().includes("network")) {
      return "Network error — check your connection and try again.";
    }

    return raw;
  }

  return "An unexpected error occurred.";
}
