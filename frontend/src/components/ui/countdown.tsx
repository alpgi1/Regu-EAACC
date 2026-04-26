import { useState, useEffect } from "react";
import { cn } from "@/lib/utils";

interface TimeLeft {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  isPast: boolean;
}

function getTimeLeft(target: Date): TimeLeft {
  const diff = target.getTime() - Date.now();
  if (diff <= 0) {
    return { days: 0, hours: 0, minutes: 0, seconds: 0, isPast: true };
  }
  const totalSeconds = Math.floor(diff / 1000);
  return {
    days:    Math.floor(totalSeconds / 86400),
    hours:   Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
    isPast:  false,
  };
}

/** Headless hook - returns live countdown to targetDate, updated every second. */
export function useCountdown(targetDate: Date): TimeLeft {
  const [timeLeft, setTimeLeft] = useState<TimeLeft>(() => getTimeLeft(targetDate));

  useEffect(() => {
    const id = setInterval(() => {
      setTimeLeft(getTimeLeft(targetDate));
    }, 1000);
    return () => clearInterval(id);
  }, [targetDate]);

  return timeLeft;
}

// ─── Tile ──────────────────────────────────────────────────────────────────

interface TileProps {
  value: number;
  label: string;
  /** Apply the seconds-only faint blue glow */
  glowing?: boolean;
}

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function Tile({ value, label, glowing = false }: TileProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center",
        "rounded-xl px-5 py-4 min-w-[72px]",
        "bg-[#0A0F1F] border border-[rgba(235,235,235,0.08)]",
        // inset top highlight
        "shadow-[inset_0_1px_0_rgba(255,255,255,0.05)]",
        glowing && "shadow-[inset_0_1px_0_rgba(255,255,255,0.05),0_0_24px_rgba(42,82,190,0.25)]"
      )}
    >
      <span
        className="text-3xl font-semibold tabular-nums tracking-tight text-[#EBEBEB] font-[family-name:var(--font-heading)]"
        style={{ fontFeatureSettings: '"tnum"' }}
      >
        {pad(value)}
      </span>
      <span className="mt-1 text-[10px] uppercase tracking-[0.12em] text-[rgba(235,235,235,0.38)]">
        {label}
      </span>
    </div>
  );
}

// ─── Countdown presentational component ───────────────────────────────────

interface CountdownProps {
  targetDate: Date;
  className?: string;
}

/**
 * Renders four DD HH MM SS tiles counting down to targetDate.
 * When the date has passed, renders an "In force" pill.
 * aria-live="polite" is on the region, not per-digit, to avoid screen-reader spam.
 */
export function Countdown({ targetDate, className }: CountdownProps) {
  const { days, hours, minutes, seconds, isPast } = useCountdown(targetDate);

  if (isPast) {
    return (
      <div
        className={cn(
          "inline-flex items-center gap-2 px-4 py-2 rounded-full",
          "bg-[rgba(48,164,108,0.15)] border border-[rgba(48,164,108,0.3)]",
          "text-[#30A46C] text-sm font-medium",
          className
        )}
        role="status"
      >
        <span className="w-1.5 h-1.5 rounded-full bg-[#30A46C]" aria-hidden />
        In force
      </div>
    );
  }

  return (
    <div
      role="timer"
      aria-live="polite"
      aria-label={`${days} days, ${hours} hours, ${minutes} minutes, ${seconds} seconds until the EU AI Act takes effect`}
      className={cn("flex items-center gap-3", className)}
    >
      <Tile value={days}    label="Days" />
      <Tile value={hours}   label="Hours" />
      <Tile value={minutes} label="Min" />
      <Tile value={seconds} label="Sec" glowing />
    </div>
  );
}
