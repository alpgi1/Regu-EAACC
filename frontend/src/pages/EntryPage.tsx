/**
 * EntryPage - renders at /app.
 *
 * Shows a dimmed background with a single short paragraph,
 * and the EntryModal on top. The modal cannot be dismissed.
 */

import EntryModal from "@/components/analysis/EntryModal";

export default function EntryPage() {
  return (
    <div className="min-h-screen bg-[var(--color-regu-bg)] flex items-center justify-center">
      <main
        id="main-content"
        className="relative w-full max-w-md px-6 text-center"
      >
        <p className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed">
          Start a new EU AI Act analysis. Takes about 5 minutes for a
          first-pass classification.
        </p>
      </main>
      <EntryModal />
    </div>
  );
}
