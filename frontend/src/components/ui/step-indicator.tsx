/**
 * StepIndicator - thin horizontal strip showing analysis progress.
 *
 * Three steps: 1 Classification, 2 Documentation, 3 Report.
 * Current step is accent-colored; completed steps are muted; upcoming are subtle.
 */

import { cn } from "@/lib/utils";

const STEPS = [
  { number: 1, label: "Classification" },
  { number: 2, label: "Documentation" },
  { number: 3, label: "Report" },
] as const;

interface StepIndicatorProps {
  /** 1 | 2 | 3 */
  currentStep: 1 | 2 | 3;
  className?: string;
}

export function StepIndicator({ currentStep, className }: StepIndicatorProps) {
  return (
    <div
      role="list"
      aria-label="Analysis progress"
      className={cn("flex items-center gap-1 text-xs font-medium tracking-wide", className)}
    >
      {STEPS.map((step, i) => {
        const isCurrent = step.number === currentStep;
        const isCompleted = step.number < currentStep;

        return (
          <span key={step.number} className="flex items-center gap-1" role="listitem">
            {i > 0 && (
              <span
                className="mx-1.5 text-[var(--color-regu-fg-subtle)]"
                aria-hidden
              >
                ·
              </span>
            )}
            <span
              className={cn(
                "tabular-nums",
                isCurrent && "text-[var(--color-regu-accent)]",
                isCompleted && "text-[var(--color-regu-fg-muted)]",
                !isCurrent && !isCompleted && "text-[var(--color-regu-fg-subtle)]",
              )}
            >
              {step.number}
            </span>
            <span
              className={cn(
                isCurrent && "text-[var(--color-regu-accent)]",
                isCompleted && "text-[var(--color-regu-fg-muted)]",
                !isCurrent && !isCompleted && "text-[var(--color-regu-fg-subtle)]",
              )}
            >
              {step.label}
            </span>
          </span>
        );
      })}
    </div>
  );
}
