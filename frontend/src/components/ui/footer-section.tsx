/**
 * FooterSection — adapted from the AnimatedContainer footer pattern.
 *
 * Changes from the original:
 * - `from 'motion/react'` → `from 'framer-motion'` (consistent project-wide)
 * - ShieldCheck icon (Lucide) replaces FrameIcon
 * - Sections: Product, Company, Resources, Legal (replaces Social Links block)
 * - Radial glow recolored to regu-accent at very low alpha
 * - Copyright and brand updated to REGU
 */

import { motion, useReducedMotion } from "framer-motion";
import { ShieldCheck, ExternalLink } from "lucide-react";
import { Link } from "react-router-dom";
import { EASE_SMOOTH } from "@/lib/utils";

const FOOTER_LINKS = {
  Product: [
    { label: "Analysis", href: "/app" },
    { label: "Methodology", href: "#methodology" },
    { label: "Demo", href: "#demo" },
    { label: "Pricing", href: "#pricing" },
  ],
  Company: [
    { label: "About", href: "#about" },
    { label: "Contact", href: "#contact" },
    { label: "Careers", href: "#careers" },
    { label: "Press", href: "#press" },
  ],
  Resources: [
    { label: "EU AI Act text", href: "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32024R1689", target: "_blank" },
    { label: "Annex IV guide", href: "#annex-iv" },
    { label: "Changelog", href: "#changelog" },
    { label: "FAQ", href: "#faq" },
  ],
  Legal: [
    { label: "Privacy", href: "/privacy" },
    { label: "Terms", href: "/terms" },
    { label: "Cookies", href: "/cookies" },
    { label: "DPA", href: "/dpa" },
  ],
} as const;

interface AnimatedContainerProps {
  children: React.ReactNode;
  delay?: number;
}

function AnimatedContainer({ children, delay = 0 }: AnimatedContainerProps) {
  const prefersReduced = useReducedMotion();

  return (
    <motion.div
      initial={prefersReduced ? false : { opacity: 0, y: 16 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.5, delay, ease: EASE_SMOOTH }}
    >
      {children}
    </motion.div>
  );
}

export function FooterSection() {
  const year = new Date().getFullYear();

  return (
    <footer className="relative border-t border-[rgba(235,235,235,0.08)] bg-[#060814]">
      {/* Radial glow — regu-accent at very low alpha */}
      <div
        className="absolute top-0 left-1/2 -translate-x-1/2 w-[1200px] h-[300px] pointer-events-none"
        aria-hidden
        style={{
          background:
            "radial-gradient(ellipse at top, rgba(42,82,190,0.06) 0%, transparent 70%)",
        }}
      />

      <div className="relative max-w-6xl mx-auto px-6 py-16">
        {/* Top row: brand + columns */}
        <div className="grid grid-cols-2 gap-8 md:grid-cols-5 lg:grid-cols-5">
          {/* Brand */}
          <AnimatedContainer delay={0}>
            <div className="col-span-2 md:col-span-1 lg:col-span-1 flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <ShieldCheck
                  className="text-[#2A52BE]"
                  size={20}
                  aria-hidden
                />
                <span className="text-[#EBEBEB] font-semibold text-lg font-[family-name:var(--font-heading)]">
                  Regu
                </span>
              </div>
              <p className="text-sm text-[rgba(235,235,235,0.38)] leading-relaxed max-w-[200px]">
                EU AI Act compliance analysis grounded in the regulation's own text.
              </p>
              {/* Social — LinkedIn + GitHub only */}
              <div className="flex gap-3 mt-1">
                <a
                  href="https://linkedin.com"
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="REGU on LinkedIn (opens in new tab)"
                  className="text-xs text-[rgba(235,235,235,0.38)] hover:text-[#EBEBEB] transition-colors flex items-center gap-1"
                >
                  LinkedIn
                  <ExternalLink size={10} aria-hidden />
                </a>
                <a
                  href="https://github.com/alpgi1/Regu-EAACC"
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label="REGU on GitHub (opens in new tab)"
                  className="text-xs text-[rgba(235,235,235,0.38)] hover:text-[#EBEBEB] transition-colors flex items-center gap-1"
                >
                  GitHub
                  <ExternalLink size={10} aria-hidden />
                </a>
              </div>
            </div>
          </AnimatedContainer>

          {/* Link columns */}
          {(Object.entries(FOOTER_LINKS) as [string, readonly { label: string; href: string; target?: string }[]][]).map(
            ([section, links], i) => (
              <AnimatedContainer key={section} delay={0.1 + i * 0.05}>
                <div className="flex flex-col gap-3">
                  <h3 className="text-xs font-medium uppercase tracking-[0.12em] text-[rgba(235,235,235,0.38)]">
                    {section}
                  </h3>
                  <ul className="flex flex-col gap-2">
                    {links.map((link) => (
                      <li key={link.label}>
                        {link.href.startsWith("http") ? (
                          <a
                            href={link.href}
                            target={link.target}
                            rel={link.target === "_blank" ? "noopener noreferrer" : undefined}
                            className="text-sm text-[rgba(235,235,235,0.62)] hover:text-[#EBEBEB] transition-colors"
                          >
                            {link.label}
                          </a>
                        ) : (
                          <Link
                            to={link.href}
                            className="text-sm text-[rgba(235,235,235,0.62)] hover:text-[#EBEBEB] transition-colors"
                          >
                            {link.label}
                          </Link>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>
              </AnimatedContainer>
            )
          )}
        </div>

        {/* Bottom bar */}
        <AnimatedContainer delay={0.35}>
          <div className="mt-12 pt-6 border-t border-[rgba(235,235,235,0.08)] flex flex-col sm:flex-row justify-between items-center gap-4">
            <p className="text-xs text-[rgba(235,235,235,0.38)]">
              © {year} REGU. All rights reserved.
            </p>
            <p className="text-xs text-[rgba(235,235,235,0.38)]">
              Not legal advice.{" "}
              <Link to="/privacy" className="underline hover:text-[rgba(235,235,235,0.62)] transition-colors">
                Privacy Policy
              </Link>
            </p>
          </div>
        </AnimatedContainer>
      </div>
    </footer>
  );
}
