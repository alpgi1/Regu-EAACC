/**
 * SecondaryCta - mid-page conversion band.
 * Thin, centered, full-bleed with a faint radial glow.
 */

import { Link } from "react-router-dom";
import { m as motion, useReducedMotion } from "framer-motion";
import { EASE_SMOOTH } from "@/lib/utils";
import { ChevronRight, Play } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { StartAnalysisButton } from "@/components/ui/start-analysis-button";

export default function SecondaryCta() {
  const prefersReduced = useReducedMotion();

  return (
    <section
      className="relative py-20 bg-[#0A0F1F] border-y border-[rgba(235,235,235,0.08)] overflow-hidden"
      aria-labelledby="scta-heading"
    >
      {/* Faint radial glow - accent at 6% alpha, 1200px radius */}
      <div
        className="absolute inset-0 pointer-events-none"
        aria-hidden
        style={{
          background:
            "radial-gradient(ellipse 1200px 600px at center, rgba(42,82,190,0.06) 0%, transparent 70%)",
        }}
      />

      <motion.div
        initial={prefersReduced ? false : { opacity: 0, y: 16 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, margin: "-80px" }}
        transition={{ duration: 0.6, ease: EASE_SMOOTH }}
        className="relative z-10 max-w-6xl mx-auto px-6 flex flex-col items-center text-center gap-6"
      >
        <h2
          id="scta-heading"
          className="font-[family-name:var(--font-heading)] font-bold text-[clamp(1.5rem,3.5vw,2.5rem)] leading-tight tracking-[-0.02em] text-[#EBEBEB]"
        >
          Five minutes. No signup required to try.
        </h2>
        <p className="text-lg text-[rgba(235,235,235,0.62)] max-w-xl">
          See your system's risk classification, with citations, before you give
          us an email.
        </p>

        <div className="flex flex-wrap gap-4 justify-center">
          <StartAnalysisButton className={buttonVariants({ variant: "primary", size: "lg" })}>
            Start Analysis
            <ChevronRight size={16} aria-hidden />
          </StartAnalysisButton>
          <Button variant="outline" size="lg" asChild>
            <Link to="#demo">
              <Play size={15} aria-hidden />
              Watch demo
            </Link>
          </Button>
        </div>
      </motion.div>
    </section>
  );
}
