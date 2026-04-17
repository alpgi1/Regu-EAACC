import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merge Tailwind classes safely, resolving conflicts via tailwind-merge
 * and handling conditional classes via clsx.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Shared cubic-bezier easing for all Framer Motion transitions.
 * Typed as a 4-tuple so Framer Motion 12's strict Easing type is satisfied.
 */
export const EASE_SMOOTH: [number, number, number, number] = [0.23, 0.86, 0.39, 0.96];
