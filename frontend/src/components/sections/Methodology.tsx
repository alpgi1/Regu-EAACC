/**
 * Methodology — "Grounded in the Act. Not in guesswork."
 * Four-pillar RAG architecture + three promises + GlowingCard visual anchor.
 */

import { m as motion, useReducedMotion } from "framer-motion";
import {
  ScrollText,
  Library,
  BookOpen,
  GitBranch,
  ShieldAlert,
  ClipboardList,
  Compass,
} from "lucide-react";
import { Section } from "@/components/ui/section";
import { GlowingCard } from "@/components/ui/glowing-shadow";
import { cn, EASE_SMOOTH } from "@/lib/utils";

// ─── Four RAG pillars (actual architecture from project context) ──────────

const PILLARS = [
  {
    icon: ScrollText,
    title: "Legal corpus (RAG)",
    body: "The full EU AI Act, chunked paragraph-by-paragraph, retrieved via hybrid vector + keyword search.",
  },
  {
    icon: Library,
    title: "Curated use cases",
    body: "15+ real-world AI system scenarios mapped to Annex III risk categories.",
  },
  {
    icon: BookOpen,
    title: "Commission guidance",
    body: "Official Commission interpretive documents, header-aware retrieval.",
  },
  {
    icon: GitBranch,
    title: "Decision rules",
    body: "40 procedural compliance checks derived from the FLI Compliance Checker.",
  },
] as const;

// ─── Three promises ───────────────────────────────────────────────────────

const PROMISES = [
  {
    icon: ShieldAlert,
    headline: "This is not legal advice.",
    body: "REGU is a structured first-pass analyzer. It is not a substitute for a qualified EU lawyer or a notified body. We say this loudly because we mean it.",
  },
  {
    icon: ClipboardList,
    headline: "We help you collect the right data.",
    body: "Stage 1 classifies your system's risk tier. Stage 2 walks you through Annex IV documentation requirements. Upload a PDF, paste text, or answer guided questions.",
  },
  {
    icon: Compass,
    headline: "We help you plan the next steps.",
    body: "Every report ends with concrete recommendations: which authority to contact, which documentation to prepare, which obligations apply to your risk tier.",
  },
] as const;

// ─── Reveal variant ───────────────────────────────────────────────────────

const reveal = {
  hidden: { opacity: 0, y: 16, filter: "blur(6px)" },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { duration: 0.55, delay: i * 0.08, ease: EASE_SMOOTH },
  }),
};

export default function Methodology() {
  const prefersReduced = useReducedMotion();

  return (
    <Section id="methodology" className="bg-[#060814]" eyebrow="How it works">
      {/* Heading */}
      <div className="mb-12 max-w-3xl">
        <h2
          className={cn(
            "font-[family-name:var(--font-heading)] font-bold",
            "text-[clamp(1.75rem,4vw,3rem)]",
            "leading-tight tracking-[-0.025em] text-[#EBEBEB]"
          )}
        >
          Grounded in the Act.{" "}
          <span className="text-[rgba(235,235,235,0.62)]">Not in guesswork.</span>
        </h2>
        <p className="mt-4 text-lg text-[rgba(235,235,235,0.62)] max-w-2xl">
          REGU is a retrieval-augmented compliance engine. Every claim it makes is
          cited back to a specific paragraph in the regulation.
        </p>
      </div>

      {/* ── Sub-section A: four pillars + GlowingCard ──────────────────── */}
      <div className="flex flex-col xl:flex-row gap-12 items-start mb-16">
        {/* Pillars grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5 flex-1">
          {PILLARS.map(({ icon: Icon, title, body }, i) => (
            <motion.div
              key={title}
              custom={i}
              variants={prefersReduced ? {} : reveal}
              initial="hidden"
              whileInView="show"
              viewport={{ once: true, margin: "-80px" }}
              className={cn(
                "rounded-xl p-6",
                "bg-[#0A0F1F] border border-[rgba(235,235,235,0.08)]",
                "flex flex-col gap-3"
              )}
            >
              <Icon size={18} className="text-[#2A52BE]" aria-hidden />
              <h3 className="text-sm font-semibold text-[#EBEBEB]">{title}</h3>
              <p className="text-sm text-[rgba(235,235,235,0.62)] leading-relaxed">
                {body}
              </p>
            </motion.div>
          ))}
        </div>

        {/* GlowingCard visual anchor — one piece of "look at our tech" flair */}
        <motion.div
          variants={prefersReduced ? {} : reveal}
          custom={4}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, margin: "-80px" }}
          className="flex-shrink-0 flex xl:justify-end w-full xl:w-auto"
        >
          <GlowingCard>
            <p
              className={cn(
                "font-[family-name:var(--font-heading)] font-semibold",
                "text-xl tracking-tight text-[#EBEBEB]",
                "text-center leading-snug"
              )}
            >
              Cited.{" "}
              <span className="text-[#4A6FE5]">Traceable.</span>{" "}
              <br />
              Auditable.
            </p>
          </GlowingCard>
        </motion.div>
      </div>

      {/* ── Sub-section B: three promises ─────────────────────────────── */}
      <div className="flex flex-col divide-y divide-[rgba(235,235,235,0.08)]">
        {PROMISES.map(({ icon: Icon, headline, body }, i) => (
          <motion.div
            key={headline}
            custom={i}
            variants={prefersReduced ? {} : reveal}
            initial="hidden"
            whileInView="show"
            viewport={{ once: true, margin: "-80px" }}
            className="flex gap-6 py-8 first:pt-0 last:pb-0"
          >
            <div className="flex-shrink-0 mt-0.5">
              <Icon size={18} className="text-[#2A52BE]" aria-hidden />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-[#EBEBEB] mb-2">
                {headline}
              </h3>
              <p className="text-sm text-[rgba(235,235,235,0.62)] leading-relaxed max-w-2xl">
                {body}
              </p>
            </div>
          </motion.div>
        ))}
      </div>
    </Section>
  );
}
