import { Link } from "react-router-dom";

export default function ComingSoon() {
  return (
    <main className="min-h-screen bg-[var(--bg-base)] flex items-center justify-center px-6">
      <div className="max-w-[480px] text-center flex flex-col items-center gap-6">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium"
          style={{ fontSize: "var(--text-micro)" }}
        >
          REGU
        </p>
        <h1
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{ fontSize: "clamp(2rem, 4.5vw, 3rem)", lineHeight: "1.05" }}
        >
          This page is{" "}
          <span className="font-serif italic font-normal">coming soon.</span>
        </h1>
        <p
          className="text-[var(--ink-secondary)]"
          style={{ fontSize: "var(--text-body-lg)", lineHeight: "1.55" }}
        >
          We're building it. For now, email{" "}
          <a
            href="mailto:founders@regu.eu"
            className="text-[var(--brand-400)] hover:underline"
          >
            founders@regu.eu
          </a>{" "}
          and we'll get back to you.
        </p>
        <Link
          to="/"
          className="mt-2 h-11 px-5 inline-flex items-center rounded-full text-[14px] font-medium border border-[var(--hairline-strong)] text-[var(--ink-primary)] hover:bg-[rgba(255,255,255,0.04)] transition-colors duration-[var(--dur-fast)]"
        >
          Return home
        </Link>
      </div>
    </main>
  );
}
