import { cn } from "@/lib/utils";

interface MarqueeProps {
  children: React.ReactNode;
  /** Duration in seconds (default 40) */
  duration?: number;
  className?: string;
}

/**
 * Pure-CSS infinite marquee. Duplicates children twice so the loop is
 * seamless. Pauses on hover. Respects prefers-reduced-motion via CSS.
 *
 * No JS - no IntersectionObserver, no requestAnimationFrame.
 */
export function Marquee({ children, duration = 40, className }: MarqueeProps) {
  return (
    <div
      className={cn(
        "overflow-hidden whitespace-nowrap",
        // pause on hover
        "[&:hover_.marquee-track]:animation-play-state-paused",
        className
      )}
      aria-hidden // decorative - screen readers don't need the ticker
    >
      <style>{`
        @keyframes marquee-scroll {
          from { transform: translateX(0); }
          to   { transform: translateX(-50%); }
        }
        .marquee-track {
          display: inline-flex;
          animation: marquee-scroll ${duration}s linear infinite;
        }
        .marquee-track:hover {
          animation-play-state: paused;
        }
        @media (prefers-reduced-motion: reduce) {
          .marquee-track {
            animation: none;
          }
        }
      `}</style>
      {/* Two copies → seamless loop (first copy scrolls out as second enters) */}
      <span className="marquee-track">
        <span className="inline-flex">{children}</span>
        <span className="inline-flex" aria-hidden>{children}</span>
      </span>
    </div>
  );
}
