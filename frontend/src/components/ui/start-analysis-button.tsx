import { useState } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

interface Props {
  className?: string;
  children?: React.ReactNode;
}

export function StartAnalysisButton({ className, children }: Props) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button type="button" className={className} onClick={() => setOpen(true)}>
        {children}
      </button>

      {open && (
        <div
          className="fixed inset-0 z-[200] flex items-center justify-center p-6"
          style={{
            background: "rgba(0,0,0,0.65)",
            backdropFilter: "blur(6px)",
            WebkitBackdropFilter: "blur(6px)",
          }}
          onClick={() => setOpen(false)}
          role="dialog"
          aria-modal="true"
          aria-label="Early access notice"
        >
          <div
            className={cn(
              "relative max-w-[460px] w-full rounded-2xl p-8 flex flex-col gap-5",
              "bg-[#0A0F1F] border border-[rgba(235,235,235,0.12)] shadow-2xl"
            )}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="absolute top-4 right-4 text-[rgba(235,235,235,0.38)] hover:text-[#EBEBEB] transition-colors"
              aria-label="Close"
            >
              <X size={18} />
            </button>

            <div className="flex flex-col gap-1.5">
              <span className="text-xs font-medium uppercase tracking-[0.1em] text-[rgba(235,235,235,0.38)]">
                Early Access
              </span>
              <h2 className="font-semibold text-[#EBEBEB] text-xl leading-snug">
                Not open for public testing yet.
              </h2>
            </div>

            <p className="text-[rgba(235,235,235,0.62)] text-sm leading-relaxed">
              REGU is currently in MVP stage and is not open for public testing.
              To get early access or ask a question, please reach out to us directly.
            </p>

            <div className="flex flex-col gap-2 pt-1">
              <a
                href="mailto:alpgiray.dev@gmail.com"
                className="text-sm text-[#4A6FE5] hover:underline transition-colors"
              >
                alpgiray.dev@gmail.com
              </a>
              <a
                href="mailto:cnumanberk@gmail.com"
                className="text-sm text-[#4A6FE5] hover:underline transition-colors"
              >
                cnumanberk@gmail.com
              </a>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
