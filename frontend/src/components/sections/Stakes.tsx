/**
 * Stakes — fear/urgency section.
 * Three stat cards + pure-CSS ticker marquee of article references.
 */

import { m as motion, useReducedMotion } from "framer-motion";
import { Section } from "@/components/ui/section";
import { Marquee } from "@/components/ui/marquee";
import { cn } from "@/lib/utils";

const STAT_CARDS = [
  {
    value: "€35M",
    valueSub: "or 7% of global turnover",
    label: "Maximum fine",
    context:
      "Maximum fine for prohibited AI systems. Whichever is higher.",
    valueColor: "text-[#E5484D]",
  },
  {
    value: "458",
    valueSub: "pages · 113 articles · 13 annexes",
    label: "Regulation size",
    context:
      "The full Act, plus implementing acts, plus Commission guidance. Nobody reads all of it.",
    valueColor: "text-[#EBEBEB]",
  },
  {
    value: "~5",
    valueSub: "minutes",
    label: "REGU analysis time",
    context:
      "What REGU takes to give you a first-pass risk classification with citations back to the Act itself.",
    valueColor: "text-[#EBEBEB]",
  },
] as const;

const TICKER_ITEMS = [
  "Art. 5 — Prohibited practices",
  "Art. 6 — High-risk classification",
  "Annex III — High-risk categories",
  "Art. 9 — Risk management system",
  "Art. 10 — Data governance",
  "Annex IV — Technical documentation",
  "Art. 13 — Transparency obligations",
  "Art. 14 — Human oversight",
  "Art. 15 — Accuracy & robustness",
  "GPAI obligations",
  "Art. 50 — Transparency for users",
  "2 Aug 2026 — Enforcement begins",
];

// Framer Motion reveal variant
const cardReveal = {
  hidden: { opacity: 0, y: 20, filter: "blur(6px)" },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: {
      duration: 0.6,
      delay: i * 0.1,
      ease: [0.23, 0.86, 0.39, 0.96],
    },
  }),
};

interface StatCardProps {
  value: string;
  valueSub: string;
  label: string;
  context: string;
  valueColor: string;
  index: number;
}

function StatCard({ value, valueSub, label, context, valueColor, index }: StatCardProps) {
  const prefersReduced = useReducedMotion();

  return (
    <motion.article
      custom={index}
      variants={prefersReduced ? {} : cardReveal}
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, margin: "-80px" }}
      whileHover={
        prefersReduced
          ? {}
          : {
              y: -2,
              boxShadow: "0 0 0 1px rgba(235,235,235,0.16), 0 0 32px rgba(42,82,190,0.12)",
              transition: { duration: 0.15 },
            }
      }
      className={cn(
        "rounded-2xl p-8",
        "bg-[#0A0F1F] border border-[rgba(235,235,235,0.08)]",
        "flex flex-col gap-2",
        "transition-shadow duration-150"
      )}
    >
      {/* Giant number */}
      <div
        className={cn(
          "font-[family-name:var(--font-heading)] font-bold leading-none",
          "text-[clamp(3rem,8vw,5.5rem)]",
          "tabular-nums tracking-tight",
          valueColor
        )}
        style={{ fontFeatureSettings: '"tnum"' }}
        aria-label={`${value} ${valueSub}`}
      >
        {value}
      </div>
      <p className="text-xs uppercase tracking-[0.1em] text-[rgba(235,235,235,0.38)]">
        {valueSub}
      </p>
      <p className="text-sm font-semibold uppercase tracking-[0.08em] text-[rgba(235,235,235,0.38)] mt-2">
        {label}
      </p>
      <p className="text-sm text-[rgba(235,235,235,0.62)] leading-relaxed mt-1">
        {context}
      </p>
    </motion.article>
  );
}

export default function Stakes() {
  return (
    <Section id="stakes" className="bg-[#060814]">
      {/* Heading — left-aligned */}
      <div className="max-w-5xl mb-12">
        <h2
          className={cn(
            "font-[family-name:var(--font-heading)] font-bold",
            "text-[clamp(1.75rem,4vw,3.25rem)]",
            "leading-tight tracking-[-0.025em]",
            "text-[#EBEBEB]"
          )}
        >
          The regulation is 458 pages.{" "}
          <br className="hidden sm:block" />
          The fines are up to €35M.
        </h2>
        <p className="mt-4 text-lg text-[rgba(235,235,235,0.62)] max-w-2xl">
          You're building. You don't have a compliance team. Here's what's
          actually at stake.
        </p>
      </div>

      {/* Ticker above cards */}
      <div className="mb-8 border-y border-[rgba(235,235,235,0.08)] py-3">
        <Marquee
          duration={40}
          className="text-xs text-[rgba(235,235,235,0.38)] tracking-wide"
        >
          {TICKER_ITEMS.map((item) => (
            <span key={item} className="mx-8">
              {item}
            </span>
          ))}
        </Marquee>
      </div>

      {/* Stat cards */}
      <div className="grid md:grid-cols-3 gap-6">
        {STAT_CARDS.map((card, i) => (
          <StatCard key={card.label} {...card} index={i} />
        ))}
      </div>
    </Section>
  );
}
