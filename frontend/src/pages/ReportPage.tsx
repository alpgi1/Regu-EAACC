/**
 * ReportPage - the deliverable. Looks like a document you would attach
 * to an email to your legal counsel.
 *
 * Seven blocks: header, executive summary, classification rationale,
 * gap analysis (collapsible), obligations, citations, disclaimer.
 */

import { useState, useEffect } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { ShieldCheck, Download, Link2, Check, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { StatusChip, severityToVariant } from "@/components/ui/status-chip";
import { cn } from "@/lib/utils";
import { useGenerateReport, useReport, useCitations } from "@/lib/queries";
import type { CitationDto, GapAnalysisResponse } from "@/lib/api";
import { getErrorMessage } from "@/lib/api";

// ── Helpers ─────────────────────────────────────────────────────────────────

function riskBadgeColor(risk: string | undefined): string {
  switch (risk) {
    case "unacceptable": return "critical";
    case "high": return "major";
    case "limited":
    case "minimal": return "minor";
    default: return "neutral";
  }
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString("en-GB", {
      day: "numeric",
      month: "long",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

// ── Component ───────────────────────────────────────────────────────────────

export default function ReportPage() {
  const { id: sessionId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [reportId, setReportId] = useState<string | undefined>(undefined);
  const [copiedLink, setCopiedLink] = useState(false);
  const [pdfTooltip, setPdfTooltip] = useState(false);

  // Generate report if we don't have a reportId yet
  const generateReport = useGenerateReport(sessionId ?? "");

  // Trigger report generation on mount
  useEffect(() => {
    if (!sessionId) return;

    // Check if we arrived with a reportId in session storage (future optimisation)
    const stored = sessionStorage.getItem(`regu-report-${sessionId}`);
    if (stored) {
      setReportId(stored);
      return;
    }

    // Generate the report
    generateReport.mutateAsync().then((res) => {
      setReportId(res.reportId);
      sessionStorage.setItem(`regu-report-${sessionId}`, res.reportId);
    }).catch(() => {
      // Error handled in UI below
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  // Fetch the full report once we have an ID
  const { data: report, isLoading: reportLoading, error: reportError } = useReport(reportId);
  const { data: citations } = useCitations(reportId);

  // ── Copy link ─────────────────────────────────────────────────────────
  function copyLink() {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  }

  function handleDownloadPDF() {
    // Not yet implemented on the backend
    setPdfTooltip(true);
    setTimeout(() => setPdfTooltip(false), 2500);
  }

  // ── Guard ─────────────────────────────────────────────────────────────
  if (!sessionId) {
    navigate("/app", { replace: true });
    return null;
  }

  // Loading state
  if (generateReport.isPending || reportLoading) {
    return (
      <div className="min-h-screen bg-[var(--color-regu-bg)] flex flex-col items-center justify-center gap-4">
        <div className="h-0.5 w-48 rounded-full bg-[var(--color-regu-border)] overflow-hidden">
          <div className="h-full w-1/2 bg-[var(--color-regu-accent)] animate-[indeterminate_1.5s_ease-in-out_infinite] rounded-full" />
        </div>
        <p className="text-sm text-[var(--color-regu-fg-muted)]">
          Generating compliance report...
        </p>
      </div>
    );
  }

  // Error state
  if (generateReport.isError || reportError) {
    return (
      <div className="min-h-screen bg-[var(--color-regu-bg)] flex flex-col items-center justify-center gap-4 px-6">
        <p className="text-sm text-[var(--color-regu-danger)]">
          {getErrorMessage(generateReport.error, "Failed to generate report.")}
        </p>
        <Button variant="primary" size="md" onClick={() => generateReport.mutate()}>
          Retry
        </Button>
      </div>
    );
  }

  if (!report) return null;

  // ── Extract sections by title ─────────────────────────────────────────
  const classificationSection = report.sections.find(
    (s) => s.title.toLowerCase().includes("risk") || s.title.toLowerCase().includes("classification"),
  );
  const gapSection = report.sections.find(
    (s) => s.title.toLowerCase().includes("gap") || s.title.toLowerCase().includes("compliance gap"),
  );
  const obligationsSection = report.sections.find(
    (s) => s.title.toLowerCase().includes("obligation"),
  );
  const recommendationsSection = report.sections.find(
    (s) => s.title.toLowerCase().includes("recommendation"),
  );
  // Remaining sections that don't match the above
  const otherSections = report.sections.filter(
    (s) => s !== classificationSection && s !== gapSection && s !== obligationsSection && s !== recommendationsSection,
  );

  const allCitations = citations || report.citations || [];

  return (
    <div className="min-h-screen bg-[var(--color-regu-bg)]">
      {/* ── Top bar ───────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-30 border-b border-[var(--color-regu-border)] bg-[var(--color-regu-bg)]/90 backdrop-blur-xl">
        <div className="flex items-center justify-between px-6 py-3 max-w-[1100px] mx-auto">
          <Link to="/" className="flex items-center gap-2" aria-label="REGU home">
            <ShieldCheck size={18} className="text-[var(--color-regu-accent)]" aria-hidden />
            <span className="text-[var(--color-regu-fg)] font-semibold text-base font-[family-name:var(--font-heading)] tracking-tight">
              Regu
            </span>
          </Link>

          <span className="text-sm text-[var(--color-regu-fg-muted)] hidden sm:block">
            Analysis report
          </span>

          <div className="flex items-center gap-2">
            <div className="relative">
              <Button variant="primary" size="sm" onClick={handleDownloadPDF}>
                <Download size={14} aria-hidden />
                Download PDF
              </Button>
              {pdfTooltip && (
                <div className="absolute top-full right-0 mt-2 px-3 py-1.5 rounded-lg bg-[var(--color-regu-surface-2)] border border-[var(--color-regu-border)] text-xs text-[var(--color-regu-fg-muted)] whitespace-nowrap shadow-lg">
                  Export coming soon
                </div>
              )}
            </div>
            <Button variant="ghost" size="sm" onClick={copyLink}>
              {copiedLink ? <Check size={14} aria-hidden /> : <Link2 size={14} aria-hidden />}
              {copiedLink ? "Copied" : "Share link"}
            </Button>
          </div>
        </div>
      </header>

      <main id="main-content" className="py-8 px-6">
        {/* ── Document container ────────────────────────────────────── */}
        <div className="mx-auto max-w-[900px] bg-[var(--color-regu-surface)] border border-[var(--color-regu-border)] rounded-2xl px-8 md:px-12 py-12 md:py-16 shadow-lg">

          {/* ── Block 1: Header ───────────────────────────────────── */}
          <div className="mb-10 pb-8 border-b border-[var(--color-regu-border)]">
            <div className="flex flex-wrap items-center gap-3 text-xs text-[var(--color-regu-fg-subtle)] mb-4 font-mono">
              <span>Report ID: {report.reportId}</span>
              <span className="w-px h-3 bg-[var(--color-regu-border)]" aria-hidden />
              <span>Generated {formatDate(report.generatedAt)}</span>
            </div>
            {report.classification && (
              <StatusChip
                variant={riskBadgeColor(report.classification.riskCategory) as any}
                className="text-sm px-4 py-1"
              >
                {report.classification.riskCategory.replace("_", " ").toUpperCase()}
              </StatusChip>
            )}
          </div>

          {/* ── Block 2: Executive Summary ────────────────────────── */}
          <section className="mb-10">
            <h2 className="text-xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
              Executive summary
            </h2>
            <div className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed whitespace-pre-line">
              {report.summary || "No executive summary available."}
            </div>
          </section>

          {/* ── Block 3: Classification Rationale ─────────────────── */}
          {classificationSection && (
            <section className="mb-10">
              <h2 className="text-xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                Classification rationale
              </h2>
              <div className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed whitespace-pre-line">
                {renderWithCitations(classificationSection.content, allCitations)}
              </div>
            </section>
          )}

          {/* ── Block 4: Gap Analysis ─────────────────────────────── */}
          {(gapSection || report.gapAnalyses.length > 0) && (
            <section className="mb-10">
              <h2 className="text-xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                Gap analysis
              </h2>

              {report.gapAnalyses.length > 0 ? (
                <div className="space-y-2">
                  {report.gapAnalyses.map((ga, i) => (
                    <GapSection key={ga.sectionNumber} analysis={ga} defaultOpen={i === 0} />
                  ))}
                </div>
              ) : gapSection ? (
                <div className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed whitespace-pre-line">
                  {renderWithCitations(gapSection.content, allCitations)}
                </div>
              ) : null}
            </section>
          )}

          {/* ── Block 5: Obligations ──────────────────────────────── */}
          {obligationsSection && (
            <section className="mb-10">
              <h2 className="text-xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                Applicable obligations
              </h2>
              <div className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed whitespace-pre-line">
                {renderWithCitations(obligationsSection.content, allCitations)}
              </div>
            </section>
          )}

          {/* ── Other sections ────────────────────────────────────── */}
          {otherSections.map((sec) => (
            <section key={sec.title} className="mb-10">
              <h2 className="text-xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                {sec.title}
              </h2>
              <div className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed whitespace-pre-line">
                {renderWithCitations(sec.content, allCitations)}
              </div>
            </section>
          ))}

          {/* ── Block 6: Citations ────────────────────────────────── */}
          {allCitations.length > 0 && (
            <section className="mb-10" id="citations">
              <h2 className="text-xl font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                Citations
              </h2>
              <ol className="space-y-3 list-none">
                {allCitations.map((c, i) => (
                  <li
                    key={c.citationId}
                    id={`cite-${i + 1}`}
                    className="text-sm scroll-mt-24 target:bg-[var(--color-regu-accent)]/5 target:rounded-lg target:px-3 target:py-2 target:-mx-3 transition-colors duration-500"
                  >
                    <div className="flex items-baseline gap-2">
                      <span className="text-[var(--color-regu-accent)] font-mono text-xs font-semibold tabular-nums shrink-0">
                        [{i + 1}]
                      </span>
                      <div>
                        <p className="text-[var(--color-regu-fg)] leading-relaxed">
                          {c.snippet}
                        </p>
                        <p className="text-[10px] text-[var(--color-regu-fg-subtle)] font-mono mt-1">
                          Source: {c.sourceTable || "law"} / chunk {c.sourceChunkId}
                          {c.reference && ` / ${c.reference}`}
                        </p>
                      </div>
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          )}

          {/* ── Block 7: Disclaimer ───────────────────────────────── */}
          <footer className="pt-8 border-t border-[var(--color-regu-border)]">
            <p className="text-xs text-[var(--color-regu-fg-muted)] italic leading-relaxed mb-6">
              {report.disclaimer ||
                "This report is a structured first-pass analysis generated by REGU. It is not legal advice. Obtain qualified counsel before making compliance commitments or submissions to authorities."}
            </p>
            <div className="flex items-center gap-2">
              <ShieldCheck size={14} className="text-[var(--color-regu-fg-subtle)]" aria-hidden />
              <span className="text-xs text-[var(--color-regu-fg-subtle)] font-[family-name:var(--font-heading)] font-semibold tracking-tight">
                Regu
              </span>
            </div>
          </footer>
        </div>
      </main>
    </div>
  );
}

// ── GapSection (collapsible) ────────────────────────────────────────────────

function GapSection({ analysis, defaultOpen }: { analysis: GapAnalysisResponse; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <details open={defaultOpen} onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary className="flex items-center justify-between cursor-pointer rounded-xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface-2)] px-4 py-3 list-none [&::-webkit-details-marker]:hidden hover:bg-[var(--color-regu-surface-2)]/80 transition-colors">
        <div className="flex items-center gap-3">
          <ChevronDown
            size={14}
            className={cn("text-[var(--color-regu-fg-subtle)] transition-transform", open && "rotate-180")}
            aria-hidden
          />
          <span className="text-sm font-medium text-[var(--color-regu-fg)]">
            Section {analysis.sectionNumber}: {analysis.sectionTitle}
          </span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-[var(--color-regu-accent)] font-mono tabular-nums">
            {Math.round(analysis.compliancePercentage)}%
          </span>
          <StatusChip variant={analysis.gapCount === 0 ? "resolved" : "major"}>
            {analysis.gapCount === 0 ? "No gaps" : `${analysis.gapCount} gaps`}
          </StatusChip>
        </div>
      </summary>
      <div className="px-4 py-3 space-y-2">
        {analysis.items.map((item) => (
          <div
            key={item.requirementId}
            className="flex flex-col gap-1 rounded-lg border border-[var(--color-regu-border)]/50 bg-[var(--color-regu-surface)] p-3"
          >
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-[var(--color-regu-fg-subtle)]">
                {item.requirementId}
              </span>
              <StatusChip variant={item.found ? "resolved" : severityToVariant(item.severity)}>
                {item.found ? "Met" : item.severity || "Gap"}
              </StatusChip>
            </div>
            {!item.found && item.gapDescription && (
              <p className="text-xs text-[var(--color-regu-fg-muted)] leading-relaxed">
                {item.gapDescription}
              </p>
            )}
            {item.found && item.extractedValue && (
              <p className="text-xs text-[var(--color-regu-fg-muted)] leading-relaxed">
                {item.extractedValue}
              </p>
            )}
            {item.recommendation && (
              <p className="text-xs text-[var(--color-regu-fg-subtle)] italic">
                {item.recommendation}
              </p>
            )}
          </div>
        ))}
      </div>
    </details>
  );
}

// ── Citation rendering ──────────────────────────────────────────────────────

/**
 * Replace [N] references in text with clickable links to the citation footer.
 * Returns a React node.
 */
function renderWithCitations(text: string, citations: CitationDto[]): React.ReactNode {
  if (!citations.length) return text;

  const parts = text.split(/(\[\d+\])/g);
  return parts.map((part, i) => {
    const match = part.match(/^\[(\d+)\]$/);
    if (match) {
      const num = parseInt(match[1], 10);
      return (
        <a
          key={i}
          href={`#cite-${num}`}
          className="text-[var(--color-regu-accent)] hover:underline font-mono text-[11px] align-super"
          onClick={(e) => {
            e.preventDefault();
            const el = document.getElementById(`cite-${num}`);
            if (el) {
              el.scrollIntoView({ behavior: "smooth", block: "center" });
              // Brief highlight flash
              el.classList.add("target");
              setTimeout(() => el.classList.remove("target"), 1500);
            }
          }}
        >
          [{num}]
        </a>
      );
    }
    return <span key={i}>{part}</span>;
  });
}
