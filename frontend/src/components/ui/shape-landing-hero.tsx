/**
 * Geometric ambient shapes for the hero background.
 *
 * Adapted from the ElegantShape pattern - re-themed to REGU's
 * navy/indigo palette. Rose and amber removed; violet and deep-blue only.
 *
 * Usage:
 *   <HeroGeometric />   ← full-bleed background layer, position absolute
 */

import { motion, useReducedMotion } from "framer-motion";
import { cn, EASE_SMOOTH } from "@/lib/utils";

// ─── Single elongated capsule shape ───────────────────────────────────────

interface ElegantShapeProps {
  className?: string;
  delay?: number;
  width?: number;
  height?: number;
  rotate?: number;
  gradient?: string;
}

export function ElegantShape({
  className,
  delay = 0,
  width = 400,
  height = 100,
  rotate = 0,
  gradient = "from-indigo-500/[0.15]",
}: ElegantShapeProps) {
  const prefersReduced = useReducedMotion();

  return (
    <motion.div
      initial={prefersReduced ? false : { opacity: 0, y: -150, rotate: rotate - 15 }}
      animate={{ opacity: 1, y: 0, rotate }}
      transition={{
        duration: 2.4,
        delay,
        ease: EASE_SMOOTH,
        opacity: { duration: 1.2 },
      }}
      className={cn("absolute", className)}
    >
      <motion.div
        animate={prefersReduced ? {} : { y: [0, 20, 0] }}
        transition={{
          duration: 12,
          repeat: Infinity,
          ease: "easeInOut",
        }}
        style={{ width, height }}
        className="relative"
      >
        {/* Outer shell - very faint border */}
        <div
          className={cn(
            "absolute inset-0 rounded-full",
            "bg-gradient-to-r to-transparent",
            gradient,
            "border border-white/[0.06]",
            "shadow-[0_8px_32px_rgba(0,0,0,0.3)]",
            // inner highlight
            "after:absolute after:inset-[1px] after:rounded-full",
            "after:bg-gradient-to-b after:from-white/[0.08] after:to-transparent"
          )}
        />
      </motion.div>
    </motion.div>
  );
}

// ─── Composed background layer ─────────────────────────────────────────────

/**
 * Place as the first child of Hero with `position: absolute; inset: 0`.
 * All shapes are decorative; the container is aria-hidden.
 */
export function HeroGeometric() {
  return (
    <div
      className="absolute inset-0 overflow-hidden pointer-events-none"
      aria-hidden
    >
      {/* Large slow shape - upper-left, deep indigo */}
      <ElegantShape
        delay={0.3}
        width={640}
        height={160}
        rotate={-15}
        gradient="from-[#2A52BE]/[0.12]"
        className="-top-20 -left-48"
      />

      {/* Mid-size shape - upper-right, violet */}
      <ElegantShape
        delay={0.5}
        width={480}
        height={120}
        rotate={12}
        gradient="from-violet-500/[0.10]"
        className="top-12 -right-36"
      />

      {/* Accent shape - centre-left, accent-hi blue */}
      <ElegantShape
        delay={0.7}
        width={320}
        height={80}
        rotate={-8}
        gradient="from-[#4A6FE5]/[0.10]"
        className="top-1/3 left-16"
      />

      {/* Small shape - lower-right, indigo */}
      <ElegantShape
        delay={1.0}
        width={280}
        height={70}
        rotate={20}
        gradient="from-indigo-400/[0.08]"
        className="bottom-1/4 right-20"
      />

      {/* Tiny accent - bottom-left, violet */}
      <ElegantShape
        delay={1.2}
        width={200}
        height={50}
        rotate={-25}
        gradient="from-violet-400/[0.07]"
        className="bottom-16 left-1/4"
      />

      {/* Radial glow at centre-top - very faint */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] bg-[radial-gradient(ellipse_at_top,rgba(42,82,190,0.08)_0%,transparent_70%)]" />
    </div>
  );
}
