/**
 * NavBar - sticky, translucent, shrinks on scroll.
 * Mobile: hamburger opens a Vaul drawer from the top.
 */

import { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { ShieldCheck, Menu, X, ChevronRight } from "lucide-react";
import { Drawer } from "vaul";
import { Button, buttonVariants } from "@/components/ui/button";
import { StartAnalysisButton } from "@/components/ui/start-analysis-button";
import { cn } from "@/lib/utils";

const NAV_LINKS = [
  { label: "Methodology", href: "#methodology" },
  { label: "Platform", href: "#platform" },
  { label: "Contact", href: "#contact" },
] as const;

// Smooth-scroll to anchor even when React Router intercepts hash links
function scrollTo(href: string) {
  const id = href.replace("#", "");
  const el = document.getElementById(id);
  el?.scrollIntoView({ behavior: "smooth", block: "start" });
}

export default function NavBar() {
  const [scrolled, setScrolled] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const prevScroll = useRef(0);

  useEffect(() => {
    function onScroll() {
      setScrolled(window.scrollY > 24);
      prevScroll.current = window.scrollY;
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      role="banner"
      className={cn(
        "fixed top-0 inset-x-0 z-50 transition-all duration-200",
        "backdrop-blur-xl bg-[#060814]/60",
        scrolled && "border-b border-[rgba(235,235,235,0.08)]",
        scrolled ? "py-2" : "py-4"
      )}
    >
      <nav
        className="max-w-6xl mx-auto px-6 flex items-center justify-between"
        aria-label="Main navigation"
      >
        {/* Wordmark */}
        <Link
          to="/"
          className="flex items-center gap-2 group focus-visible:outline-[#2A52BE]"
          aria-label="REGU home"
        >
          <ShieldCheck
            size={20}
            className="text-[#2A52BE]"
            aria-hidden
          />
          <span className="text-[#EBEBEB] font-semibold text-lg font-[family-name:var(--font-heading)] tracking-tight">
            Regu
          </span>
        </Link>

        {/* Desktop centre links */}
        <ul className="hidden md:flex items-center gap-8 list-none" role="list">
          {NAV_LINKS.map((link) => (
            <li key={link.label}>
              <button
                onClick={() => scrollTo(link.href)}
                className="text-sm text-[rgba(235,235,235,0.62)] hover:text-[#EBEBEB] transition-colors cursor-pointer focus-visible:outline-[#2A52BE] focus-visible:outline-2 focus-visible:outline-offset-2 rounded"
              >
                {link.label}
              </button>
            </li>
          ))}
        </ul>

        {/* Desktop right actions */}
        <div className="hidden md:flex items-center gap-3">
          <Button variant="ghost" size="sm" asChild>
            <Link to="/app">Sign in</Link>
          </Button>
          <StartAnalysisButton className={buttonVariants({ variant: "primary", size: "sm" })}>
            Start Analysis
            <ChevronRight size={14} aria-hidden />
          </StartAnalysisButton>
        </div>

        {/* Mobile hamburger */}
        <Drawer.Root
          open={drawerOpen}
          onOpenChange={setDrawerOpen}
          direction="top"
        >
          <Drawer.Trigger asChild>
            <button
              className="md:hidden text-[#EBEBEB] focus-visible:outline-[#2A52BE] focus-visible:outline-2 focus-visible:outline-offset-2 rounded"
              aria-label={drawerOpen ? "Close menu" : "Open menu"}
              aria-expanded={drawerOpen}
              aria-controls="mobile-menu"
            >
              {drawerOpen ? <X size={22} aria-hidden /> : <Menu size={22} aria-hidden />}
            </button>
          </Drawer.Trigger>

          <Drawer.Portal>
            <Drawer.Overlay className="fixed inset-0 bg-black/40 z-40" />
            <Drawer.Content
              id="mobile-menu"
              className={cn(
                "fixed top-0 inset-x-0 z-50",
                "bg-[#0A0F1F] border-b border-[rgba(235,235,235,0.08)]",
                "pt-16 pb-8 px-6 flex flex-col gap-6"
              )}
              aria-label="Mobile navigation"
            >
              <Drawer.Title className="sr-only">Navigation menu</Drawer.Title>
              <ul className="flex flex-col gap-5 list-none" role="list">
                {NAV_LINKS.map((link) => (
                  <li key={link.label}>
                    <button
                      onClick={() => {
                        scrollTo(link.href);
                        setDrawerOpen(false);
                      }}
                      className="w-full text-left text-base text-[rgba(235,235,235,0.62)] hover:text-[#EBEBEB] transition-colors"
                    >
                      {link.label}
                    </button>
                  </li>
                ))}
              </ul>
              <div className="flex flex-col gap-3 pt-2 border-t border-[rgba(235,235,235,0.08)]">
                <Button variant="ghost" size="md" asChild className="w-full justify-center">
                  <Link to="/app" onClick={() => setDrawerOpen(false)}>
                    Sign in
                  </Link>
                </Button>
                <StartAnalysisButton className={cn(buttonVariants({ variant: "primary", size: "md" }), "w-full justify-center")}>
                  Start Analysis
                  <ChevronRight size={14} aria-hidden />
                </StartAnalysisButton>
              </div>
            </Drawer.Content>
          </Drawer.Portal>
        </Drawer.Root>
      </nav>
    </header>
  );
}
