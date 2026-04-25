/**
 * EntryModal — the risk-level gate shown on /app.
 *
 * Cannot be dismissed: no X button, no Esc, no click-outside.
 * Two paths:
 *   1. "I don't know" → POST /interviews → navigate to stage1
 *   2. "I already know" → POST /interviews + POST /skip-to-stage2 → navigate to stage2
 */

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useStartInterview, useSkipToStage2 } from "@/lib/queries";
import { RISK_TIERS } from "@/lib/constants";
import { getErrorMessage } from "@/lib/api";

export default function EntryModal() {
  const navigate = useNavigate();
  const startInterview = useStartInterview();
  const skipToStage2 = useSkipToStage2();

  const [pendingPath, setPendingPath] = useState<"classify" | "high" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tierOpen, setTierOpen] = useState(false);

  async function handleClassify() {
    setPendingPath("classify");
    setError(null);
    try {
      const result = await startInterview.mutateAsync();
      const sid = result.sessionId;
      sessionStorage.setItem("regu-session", sid);
      // Store the first question so Stage1Page can render immediately
      if (result.firstQuestion) {
        sessionStorage.setItem("regu-first-question", JSON.stringify(result.firstQuestion));
      }
      navigate(`/app/session/${sid}/stage1`);
    } catch (err) {
      setError(getErrorMessage(err, "Failed to start analysis. Please try again."));
      setPendingPath(null);
    }
  }

  async function handleHighRisk() {
    setPendingPath("high");
    setError(null);
    try {
      const result = await startInterview.mutateAsync();
      const sid = result.sessionId;
      sessionStorage.setItem("regu-session", sid);
      await skipToStage2.mutateAsync({ sessionId: sid });
      navigate(`/app/session/${sid}/stage2`);
    } catch (err) {
      setError(getErrorMessage(err, "Failed to start analysis. Please try again."));
      setPendingPath(null);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--color-regu-bg)]/80 backdrop-blur-sm px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="entry-modal-title"
    >
      <div
        className={cn(
          "w-full max-w-[520px]",
          "bg-[var(--color-regu-surface)] border border-[var(--color-regu-border-hi)]",
          "rounded-2xl p-8 shadow-2xl",
        )}
      >
        {/* Eyebrow */}
        <p className="text-[10px] font-medium uppercase tracking-[0.14em] text-[var(--color-regu-fg-subtle)] mb-5">
          New analysis
        </p>

        {/* Heading */}
        <h2
          id="entry-modal-title"
          className="text-2xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-3"
        >
          Do you know your system's EU AI Act risk classification?
        </h2>

        {/* Helper */}
        <p className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed mb-8">
          The Act sorts AI systems into five risk tiers. If you are not sure
          where yours falls, we will classify it with a short interview.
        </p>

        {/* Error */}
        {error && (
          <div className="mb-4 rounded-lg border border-[var(--color-regu-danger)]/30 bg-[var(--color-regu-danger)]/5 px-4 py-3 text-sm text-[var(--color-regu-danger)]">
            <p>{error}</p>
            <button
              onClick={() => pendingPath === "high" ? handleHighRisk() : handleClassify()}
              className="mt-2 text-xs font-medium underline underline-offset-2 hover:no-underline"
              type="button"
            >
              Retry
            </button>
          </div>
        )}

        {/* Action buttons */}
        <div className="flex flex-col sm:flex-row gap-3 mb-6">
          <Button
            variant="outline"
            size="lg"
            onClick={handleClassify}
            disabled={pendingPath !== null}
            className="flex-1 text-sm"
          >
            {pendingPath === "classify" ? "Starting..." : "I don't know \u2014 classify my system"}
          </Button>
          <Button
            variant="primary"
            size="lg"
            onClick={handleHighRisk}
            disabled={pendingPath !== null}
            className="flex-1 text-sm"
          >
            {pendingPath === "high" ? "Starting..." : "I already know \u2014 my system is high-risk"}
          </Button>
        </div>

        {/* Risk tier accordion */}
        <details
          open={tierOpen}
          onToggle={(e) => setTierOpen((e.target as HTMLDetailsElement).open)}
        >
          <summary className="flex items-center gap-2 cursor-pointer text-sm text-[var(--color-regu-fg-muted)] hover:text-[var(--color-regu-fg)] transition-colors select-none list-none [&::-webkit-details-marker]:hidden">
            <ChevronDown
              size={14}
              className={cn("transition-transform duration-200", tierOpen && "rotate-180")}
              aria-hidden
            />
            What are the risk tiers?
          </summary>
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="border-b border-[var(--color-regu-border)]">
                  <th className="text-left py-2 pr-3 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
                    Tier
                  </th>
                  <th className="text-left py-2 pr-3 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
                    Definition
                  </th>
                  <th className="text-left py-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
                    Example
                  </th>
                </tr>
              </thead>
              <tbody>
                {RISK_TIERS.map((t) => (
                  <tr key={t.tier} className="border-b border-[var(--color-regu-border)]/50">
                    <td className="py-2.5 pr-3 text-[var(--color-regu-fg)] font-medium whitespace-nowrap align-top">
                      {t.tier}
                    </td>
                    <td className="py-2.5 pr-3 text-[var(--color-regu-fg-muted)] leading-snug align-top">
                      {t.definition}
                    </td>
                    <td className="py-2.5 text-[var(--color-regu-fg-subtle)] leading-snug align-top">
                      {t.example}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      </div>
    </div>
  );
}
