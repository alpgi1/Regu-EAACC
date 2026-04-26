/**
 * StatusChip - small pill for gap severity or section status.
 *
 * Variants: critical (red), major (warn), minor (accent), resolved (ok).
 * Each includes a text label - never icon-only.
 */

import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const chipVariants = cva(
  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium leading-tight whitespace-nowrap",
  {
    variants: {
      variant: {
        critical: "bg-[var(--color-regu-danger)]/10 text-[var(--color-regu-danger)]",
        major: "bg-[var(--color-regu-warn)]/10 text-[var(--color-regu-warn)]",
        minor: "bg-[var(--color-regu-accent)]/10 text-[var(--color-regu-accent)]",
        resolved: "bg-[var(--color-regu-ok)]/10 text-[var(--color-regu-ok)]",
        neutral: "bg-[var(--color-regu-fg-subtle)]/10 text-[var(--color-regu-fg-muted)]",
      },
    },
    defaultVariants: {
      variant: "neutral",
    },
  },
);

interface StatusChipProps extends VariantProps<typeof chipVariants> {
  children: React.ReactNode;
  className?: string;
}

export function StatusChip({ variant, children, className }: StatusChipProps) {
  return (
    <span className={cn(chipVariants({ variant }), className)}>
      {children}
    </span>
  );
}

/**
 * Map a severity string from the backend to a StatusChip variant.
 * Defensively handles unknown values by falling back to "neutral".
 */
export function severityToVariant(
  severity: string | null | undefined,
): "critical" | "major" | "minor" | "resolved" | "neutral" {
  if (!severity) return "neutral";
  const lower = severity.toLowerCase();
  if (lower === "critical" || lower === "high") return "critical";
  if (lower === "major" || lower === "medium") return "major";
  if (lower === "minor" || lower === "low") return "minor";
  if (lower === "resolved" || lower === "met" || lower === "satisfied") return "resolved";
  return "neutral";
}
