import { cn } from "@/lib/utils";

const PRINCIPLES = [
  {
    title: "Citation mandatory",
    body: "Every claim in a REGU report traces back to a specific paragraph, recital, or annex point in the Act. If the engine cannot cite a source, it does not make the claim.",
  },
  {
    title: "Law-primary",
    body: "The legal text is authoritative. Commission guidance and curated use cases inform retrieval, but the regulation itself is the ground truth - never the engine's prior.",
  },
  {
    title: "Fail-safe, not fail-silent",
    body: "When the engine is uncertain, it surfaces a review_recommended flag rather than guessing. Compliance work is too consequential for confident-sounding hallucinations.",
  },
];

export default function Methodology() {
  return (
    <section
      id="methodology"
      className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]"
    >
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
          style={{ fontSize: "var(--text-micro)" }}
        >
          Our story
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16">
          <div className="lg:col-span-7">
            <h2
              className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
              style={{
                fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
                lineHeight: "1.05",
              }}
            >
              Why we built REGU.
            </h2>

            <div
              className="mt-10 space-y-6 text-[var(--ink-secondary)]"
              style={{
                fontSize: "var(--text-body-lg)",
                lineHeight: "1.65",
              }}
            >
              <p>
                The EU AI Act is 458 pages of densely cross-referenced legal
                text. The startups it most affects - small teams shipping
                AI features into the EU market - are the ones least equipped
                to read it. They have no compliance officer, no general
                counsel on retainer, and no time to learn what an Annex IV
                technical file is supposed to contain.
              </p>
              <p>
                Most "AI compliance" tools that exist today are either
                generic GRC platforms with the regulation pasted into a
                checklist, or marketing pages with a chatbot wrapped around
                GPT. Neither of those gives a founder a real answer to the
                only question that matters: <em>am I in trouble, and what
                do I actually need to do?</em> REGU is what we wanted to
                exist when we read the Act for the first time.
              </p>
            </div>
          </div>

          <div className="lg:col-span-5 lg:pl-6">
            <div className="flex flex-col">
              {PRINCIPLES.map((p, i) => (
                <div
                  key={p.title}
                  className={cn(
                    "py-6",
                    i > 0 && "border-t border-[var(--hairline)]"
                  )}
                >
                  <h3
                    className="font-semibold text-[var(--ink-primary)]"
                    style={{ fontSize: "1.0625rem", letterSpacing: "-0.01em" }}
                  >
                    {p.title}
                  </h3>
                  <p
                    className="mt-2 text-[var(--ink-secondary)]"
                    style={{ fontSize: "0.9375rem", lineHeight: "1.6" }}
                  >
                    {p.body}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
