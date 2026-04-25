import { useEffect, useRef, useState } from "react";
import { animate, useInView, useReducedMotion } from "framer-motion";
import { cn } from "@/lib/utils";

type Card = {
  value: number;
  prefix?: string;
  suffix?: string;
  display?: (n: number) => string;
  caption: string;
  title: string;
  body: string;
  color: string;
};

const CARDS: Card[] = [
  {
    value: 35,
    prefix: "€",
    suffix: "M",
    caption: "or 7% of global turnover",
    title: "MAXIMUM FINE",
    body: "Maximum fine for prohibited AI systems. Whichever is higher.",
    color: "var(--danger)",
  },
  {
    value: 458,
    caption: "pages · 113 articles · 13 annexes",
    title: "REGULATION SIZE",
    body: "The full Act, plus implementing acts, plus Commission guidance. Nobody reads all of it.",
    color: "var(--ink-primary)",
  },
  {
    value: 5,
    prefix: "~",
    caption: "minutes",
    title: "REGU ANALYSIS TIME",
    body: "What REGU takes to give you a first-pass risk classification with citations back to the Act.",
    color: "var(--ok)",
  },
];

function CountUp({
  value,
  prefix = "",
  suffix = "",
  active,
}: {
  value: number;
  prefix?: string;
  suffix?: string;
  active: boolean;
}) {
  const reduce = useReducedMotion();
  const [n, setN] = useState<number>(reduce ? value : 0);

  useEffect(() => {
    if (reduce) {
      setN(value);
      return;
    }
    if (!active) return;
    const controls = animate(0, value, {
      duration: 0.8,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => setN(v),
    });
    return () => controls.stop();
  }, [active, value, reduce]);

  return (
    <span className="tabular-nums">
      {prefix}
      {Math.round(n).toLocaleString("en-US")}
      {suffix}
    </span>
  );
}

function StakesCard({ card, index }: { card: Card; index: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: "0px 0px -10% 0px" });

  return (
    <div
      ref={ref}
      className={cn(
        "group relative rounded-2xl p-8",
        "bg-[var(--bg-elev-1)]",
        "border border-[var(--hairline)]",
        "transition-[border-color,box-shadow] duration-[var(--dur-base)] ease-[var(--ease-out)]",
        "hover:border-[var(--hairline-strong)]",
        "hover:shadow-[inset_0_0_0_1px_var(--hairline-strong)]"
      )}
      style={{
        opacity: inView ? 1 : 0,
        transform: inView ? "translateY(0)" : "translateY(16px)",
        transition: `opacity 0.55s var(--ease-out) ${index * 0.06}s, transform 0.55s var(--ease-out) ${index * 0.06}s, border-color var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out)`,
      }}
    >
      <div
        className="font-semibold leading-none tracking-tight"
        style={{
          fontSize: "clamp(3.25rem, 6vw, 4.75rem)",
          color: card.color,
        }}
      >
        <CountUp
          value={card.value}
          prefix={card.prefix}
          suffix={card.suffix}
          active={inView}
        />
      </div>
      <p
        className="mt-3 text-[var(--ink-tertiary)]"
        style={{ fontSize: "var(--text-caption)" }}
      >
        {card.caption}
      </p>
      <p
        className="mt-8 uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium"
        style={{ fontSize: "var(--text-micro)" }}
      >
        {card.title}
      </p>
      <p className="mt-2 text-[var(--ink-secondary)] leading-relaxed text-[15px]">
        {card.body}
      </p>
    </div>
  );
}

export default function Stakes() {
  return (
    <section
      id="stakes"
      className="bg-[var(--bg-base)] py-20 md:py-32"
    >
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "22ch",
          }}
        >
          The regulation is{" "}
          <span className="font-serif italic font-normal">458 pages.</span>{" "}
          The fines are up to{" "}
          <span className="font-serif italic font-normal">€35M.</span>
        </h2>
        <p
          className="mt-6 text-[var(--ink-secondary)] max-w-[58ch]"
          style={{ fontSize: "var(--text-body-lg)" }}
        >
          You're building. You don't have a compliance team. Here's what's
          actually at stake.
        </p>

        <div className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-6">
          {CARDS.map((c, i) => (
            <StakesCard key={c.title} card={c} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
