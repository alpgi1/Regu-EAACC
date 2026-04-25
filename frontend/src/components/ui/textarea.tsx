import { forwardRef } from "react";
import { cn } from "@/lib/utils";

export interface TextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {}

/**
 * Minimal textarea primitive styled with REGU tokens.
 * Callers set min-height and other sizing via className.
 */
const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        ref={ref}
        className={cn(
          "flex w-full rounded-xl px-4 py-3",
          "bg-[var(--color-regu-surface)] border border-[var(--color-regu-border)]",
          "text-[var(--color-regu-fg)] text-sm leading-relaxed",
          "placeholder:text-[var(--color-regu-fg-subtle)]",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-regu-accent)]/60",
          "focus-visible:border-transparent",
          "resize-none transition-colors duration-150",
          "disabled:cursor-not-allowed disabled:opacity-40",
          className,
        )}
        {...props}
      />
    );
  },
);
Textarea.displayName = "Textarea";

export { Textarea };
