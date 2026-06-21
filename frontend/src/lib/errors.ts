/**
 * Parses an error thrown by `api.*` calls.
 * Backend sends:  { status, error, message, details[], path, timestamp }
 * Falls back gracefully for network errors, timeouts, etc.
 */
export function parseApiError(err: unknown): string {
  if (!err) return "An unexpected error occurred.";

  if (typeof err === "string") return err;

  if (err instanceof Error) {
    const raw = err.message;

    // Try to parse backend JSON error body embedded in message
    try {
      const parsed = JSON.parse(raw);
      if (parsed?.message) {
        const base = parsed.message as string;
        const details: string[] = parsed.details ?? [];
        return details.length > 0 ? `${base}: ${details.join(", ")}` : base;
      }
    } catch {
      // Not JSON — use raw message
    }

    if (raw.includes("401") || raw.toLowerCase().includes("unauthorized") ||
        raw.toLowerCase().includes("session expired")) {
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
    if (raw.includes("422") || raw.toLowerCase().includes("unprocessable")) {
      // Intent check nudge — return as-is, shown inline not as generic error
      return raw;
    }
    if (raw.includes("429")) {
      return "You've hit the daily limit. Try again tomorrow.";
    }
    if (raw.includes("500")) {
      return "Something went wrong on our end. Please try again.";
    }
    if (raw.includes("503") || raw.toLowerCase().includes("unavailable")) {
      return "The AI service is temporarily unavailable. Please try again shortly.";
    }
    if (raw.toLowerCase().includes("failed to fetch") ||
        raw.toLowerCase().includes("networkerror") ||
        raw.toLowerCase().includes("network request failed")) {
      return "Network error — check your connection and try again.";
    }
    if (raw.toLowerCase().includes("timeout") || raw.toLowerCase().includes("timed out")) {
      return "The request timed out. Please try again.";
    }

    return raw;
  }

  return "An unexpected error occurred.";
}

/**
 * Returns true if the error is something the user can retry
 * (network blip, 503) vs something they can't (403, 404).
 */
export function isRetryable(err: unknown): boolean {
  if (!(err instanceof Error)) return false;
  const raw = err.message.toLowerCase();
  return raw.includes("503") ||
         raw.includes("network") ||
         raw.includes("failed to fetch") ||
         raw.includes("timeout");
}