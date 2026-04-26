import { useRef } from "react";
import { useInView } from "framer-motion";
import { cn } from "@/lib/utils";

type Tier = "PROHIBITED" | "HIGH RISK" | "LIMITED" | "MINIMAL";

const TIER_STYLES: Record<Tier, { bg: string; color: string; border: string }> = {
  PROHIBITED: {
    bg: "rgba(229,72,77,0.12)",
    color: "var(--danger)",
    border: "rgba(229,72,77,0.28)",
  },
  "HIGH RISK": {
    bg: "rgba(229,72,77,0.10)",
    color: "var(--danger)",
    border: "rgba(229,72,77,0.20)",
  },
  LIMITED: {
    bg: "rgba(229,162,59,0.10)",
    color: "var(--warn)",
    border: "rgba(229,162,59,0.22)",
  },
  MINIMAL: {
    bg: "rgba(60,203,127,0.10)",
    color: "var(--ok)",
    border: "rgba(60,203,127,0.22)",
  },
};

const CASES: { title: string; tier: Tier; body: string; cite: string }[] = [
  {
    title: "AI-driven CV screening for hiring",
    tier: "HIGH RISK",
    body: "Ranks job applicants for recruiters based on resume features and job-fit scoring.",
    cite: "Annex III §4(a)",
  },
  {
    title: "Customer support chatbot",
    tier: "LIMITED",
    body: "Conversational assistant that answers product questions and routes complex tickets.",
    cite: "Article 50(1)",
  },
  {
    title: "Spam filter using ML",
    tier: "MINIMAL",
    body: "Email or message filtering trained on labelled spam - no profiling beyond the filter.",
    cite: "Out of scope",
  },
  {
    title: "Real-time biometric ID in public spaces",
    tier: "PROHIBITED",
    body: "Live facial recognition deployed in publicly accessible areas without a Title-V exception.",
    cite: "Article 5(1)(h)",
  },
  {
    title: "Credit scoring for consumer loans",
    tier: "HIGH RISK",
    body: "ML-driven creditworthiness assessment determining whether to extend a consumer loan.",
    cite: "Annex III §5(b)",
  },
  {
    title: "AI-generated marketing copy tool",
    tier: "MINIMAL",
    body: "Generates ad and landing-page copy on request - output is reviewed by humans before use.",
    cite: "Out of scope · disclosure",
  },
  {
    title: "Predictive maintenance for factory robots",
    tier: "MINIMAL",
    body: "Forecasts when industrial equipment will fail based on sensor telemetry.",
    cite: "Out of scope",
  },
  {
    title: "Emotion recognition in workplace",
    tier: "PROHIBITED",
    body: "Infers employee emotional state from video, audio, or biometric data during work.",
    cite: "Article 5(1)(f)",
  },
];

function CaseCard({
  c,
  index,
}: {
  c: { title: string; tier: Tier; body: string; cite: string };
  index: number;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: "0px 0px -10% 0px" });
  const delay = Math.min(index, 5) * 0.06;

  return (
    <div
      ref={ref}
      className={cn(
        "rounded-2xl border border-[var(--hairline)] bg-[var(--bg-elev-1)] p-6",
        "transition-colors duration-[var(--dur-base)]",
        "hover:border-[var(--hairline-strong)]",
        "flex flex-col"
      )}
      style={{
        opacity: inView ? 1 : 0,
        transform: inView ? "translateY(0)" : "translateY(16px)",
        transition: `opacity 0.55s var(--ease-out) ${delay}s, transform 0.55s var(--ease-out) ${delay}s, border-color var(--dur-base) var(--ease-out)`,
      }}
    >
      <div className="flex items-start justify-between gap-3">
        <h3
          className="font-semibold text-[var(--ink-primary)] flex-1"
          style={{
            fontSize: "1rem",
            letterSpacing: "-0.01em",
            lineHeight: "1.35",
          }}
        >
          {c.title}
        </h3>
        <Pill tier={c.tier} />
      </div>
      <p
        className="mt-3 text-[var(--ink-secondary)] flex-1"
        style={{ fontSize: "0.9375rem", lineHeight: "1.55" }}
      >
        {c.body}
      </p>
      <div
        className="mt-5 pt-4 border-t border-[var(--hairline)] text-[var(--ink-tertiary)]"
        style={{
          fontFamily: "var(--font-mono)",
          fontSize: "var(--text-caption)",
        }}
      >
        {c.cite}
      </div>
    </div>
  );
}

function Pill({ tier }: { tier: Tier }) {
  const s = TIER_STYLES[tier];
  return (
    <span
      className="inline-flex items-center px-2.5 py-1 rounded-full font-medium uppercase tracking-[0.08em]"
      style={{
        fontSize: "var(--text-micro)",
        background: s.bg,
        color: s.color,
        border: `1px solid ${s.border}`,
      }}
    >
      {tier}
    </span>
  );
}

export default function UseCases() {
  return (
    <section className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]">
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
          style={{ fontSize: "var(--text-micro)" }}
        >
          What REGU can classify
        </p>

        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "22ch",
          }}
        >
          Real systems.{" "}
          <span className="font-serif italic font-normal">
            Real classifications.
          </span>
        </h2>

        <p
          className="mt-6 text-[var(--ink-secondary)] max-w-[62ch]"
          style={{ fontSize: "var(--text-body-lg)" }}
        >
          Synthetic scenarios from our test corpus, mapped to Annex III risk
          categories. The same engine that classifies these analyzes yours.
        </p>

        <div className="mt-12 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {CASES.map((c, i) => (
            <CaseCard key={c.title} c={c} index={i} />
          ))}
        </div>

        <p
          className="mt-8 text-[var(--ink-tertiary)] max-w-[60ch]"
          style={{ fontSize: "var(--text-caption)" }}
        >
          Scenarios shown are synthetic test cases. REGU runs identical
          retrieval and classification logic on your real system description.
        </p>
      </div>
    </section>
  );
}
