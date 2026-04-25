import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { scrollToSection } from "@/lib/lenis";

const NAV_LINKS = [
  { id: "methodology", label: "Methodology" },
  { id: "platform", label: "Platform" },
  { id: "contact", label: "Contact" },
];

function ShieldMark({ size = 18 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="M12 2 4 5v6c0 5 3.5 9.5 8 11 4.5-1.5 8-6 8-11V5l-8-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export default function Nav() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 16);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (mobileOpen) document.body.style.overflow = "hidden";
    else document.body.style.overflow = "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileOpen]);

  function handleAnchor(id: string, e: React.MouseEvent) {
    e.preventDefault();
    setMobileOpen(false);
    scrollToSection(id);
  }

  return (
    <>
      <nav
        className={cn(
          "fixed top-0 inset-x-0 z-40 h-16",
          "transition-[background-color,backdrop-filter,border-color] duration-[var(--dur-base)] ease-[var(--ease-out)]",
          scrolled
            ? "bg-[rgba(5,8,20,0.72)] backdrop-blur-xl border-b border-[var(--hairline)]"
            : "bg-transparent border-b border-transparent"
        )}
        aria-label="Primary"
      >
        <div className="max-w-[1200px] mx-auto h-full px-6 md:px-10 flex items-center justify-between">
          {/* Wordmark */}
          <Link
            to="/"
            className="flex items-center gap-2 text-[var(--ink-primary)] hover:text-[var(--ink-primary)]"
          >
            <span className="text-[var(--brand-500)]">
              <ShieldMark />
            </span>
            <span className="font-semibold tracking-tight text-[15px]">REGU</span>
          </Link>

          {/* Center links — desktop */}
          <div className="hidden md:flex items-center gap-8">
            {NAV_LINKS.map((l) => (
              <a
                key={l.id}
                href={`#${l.id}`}
                onClick={(e) => handleAnchor(l.id, e)}
                className={cn(
                  "text-[14px] text-[var(--ink-secondary)]",
                  "transition-colors duration-[var(--dur-fast)]",
                  "hover:text-[var(--ink-primary)]",
                  "relative",
                  "after:content-[''] after:absolute after:left-0 after:bottom-[-4px] after:h-px after:w-0 after:bg-[var(--ink-primary)]",
                  "after:transition-[width] after:duration-[var(--dur-base)]",
                  "hover:after:w-full"
                )}
              >
                {l.label}
              </a>
            ))}
          </div>

          {/* Right CTAs — desktop */}
          <div className="hidden md:flex items-center gap-3">
            <Link
              to="/coming-soon"
              className={cn(
                "h-9 px-4 inline-flex items-center rounded-full text-[14px]",
                "text-[var(--ink-secondary)] hover:text-[var(--ink-primary)]",
                "hover:bg-[rgba(255,255,255,0.04)]",
                "transition-colors duration-[var(--dur-fast)]"
              )}
            >
              Sign in
            </Link>
            <Link
              to="/app"
              className={cn(
                "h-9 px-4 inline-flex items-center rounded-full text-[14px] font-medium",
                "bg-[var(--brand-500)] text-white",
                "hover:bg-[var(--brand-400)] transition-colors duration-[var(--dur-fast)]"
              )}
            >
              Start Analysis
            </Link>
          </div>

          {/* Mobile hamburger */}
          <button
            type="button"
            className="md:hidden h-9 w-9 inline-flex items-center justify-center rounded-full text-[var(--ink-primary)] hover:bg-[rgba(255,255,255,0.06)]"
            aria-label="Open menu"
            aria-expanded={mobileOpen}
            onClick={() => setMobileOpen(true)}
          >
            <Menu size={20} />
          </button>
        </div>
      </nav>

      {/* Mobile sheet */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-50 md:hidden bg-[var(--bg-base)] flex flex-col"
          role="dialog"
          aria-modal="true"
          aria-label="Menu"
        >
          <div className="h-16 px-6 flex items-center justify-between border-b border-[var(--hairline)]">
            <Link
              to="/"
              onClick={() => setMobileOpen(false)}
              className="flex items-center gap-2 text-[var(--ink-primary)]"
            >
              <span className="text-[var(--brand-500)]">
                <ShieldMark />
              </span>
              <span className="font-semibold tracking-tight text-[15px]">REGU</span>
            </Link>
            <button
              type="button"
              className="h-9 w-9 inline-flex items-center justify-center rounded-full text-[var(--ink-primary)] hover:bg-[rgba(255,255,255,0.06)]"
              aria-label="Close menu"
              onClick={() => setMobileOpen(false)}
            >
              <X size={20} />
            </button>
          </div>
          <div className="flex flex-col px-6 py-8 gap-2 flex-1">
            {NAV_LINKS.map((l) => (
              <a
                key={l.id}
                href={`#${l.id}`}
                onClick={(e) => handleAnchor(l.id, e)}
                className="py-3 text-2xl text-[var(--ink-primary)] tracking-tight"
              >
                {l.label}
              </a>
            ))}
            <div className="mt-auto flex flex-col gap-3 pt-8">
              <Link
                to="/coming-soon"
                onClick={() => setMobileOpen(false)}
                className="h-12 inline-flex items-center justify-center rounded-full text-[15px] text-[var(--ink-primary)] border border-[var(--hairline-strong)]"
              >
                Sign in
              </Link>
              <Link
                to="/app"
                onClick={() => setMobileOpen(false)}
                className="h-12 inline-flex items-center justify-center rounded-full text-[15px] font-medium bg-[var(--brand-500)] text-white"
              >
                Start Analysis
              </Link>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
