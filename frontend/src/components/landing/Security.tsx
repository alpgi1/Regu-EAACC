import { Check, X } from "lucide-react";

const KEEPS = [
  "Your system description, as text",
  "Optional uploaded PDF/DOCX (extracted to text via Tika)",
  "Your answers to interview questions",
  "Generated reports, scoped to your session",
];

const DOESNT = [
  "No training on your inputs",
  "No third-party analytics on input content",
  "No long-term retention of uploaded documents (extracted text only)",
  "No sharing your description with anyone",
];

export default function Security() {
  return (
    <section className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]">
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
          style={{ fontSize: "var(--text-micro)" }}
        >
          Data handling
        </p>

        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "22ch",
          }}
        >
          What we keep.{" "}
          <span className="font-serif italic font-normal">What we don't.</span>
        </h2>

        <div className="mt-12 grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="rounded-2xl border border-[var(--hairline)] bg-[var(--bg-elev-1)] p-8 transition-colors duration-[var(--dur-base)] hover:border-[var(--hairline-strong)]">
            <span
              className="inline-flex items-center px-2.5 py-1 rounded-full font-medium uppercase tracking-[0.08em] mb-6"
              style={{
                fontSize: "var(--text-micro)",
                background: "rgba(61,90,254,0.12)",
                color: "var(--brand-500)",
                border: "1px solid rgba(61,90,254,0.28)",
              }}
            >
              What REGU processes
            </span>
            <ul className="space-y-3">
              {KEEPS.map((item) => (
                <li
                  key={item}
                  className="flex gap-3 items-start text-[15px] text-[var(--ink-secondary)]"
                >
                  <Check
                    size={16}
                    aria-hidden
                    className="mt-1 flex-shrink-0"
                    style={{ color: "var(--brand-400)" }}
                  />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="rounded-2xl border border-[var(--hairline)] bg-[var(--bg-elev-1)] p-8 transition-colors duration-[var(--dur-base)] hover:border-[var(--hairline-strong)]">
            <span
              className="inline-flex items-center px-2.5 py-1 rounded-full font-medium uppercase tracking-[0.08em] mb-6"
              style={{
                fontSize: "var(--text-micro)",
                background: "rgba(60,203,127,0.12)",
                color: "var(--ok)",
                border: "1px solid rgba(60,203,127,0.28)",
              }}
            >
              What REGU does not do
            </span>
            <ul className="space-y-3">
              {DOESNT.map((item) => (
                <li
                  key={item}
                  className="flex gap-3 items-start text-[15px] text-[var(--ink-secondary)]"
                >
                  <X
                    size={16}
                    aria-hidden
                    className="mt-1 flex-shrink-0"
                    style={{ color: "var(--ok)" }}
                  />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-8 pt-6 border-t border-[var(--hairline)]">
          <p
            className="text-[var(--ink-tertiary)]"
            style={{ fontSize: "var(--text-caption)" }}
          >
            Inference runs on EU-region infrastructure. Built in the EU.
          </p>
        </div>
      </div>
    </section>
  );
}
