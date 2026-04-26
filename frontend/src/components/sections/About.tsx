/**
 * About - "Why we built REGU."
 * Two paragraphs + three-up principles strip.
 */

import { m as motion, useReducedMotion } from "framer-motion";
import { EASE_SMOOTH } from "@/lib/utils";
import { Section } from "@/components/ui/section";
import { cn } from "@/lib/utils";

// Three most public-facing principles from the project spec
// (Citation mandatory · Law-primary · Fail-safe, not fail-silent · Versioned corpus)
const PRINCIPLES = [
  {
    label: "Citation mandatory",
    description: "Every claim traces to a specific paragraph in the Act.",
  },
  {
    label: "Law-primary",
    description: "Legal text is authoritative. Guidance is secondary.",
  },
  {
    label: "Fail-safe, not fail-silent",
    description: 'Low confidence triggers a "review recommended" flag - never a false pass.',
  },
] as const;

export default function About() {
  const prefersReduced = useReducedMotion();

  return (
    <Section id="about" className="bg-[#060814]" eyebrow="Our story">
      <motion.div
        initial={prefersReduced ? false : { opacity: 0, y: 16, filter: "blur(6px)" }}
        whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
        viewport={{ once: true, margin: "-80px" }}
        transition={{ duration: 0.6, ease: EASE_SMOOTH }}
        className="max-w-3xl"
      >
        <h2
          className={cn(
            "font-[family-name:var(--font-heading)] font-bold",
            "text-[clamp(1.75rem,4vw,3rem)]",
            "leading-tight tracking-[-0.025em] text-[#EBEBEB] mb-8"
          )}
        >
          Why we built REGU.
        </h2>

        <div className="flex flex-col gap-5 prose-regu">
          <p className="text-[rgba(235,235,235,0.62)] leading-relaxed">
            The EU AI Act was designed with large enterprises in mind - companies
            with in-house legal departments, dedicated compliance officers, and
            the budget to engage specialist counsel at €500 an hour. EU founders
            and startups face the same obligations but rarely have the same
            resources. A first-pass compliance review that should take minutes
            ends up taking weeks of billable time, or simply doesn't happen.
          </p>

          <p className="text-[rgba(235,235,235,0.62)] leading-relaxed">
            REGU exists to open the Act's actual text to the people who have to
            comply with it. We built a retrieval-augmented engine that reads the
            regulation the same way a careful lawyer would - paragraph by
            paragraph, with every conclusion grounded in a specific citation -
            and produces a structured first-pass report in under five minutes.
            Not a substitute for legal advice. A starting point that turns
            "we have no idea where we stand" into "we know exactly what to ask
            our lawyer."
          </p>
        </div>
      </motion.div>

      {/* Principles strip */}
      <motion.div
        initial={prefersReduced ? false : { opacity: 0, y: 12 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-80px" }}
        transition={{ duration: 0.55, delay: 0.2, ease: EASE_SMOOTH }}
        className="mt-14 flex flex-col sm:flex-row divide-y sm:divide-y-0 sm:divide-x divide-[rgba(235,235,235,0.08)]"
        role="list"
        aria-label="Core principles"
      >
        {PRINCIPLES.map(({ label, description }) => (
          <div
            key={label}
            className="flex-1 py-6 sm:py-0 sm:px-8 first:pl-0 last:pr-0 flex flex-col gap-1.5"
            role="listitem"
          >
            <span className="text-sm font-semibold text-[#EBEBEB]">{label}</span>
            <span className="text-sm text-[rgba(235,235,235,0.62)] leading-relaxed">
              {description}
            </span>
          </div>
        ))}
      </motion.div>
    </Section>
  );
}
