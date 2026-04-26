import { Link } from "react-router-dom";
import { ChevronRight, Play } from "lucide-react";
import { cn } from "@/lib/utils";
import { StartAnalysisButton } from "@/components/ui/start-analysis-button";

export default function FinalCTA() {
  return (
    <section
      className="relative bg-[var(--bg-base)] py-32 md:py-40 border-t border-[var(--hairline)] overflow-hidden"
    >
      <div
        aria-hidden
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            "radial-gradient(50% 60% at 50% 50%, var(--brand-glow) 0%, transparent 70%)",
        }}
      />
      <div className="relative max-w-[1200px] mx-auto px-6 md:px-10 flex flex-col items-center text-center">
        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2.25rem, 5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "20ch",
          }}
        >
          Five minutes.{" "}
          <span className="font-serif italic font-normal">
            No signup required to try.
          </span>
        </h2>
        <p
          className="mt-6 text-[var(--ink-secondary)] max-w-[58ch]"
          style={{ fontSize: "var(--text-body-lg)" }}
        >
          See your system's risk classification, with citations, before you
          give us an email.
        </p>
        <div className="mt-10 flex flex-wrap gap-4 justify-center">
          <StartAnalysisButton
            className={cn(
              "h-12 px-6 inline-flex items-center gap-2 rounded-full font-medium text-[15px]",
              "bg-[var(--brand-500)] text-white",
              "hover:bg-[var(--brand-400)] transition-colors duration-[var(--dur-fast)]"
            )}
          >
            Start Analysis
            <ChevronRight size={16} aria-hidden />
          </StartAnalysisButton>
          <Link
            to="/coming-soon"
            className={cn(
              "h-12 px-6 inline-flex items-center gap-2 rounded-full font-medium text-[15px]",
              "text-[var(--ink-primary)] border border-[var(--hairline-strong)]",
              "hover:bg-[rgba(255,255,255,0.04)] transition-colors duration-[var(--dur-fast)]"
            )}
          >
            <Play size={14} aria-hidden />
            Watch demo
          </Link>
        </div>
      </div>
    </section>
  );
}
