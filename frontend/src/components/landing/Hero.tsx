import { Link } from "react-router-dom";
import { useReducedMotion } from "framer-motion";
import { ChevronRight, Play } from "lucide-react";
import { MeshGradient } from "@paper-design/shaders-react";
import { cn } from "@/lib/utils";
import Logo from "./Logo";
import { StartAnalysisButton } from "@/components/ui/start-analysis-button";

const STATIC_FALLBACK_BG =
  "radial-gradient(120% 80% at 30% 30%, #1A2452 0%, #0A1535 35%, #050814 75%)," +
  "radial-gradient(80% 60% at 80% 70%, rgba(61,90,254,0.35) 0%, transparent 60%)," +
  "linear-gradient(180deg, #050814 0%, #0F1729 100%)";

export default function Hero() {
  const reduce = useReducedMotion();

  return (
    <section
      id="hero"
      className="relative w-full overflow-hidden"
      style={{ minHeight: "min(100vh, 900px)" }}
      aria-labelledby="hero-heading"
    >
      {/* Background - shader or static fallback */}
      <div className="absolute inset-0" aria-hidden>
        {reduce ? (
          <div
            className="absolute inset-0"
            style={{ background: STATIC_FALLBACK_BG }}
          />
        ) : (
          <>
            <div
              className="absolute inset-0"
              style={{ background: "#050814" }}
            />
            <MeshGradient
              className="absolute inset-0 w-full h-full"
              colors={["#050814", "#0A1535", "#1A2452", "#3D5AFE", "#0F1729"]}
              speed={0.18}
              distortion={0.7}
              swirl={0.45}
            />
            <MeshGradient
              className="absolute inset-0 w-full h-full opacity-30 mix-blend-screen"
              colors={["#050814", "#3D5AFE", "#050814"]}
              speed={0.12}
              distortion={1}
              swirl={0.2}
            />
          </>
        )}
        {/* Vignette + bottom fade into next section */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "radial-gradient(120% 80% at 50% 30%, transparent 50%, rgba(5,8,20,0.55) 100%)",
          }}
        />
        <div
          className="absolute inset-x-0 bottom-0 h-40"
          style={{
            background:
              "linear-gradient(to bottom, transparent 0%, var(--bg-base) 100%)",
          }}
        />
      </div>

      {/* Foreground */}
      <div className="relative z-10 max-w-[1200px] mx-auto px-6 md:px-10 pt-32 md:pt-40 pb-24 md:pb-32 flex flex-col">
        {/* Logo mark */}
        <div className="self-start mb-12">
          <Logo size={64} />
        </div>

        {/* Headline - left-aligned */}
        <h1
          id="hero-heading"
          className="font-semibold tracking-[-0.03em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(3rem, 6.5vw, 5.5rem)",
            lineHeight: "1.02",
            maxWidth: "18ch",
          }}
        >
          Compliance,{" "}
          <span className="font-serif italic font-normal">grounded</span>{" "}
          in the Act.
          <br />
          For builders{" "}
          <span className="font-serif italic font-normal">without</span>{" "}
          compliance teams.
        </h1>

        {/* Sub-paragraph */}
        <p
          className="mt-8 text-[var(--ink-secondary)] font-light"
          style={{
            fontSize: "var(--text-body-lg)",
            lineHeight: "1.55",
            maxWidth: "52ch",
          }}
        >
          Structured first-pass risk classification against the EU AI Act -
          every claim cited to a specific paragraph, delivered in under five
          minutes.
        </p>

        {/* CTAs */}
        <div className="mt-10 flex flex-wrap gap-4">
          <StartAnalysisButton
            className={cn(
              "h-12 px-6 inline-flex items-center gap-2 rounded-full font-medium text-[15px]",
              "bg-[var(--brand-500)] text-white",
              "hover:bg-[var(--brand-400)] transition-colors duration-[var(--dur-fast)]"
            )}
          >
            Start Analysis
            <ChevronRight size={16} aria-hidden />
          </StartAnalysisButton>
          <Link
            to="/coming-soon"
            className={cn(
              "h-12 px-6 inline-flex items-center gap-2 rounded-full font-medium text-[15px]",
              "text-[var(--ink-primary)] border border-[var(--hairline-strong)]",
              "hover:bg-[rgba(255,255,255,0.04)] transition-colors duration-[var(--dur-fast)]"
            )}
          >
            <Play size={14} aria-hidden />
            Watch 90-second demo
          </Link>
        </div>

        {/* Corpus depth strip */}
        <div
          className="mt-10 flex flex-wrap items-center gap-x-3 gap-y-2 text-[var(--ink-tertiary)]"
          style={{ fontSize: "var(--text-caption)" }}
        >
          <span>885 paragraphs indexed</span>
          <span aria-hidden className="text-[var(--ink-quaternary)]">·</span>
          <span>113 articles</span>
          <span aria-hidden className="text-[var(--ink-quaternary)]">·</span>
          <span>13 annexes</span>
        </div>
      </div>
    </section>
  );
}
