import { cn } from "@/lib/utils";

interface SectionProps extends React.HTMLAttributes<HTMLElement> {
  /** Small uppercase label rendered above the heading slot */
  eyebrow?: string;
  /** Section id for anchor navigation and aria-labelledby */
  id?: string;
}

/**
 * Thin layout wrapper that standardises section padding, max-width, and
 * the optional eyebrow label. Renders as a <section> semantic element.
 */
export function Section({ eyebrow, id, className, children, ...props }: SectionProps) {
  const labelId = id ? `${id}-label` : undefined;

  return (
    <section
      id={id}
      aria-labelledby={labelId}
      className={cn("py-24 md:py-32", className)}
      {...props}
    >
      <div className="max-w-6xl mx-auto px-6">
        {eyebrow && (
          <p
            id={labelId}
            className="text-xs font-medium uppercase tracking-[0.14em] text-[rgba(235,235,235,0.38)] mb-4"
          >
            {eyebrow}
          </p>
        )}
        {children}
      </div>
    </section>
  );
}
