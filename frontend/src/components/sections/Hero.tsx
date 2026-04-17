/**
 * Hero — full-viewport, deep-navy canvas with ambient floating shapes,
 * staggered fade-up entrance, countdown, and CTAs.
 */

import { useState, useRef, useEffect } from "react";
import { Link } from "react-router-dom";
import { m as motion, useReducedMotion } from "framer-motion";
import { ChevronRight, Play } from "lucide-react";
import { Drawer } from "vaul";
import { Button } from "@/components/ui/button";
import { Countdown } from "@/components/ui/countdown";
import { HeroGeometric } from "@/components/ui/shape-landing-hero";
import { cn, EASE_SMOOTH } from "@/lib/utils";

const EU_AI_ACT_DATE = new Date("2026-08-02T00:00:00Z");

// Staggered fade-up variants
const container = {
  hidden: {},
  show: {
    transition: { staggerChildren: 0.12 },
  },
};

const item = {
  hidden: { opacity: 0, y: 20, filter: "blur(4px)" },
  show: {
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { duration: 0.8, ease: EASE_SMOOTH },
  },
};

// ─── Demo Video Drawer ────────────────────────────────────────────────────

function DemoDrawer() {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  // Return focus to trigger when drawer closes
  useEffect(() => {
    if (!open) triggerRef.current?.focus();
  }, [open]);

  return (
    <Drawer.Root open={open} onOpenChange={setOpen}>
      <Drawer.Trigger asChild>
        <Button
          ref={triggerRef}
          variant="outline"
          size="lg"
          aria-haspopup="dialog"
        >
          <Play size={15} aria-hidden />
          Watch 90-second demo
        </Button>
      </Drawer.Trigger>

      <Drawer.Portal>
        <Drawer.Overlay
          className="fixed inset-0 bg-black/60 z-50"
          onClick={() => setOpen(false)}
        />
        <Drawer.Content
          className={cn(
            "fixed bottom-0 inset-x-0 z-50 mx-auto",
            "max-w-3xl w-full",
            "bg-[#0A0F1F] rounded-t-2xl",
            "border border-[rgba(235,235,235,0.08)]",
            "p-6 pb-10 flex flex-col gap-4",
            "focus:outline-none"
          )}
          aria-label="Demo video"
        >
          <Drawer.Title className="text-base font-semibold text-[#EBEBEB]">
            REGU — 90-second product demo
          </Drawer.Title>

          {/* 16:9 video placeholder — TODO: embed demo */}
          <div
            className={cn(
              "relative w-full rounded-xl overflow-hidden",
              "bg-[#111733] border border-[rgba(235,235,235,0.08)]",
              // 16:9 aspect ratio
              "aspect-video",
              "flex items-center justify-center"
            )}
            role="img"
            aria-label="Demo video placeholder"
          >
            <div className="flex flex-col items-center gap-3">
              <div className="w-16 h-16 rounded-full bg-[#2A52BE]/20 border border-[#2A52BE]/40 flex items-center justify-center">
                <Play size={24} className="text-[#4A6FE5] ml-1" aria-hidden />
              </div>
              <p className="text-sm text-[rgba(235,235,235,0.38)]">
                {/* TODO: embed demo */}
                Demo video coming soon
              </p>
            </div>
          </div>

          <button
            onClick={() => setOpen(false)}
            className="mt-2 text-sm text-[rgba(235,235,235,0.38)] hover:text-[#EBEBEB] transition-colors self-center"
          >
            Close
          </button>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}

// ─── Hero ────────────────────────────────────────────────────────────────

export default function Hero() {
  const prefersReduced = useReducedMotion();

  return (
    <section
      className="relative min-h-screen flex flex-col items-center justify-center overflow-hidden bg-[#060814]"
      aria-labelledby="hero-heading"
    >
      {/* Ambient geometric background */}
      <HeroGeometric />

      {/* Bottom fade into next section */}
      <div
        className="absolute bottom-0 inset-x-0 h-40 pointer-events-none"
        aria-hidden
        style={{
          background:
            "linear-gradient(to bottom, transparent 0%, #060814 100%)",
        }}
      />

      {/* Content */}
      <motion.div
        variants={container}
        initial={prefersReduced ? false : "hidden"}
        animate="show"
        className="relative z-10 max-w-[1000px] w-full mx-auto px-6 flex flex-col items-center text-center gap-8 pt-24 pb-16"
      >
        {/* Pill badge */}
        <motion.div variants={item}>
          <div
            className={cn(
              "inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs font-medium",
              "border border-[rgba(235,235,235,0.12)] bg-[rgba(235,235,235,0.04)]",
              "text-[rgba(235,235,235,0.62)]"
            )}
          >
            <span
              className="w-1.5 h-1.5 rounded-full bg-[#F5A524]"
              aria-hidden
            />
            EU AI Act · Effective 2 August 2026
          </div>
        </motion.div>

        {/* H1 */}
        <motion.h1
          id="hero-heading"
          variants={item}
          className={cn(
            "font-[family-name:var(--font-heading)] font-bold",
            "leading-[1.06] tracking-[-0.03em]",
            "text-[clamp(2.75rem,6vw,6.5rem)]"
          )}
        >
          {/* Line 1 — plain off-white */}
          <span className="block text-[#EBEBEB]">AI Act Compliance Checker</span>
          {/* Line 2 — gradient */}
          <span
            className="block"
            style={{
              background:
                "linear-gradient(90deg, #4A6FE5 0%, #EBEBEB 50%, #2A52BE 100%)",
              WebkitBackgroundClip: "text",
              WebkitTextFillColor: "transparent",
              backgroundClip: "text",
            }}
          >
            for Your Company
          </span>
        </motion.h1>

        {/* Sub-headline */}
        <motion.p
          variants={item}
          className="max-w-[620px] text-lg text-[rgba(235,235,235,0.62)] leading-relaxed"
        >
          Structured first-pass analysis of your AI system against the EU AI Act —
          grounded in the regulation's own text, delivered in under five minutes.
        </motion.p>

        {/* CTA row */}
        <motion.div
          variants={item}
          className="flex flex-wrap gap-4 justify-center"
        >
          <Button variant="primary" size="lg" asChild>
            <Link to="/app">
              Start Analysis
              <ChevronRight size={16} aria-hidden />
            </Link>
          </Button>
          <DemoDrawer />
        </motion.div>

        {/* Countdown */}
        <motion.div variants={item} className="flex flex-col items-center gap-3">
          <p className="text-xs uppercase tracking-[0.12em] text-[rgba(235,235,235,0.38)]">
            Time until enforcement
          </p>
          <Countdown targetDate={EU_AI_ACT_DATE} />
        </motion.div>

        {/* Trust strip */}
        <motion.div
          variants={item}
          className="flex flex-wrap justify-center items-center gap-x-5 gap-y-2"
          role="list"
        >
          {[
            "GDPR-aligned",
            "No training on your data",
            "Results in ~5 min",
            "Free to start",
          ].map((claim, i, arr) => (
            <span key={claim} className="flex items-center gap-5" role="listitem">
              <span className="text-xs text-[rgba(235,235,235,0.38)]">{claim}</span>
              {i < arr.length - 1 && (
                <span
                  className="w-px h-3 bg-[rgba(235,235,235,0.16)]"
                  aria-hidden
                />
              )}
            </span>
          ))}
        </motion.div>
      </motion.div>
    </section>
  );
}
