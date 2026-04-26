import RagDiagram from "./RagDiagram";

const FEATURES = [
  {
    title: "Legal corpus (RAG)",
    detail: "885 paragraphs",
    body: "The full EU AI Act, chunked paragraph-by-paragraph, retrieved via hybrid vector + keyword search with reciprocal-rank fusion.",
  },
  {
    title: "Curated use cases",
    detail: "15+ scenarios",
    body: "Real-world AI system descriptions mapped to Annex III risk categories. Each scenario stays atomic - never split.",
  },
  {
    title: "Commission guidance",
    detail: "header-aware",
    body: "Official Commission interpretive documents, chunked with header context preserved so retrieval respects structure.",
  },
  {
    title: "Decision rules",
    detail: "40 procedural checks",
    body: "Procedural compliance logic adapted from the FLI Compliance Checker - one decision node per chunk.",
  },
];

export default function HowItWorks() {
  return (
    <section
      id="platform"
      className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]"
    >
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
          style={{ fontSize: "var(--text-micro)" }}
        >
          How it works
        </p>

        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "20ch",
          }}
        >
          Grounded in the Act.{" "}
          <span className="font-serif italic font-normal">
            Not in guesswork.
          </span>
        </h2>

        <p
          className="mt-6 text-[var(--ink-secondary)] max-w-[62ch]"
          style={{ fontSize: "var(--text-body-lg)" }}
        >
          REGU is a retrieval-augmented compliance engine. Every claim it makes
          is cited back to a specific paragraph in the regulation.
        </p>

        {/* Diagram */}
        <div className="mt-16">
          <div
            className="rounded-2xl border border-[var(--hairline)] p-6 md:p-10"
            style={{
              background: "#070B1C",
              backgroundImage:
                "radial-gradient(60% 50% at 50% 0%, rgba(61,90,254,0.10) 0%, transparent 70%)",
            }}
          >
            <RagDiagram />
          </div>
        </div>

        {/* Features grid + accent card */}
        <div className="mt-16 grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div className="lg:col-span-9 grid grid-cols-1 md:grid-cols-2 gap-5">
            {FEATURES.map((f) => (
              <div
                key={f.title}
                className="rounded-2xl border border-[var(--hairline)] bg-[var(--bg-elev-1)] p-6 transition-colors duration-[var(--dur-base)] hover:border-[var(--hairline-strong)]"
              >
                <div className="flex items-baseline justify-between gap-4">
                  <h3
                    className="font-semibold text-[var(--ink-primary)]"
                    style={{ fontSize: "1rem", letterSpacing: "-0.01em" }}
                  >
                    {f.title}
                  </h3>
                  <span
                    className="text-[var(--ink-tertiary)] tabular-nums"
                    style={{
                      fontFamily: "var(--font-mono)",
                      fontSize: "var(--text-caption)",
                    }}
                  >
                    {f.detail}
                  </span>
                </div>
                <p
                  className="mt-3 text-[var(--ink-secondary)]"
                  style={{ fontSize: "0.9375rem", lineHeight: "1.6" }}
                >
                  {f.body}
                </p>
              </div>
            ))}
          </div>

          {/* Accent card - desktop only */}
          <div className="hidden lg:block lg:col-span-3">
            <div
              className="h-full rounded-2xl flex items-center justify-center p-8 text-center"
              style={{
                border: "1px solid rgba(61,90,254,0.3)",
                background:
                  "radial-gradient(80% 80% at 50% 50%, rgba(61,90,254,0.08) 0%, transparent 70%)",
              }}
            >
              <p
                className="font-semibold text-[var(--ink-primary)] tracking-tight"
                style={{ fontSize: "1.5rem", lineHeight: "1.2" }}
              >
                Cited.{" "}
                <span
                  className="font-serif italic font-normal"
                  style={{ color: "var(--brand-500)" }}
                >
                  Traceable.
                </span>
                <br />
                Auditable.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
