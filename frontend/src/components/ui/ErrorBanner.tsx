interface ErrorBannerProps {
  message: string | null;
  onDismiss?: () => void;
  className?: string;
}

export function ErrorBanner({ message, onDismiss, className = "" }: ErrorBannerProps) {
  if (!message) return null;
  return (
    <div className={`flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900 ${className}`}>
      <span className="font-bold text-red-500 mt-0.5 flex-shrink-0">✕</span>
      <p className="flex-1">{message}</p>
      {onDismiss && (
        <button onClick={onDismiss} className="text-red-400 hover:text-red-700 flex-shrink-0">✕</button>
      )}
    </div>
  );
}
