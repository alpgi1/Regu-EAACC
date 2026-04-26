import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { useReducedMotion } from "framer-motion";
import { cn } from "@/lib/utils";
import { StartAnalysisButton } from "@/components/ui/start-analysis-button";

const TARGET = new Date("2026-08-02T00:00:00Z").getTime();

function format(t: number) {
  const total = Math.max(0, t);
  const days = Math.floor(total / 86_400_000);
  const hours = Math.floor((total % 86_400_000) / 3_600_000);
  const mins = Math.floor((total % 3_600_000) / 60_000);
  return { days, hours, mins };
}

export default function UrgencyBar() {
  const reduce = useReducedMotion();
  const [show, setShow] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [now, setNow] = useState<number>(() => Date.now());

  useEffect(() => {
    const onScroll = () => {
      const hero = document.getElementById("hero");
      if (!hero) {
        setShow(window.scrollY > 400);
        return;
      }
      const past = window.scrollY > hero.offsetHeight * 0.6;
      setShow(past);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  if (dismissed) return null;

  const { days, hours, mins } = format(TARGET - now);
  const visible = show;

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "fixed bottom-0 inset-x-0 z-30 h-12",
        "border-t border-[var(--hairline)]",
        "transition-opacity duration-[var(--dur-base)] ease-[var(--ease-out)]",
        !reduce && "transition-transform",
        visible
          ? cn("opacity-100", !reduce && "translate-y-0")
          : cn("opacity-0 pointer-events-none", !reduce && "translate-y-full")
      )}
      style={{
        background: "rgba(5,8,20,0.85)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
      }}
    >
      <div className="max-w-[1200px] mx-auto h-full px-4 md:px-10 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0 text-[13px] text-[var(--ink-secondary)]">
          <span className="hidden sm:inline">EU AI Act enforcement begins in</span>
          <span className="sm:hidden">Enforcement in</span>
          <span
            className="text-[var(--ink-primary)] tabular-nums"
            style={{ fontFamily: "var(--font-mono)" }}
          >
            {String(days).padStart(2, "0")}d · {String(hours).padStart(2, "0")}h ·{" "}
            {String(mins).padStart(2, "0")}m
          </span>
        </div>
        <div className="flex items-center gap-2">
          <StartAnalysisButton
            className="hidden sm:inline-flex items-center text-[13px] text-[var(--ink-primary)] hover:text-[var(--brand-400)] transition-colors duration-[var(--dur-fast)]"
          >
            Start Analysis →
          </StartAnalysisButton>
          <button
            type="button"
            aria-label="Dismiss"
            onClick={() => setDismissed(true)}
            className="h-8 w-8 inline-flex items-center justify-center rounded-full text-[var(--ink-tertiary)] hover:text-[var(--ink-primary)] hover:bg-[rgba(255,255,255,0.04)]"
          >
            <X size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
