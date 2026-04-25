type Cell = { value: string; tone?: "primary" | "secondary" | "tertiary" };

const ROWS: { label: string; regu: Cell; lawyer: Cell; ignore: Cell }[] = [
  {
    label: "First-pass timeline",
    regu: { value: "~5 min", tone: "primary" },
    lawyer: { value: "1–3 weeks" },
    ignore: { value: "0 — until enforcement" },
  },
  {
    label: "Cost",
    regu: { value: "Free preview", tone: "primary" },
    lawyer: { value: "€500–€1,500/hr" },
    ignore: { value: "Up to €35M fine" },
  },
  {
    label: "Citation depth",
    regu: { value: "Paragraph-level", tone: "primary" },
    lawyer: { value: "Paragraph-level" },
    ignore: { value: "—" },
  },
  {
    label: "Replaces a lawyer?",
    regu: { value: "No — first pass only", tone: "primary" },
    lawyer: { value: "Yes" },
    ignore: { value: "No — and the regulator notices" },
  },
  {
    label: "Best for",
    regu: { value: "Triage, scope, gap discovery", tone: "primary" },
    lawyer: { value: "Final sign-off, complex cases" },
    ignore: { value: "Companies who want to find out the hard way" },
  },
];

function ColorOfTone(tone?: Cell["tone"]) {
  switch (tone) {
    case "primary":
      return "var(--ink-primary)";
    case "tertiary":
      return "var(--ink-tertiary)";
    default:
      return "var(--ink-secondary)";
  }
}

export default function Comparison() {
  return (
    <section className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]">
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <p
          className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
          style={{ fontSize: "var(--text-micro)" }}
        >
          The tradeoff
        </p>

        <h2
          className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
          style={{
            fontSize: "clamp(2rem, 4.5vw, 3.5rem)",
            lineHeight: "1.05",
            maxWidth: "26ch",
          }}
        >
          Three ways to handle the AI Act.{" "}
          <span className="font-serif italic font-normal">
            Only one is honest about what it gives you.
          </span>
        </h2>

        {/* Desktop / tablet table */}
        <div className="mt-14 hidden md:block">
          <div className="relative">
            {/* REGU column highlight backdrop */}
            <div
              aria-hidden
              className="absolute inset-y-0 pointer-events-none rounded-2xl"
              style={{
                left: "calc(25%)",
                width: "calc(25%)",
                background: "rgba(61,90,254,0.06)",
                borderLeft: "1px solid var(--brand-500)",
                borderRight: "1px solid var(--brand-500)",
              }}
            />
            <table className="relative w-full text-left">
              <thead>
                <tr>
                  <th className="w-1/4" />
                  <th
                    className="w-1/4 py-5 px-5 font-semibold text-[var(--ink-primary)] text-[15px]"
                  >
                    REGU
                  </th>
                  <th className="w-1/4 py-5 px-5 font-medium text-[var(--ink-secondary)] text-[15px]">
                    External counsel
                  </th>
                  <th className="w-1/4 py-5 px-5 font-medium text-[var(--ink-tertiary)] text-[15px]">
                    Ignoring it
                  </th>
                </tr>
              </thead>
              <tbody>
                {ROWS.map((row) => (
                  <tr
                    key={row.label}
                    className="border-t border-[var(--hairline)]"
                  >
                    <td
                      className="py-5 px-5 align-top text-[var(--ink-tertiary)]"
                      style={{
                        fontSize: "var(--text-caption)",
                        fontWeight: 500,
                        letterSpacing: "0.04em",
                        textTransform: "uppercase",
                      }}
                    >
                      {row.label}
                    </td>
                    <td
                      className="py-5 px-5 align-top text-[15px]"
                      style={{ color: ColorOfTone(row.regu.tone) }}
                    >
                      {row.regu.value}
                    </td>
                    <td
                      className="py-5 px-5 align-top text-[var(--ink-secondary)] text-[15px]"
                    >
                      {row.lawyer.value}
                    </td>
                    <td
                      className="py-5 px-5 align-top text-[var(--ink-tertiary)] text-[15px]"
                    >
                      {row.ignore.value}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Mobile — three stacked cards */}
        <div className="mt-12 md:hidden flex flex-col gap-5">
          {[
            {
              header: "REGU",
              accent: true,
              cells: ROWS.map((r) => ({ label: r.label, value: r.regu.value })),
            },
            {
              header: "External counsel",
              accent: false,
              cells: ROWS.map((r) => ({ label: r.label, value: r.lawyer.value })),
            },
            {
              header: "Ignoring it",
              accent: false,
              tertiary: true,
              cells: ROWS.map((r) => ({ label: r.label, value: r.ignore.value })),
            },
          ].map((col) => (
            <div
              key={col.header}
              className="rounded-2xl p-6"
              style={{
                background: "var(--bg-elev-1)",
                border: col.accent
                  ? "1px solid var(--brand-500)"
                  : "1px solid var(--hairline)",
                boxShadow: col.accent
                  ? "inset 0 0 0 1px rgba(61,90,254,0.15)"
                  : "none",
              }}
            >
              <h3
                className="font-semibold text-[var(--ink-primary)] mb-4"
                style={{ fontSize: "1.125rem" }}
              >
                {col.header}
              </h3>
              <dl className="space-y-3">
                {col.cells.map((c) => (
                  <div key={c.label}>
                    <dt
                      className="text-[var(--ink-tertiary)] uppercase tracking-[0.08em]"
                      style={{ fontSize: "var(--text-micro)" }}
                    >
                      {c.label}
                    </dt>
                    <dd
                      className="mt-1 text-[15px]"
                      style={{
                        color: col.tertiary
                          ? "var(--ink-tertiary)"
                          : "var(--ink-primary)",
                      }}
                    >
                      {c.value}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          ))}
        </div>

        <p
          className="mt-10 text-[var(--ink-tertiary)] max-w-[68ch]"
          style={{ fontSize: "var(--text-caption)" }}
        >
          REGU is not legal advice. It's a starting point that turns "we have
          no idea where we stand" into "we know exactly what to ask our
          lawyer."
        </p>
      </div>
    </section>
  );
}
