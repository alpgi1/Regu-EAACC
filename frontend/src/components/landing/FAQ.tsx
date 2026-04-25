import * as Accordion from "@radix-ui/react-accordion";
import { ChevronDown } from "lucide-react";

const ITEMS = [
  {
    q: "Is REGU a substitute for a lawyer?",
    a: "No. REGU produces a structured first-pass risk classification with citations back to the regulation. For sign-off, complex edge cases, conformity assessments, and regulator interaction, you still need an EU lawyer or a notified body. REGU's job is to make sure that conversation starts with the right questions.",
  },
  {
    q: "What does \"first-pass\" actually mean?",
    a: "REGU classifies your AI system into one of the Act's risk tiers — prohibited, high-risk, limited, or minimal — and identifies which articles, annexes, and obligations apply. For high-risk systems it walks you through Annex IV documentation gaps. It does not produce a conformity assessment or the technical file itself.",
  },
  {
    q: "What if I'm not based in the EU?",
    a: "The Act has extraterritorial reach. If your AI system is placed on the EU market, makes outputs used inside the EU, or affects people located in the EU, you are likely in scope as a provider, importer, distributor, or authorised representative. REGU asks the questions that determine which role applies.",
  },
  {
    q: "What happens to my system description after analysis?",
    a: "Your input is processed in-session to produce a report. Uploaded documents are converted to text via Apache Tika; the original files are not retained long-term. We do not train any model on your inputs and do not share them with third parties beyond the inference provider needed to run the analysis.",
  },
  {
    q: "How does REGU handle ambiguity in the regulation?",
    a: "When retrieved evidence is thin, contradictory, or the engine's internal confidence is low, REGU surfaces a `review_recommended` flag on the affected claim instead of producing a confident-sounding answer. This is the fail-safe principle — better to tell you something deserves a second look than to invent certainty.",
  },
  {
    q: "Why Gemini 2.5 Flash and not GPT-4 or Claude?",
    a: "Two reasons. First, latency: under-five-minute analysis requires a model with low time-to-first-token at long contexts. Second, structured output reliability: Gemini 2.5 Flash with a citation schema returns valid, parseable JSON consistently enough for our citation validator. We can swap models per-region if compliance or latency requirements change.",
  },
];

export default function FAQ() {
  return (
    <section className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]">
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
          style={{ fontSize: "var(--text-micro)" }}
        >
          Questions
        </p>

        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "26ch",
          }}
        >
          What founders ask before trying REGU.
        </h2>

        <div className="mt-12 max-w-[820px]">
          <Accordion.Root type="single" collapsible className="flex flex-col">
            {ITEMS.map((item, i) => (
              <Accordion.Item
                key={item.q}
                value={`item-${i}`}
                className="border-t border-[var(--hairline)] last:border-b"
              >
                <Accordion.Header className="m-0">
                  <Accordion.Trigger
                    className="group w-full flex items-center justify-between text-left py-6 gap-6 text-[var(--ink-primary)] hover:text-[var(--brand-400)] transition-colors duration-[var(--dur-fast)]"
                  >
                    <span
                      className="font-medium"
                      style={{
                        fontSize: "1.0625rem",
                        letterSpacing: "-0.01em",
                      }}
                    >
                      {item.q}
                    </span>
                    <ChevronDown
                      size={18}
                      className="flex-shrink-0 text-[var(--ink-tertiary)] transition-transform duration-[var(--dur-base)] group-data-[state=open]:rotate-180"
                      aria-hidden
                    />
                  </Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Content
                  className="overflow-hidden data-[state=open]:animate-[accordionDown_240ms_var(--ease-out)] data-[state=closed]:animate-[accordionUp_180ms_var(--ease-in-out)]"
                >
                  <p
                    className="pb-6 pr-10 text-[var(--ink-secondary)]"
                    style={{ fontSize: "0.9375rem", lineHeight: "1.65" }}
                  >
                    {item.a}
                  </p>
                </Accordion.Content>
              </Accordion.Item>
            ))}
          </Accordion.Root>
        </div>
      </div>
    </section>
  );
}
