/**
 * GlowingCard — a single card with an animated rotating-gradient border
 * and a pointer-reactive radial glow behind it.
 *
 * Converted from a Next.js `<style jsx>` snippet to a co-located CSS file
 * (glowing-shadow.css) so it works in Vite without the jsx transform.
 *
 * @property declarations in the CSS are standard CSS (Chromium + Safari).
 * Firefox degrades gracefully: static border, no animated gradient.
 *
 * Mounted only in the Methodology section — scoped, not global.
 */

import { useRef } from "react";
import { useReducedMotion } from "framer-motion";
import "./glowing-shadow.css";

interface GlowingCardProps {
  children: React.ReactNode;
  className?: string;
}

export function GlowingCard({ children, className }: GlowingCardProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const prefersReduced = useReducedMotion();

  function handleMouseMove(e: React.MouseEvent<HTMLDivElement>) {
    // Skip pointer tracking on touch / reduced-motion
    if (prefersReduced || !cardRef.current) return;

    const rect = cardRef.current.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;

    cardRef.current.style.setProperty("--card-x", `${x}%`);
    cardRef.current.style.setProperty("--card-y", `${y}%`);
  }

  return (
    <div
      ref={cardRef}
      className={`regu-glow-card${className ? ` ${className}` : ""}`}
      onMouseMove={handleMouseMove}
    >
      <div className="regu-glow-card__inner">{children}</div>
    </div>
  );
}
