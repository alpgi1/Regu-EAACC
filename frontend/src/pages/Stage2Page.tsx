import { useState, useEffect, useRef, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  ShieldCheck,
  FileText,
  CheckCircle2,
  WifiOff,
  X,
  AlertCircle,
  Loader2,
} from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { StepIndicator } from "@/components/ui/step-indicator";
import { StatusChip, severityToVariant } from "@/components/ui/status-chip";
import { ElegantShape } from "@/components/ui/shape-landing-hero";
import { cn } from "@/lib/utils";
import {
  useInterviewStatus,
  useStartStage2,
  useUploadDocument,
  useGenerateReport,
} from "@/lib/queries";
import type { GapAnalysisResponse } from "@/lib/api";
import { getErrorMessage } from "@/lib/api";

// ── Constants ────────────────────────────────────────────────────────────────

const REQUIRED_DOCUMENTS = [
  {
    number: 1,
    title: "Technical Specification / System Architecture",
    covers: "Intended purpose, architecture, development methods, training data (Annex IV §1–2)",
  },
  {
    number: 2,
    title: "Performance & Monitoring Documentation",
    covers: "Accuracy metrics, capabilities, limitations, input specs (Annex IV §3–4)",
  },
  {
    number: 3,
    title: "Risk Management Plan",
    covers: "Article 9 risk assessment and mitigation system (Annex IV §5)",
  },
  {
    number: 4,
    title: "Testing & Validation Records",
    covers: "Test logs, validation results, signed test reports (Annex IV §2)",
  },
  {
    number: 5,
    title: "Standards Compliance Documentation",
    covers: "Harmonised standards applied or alternative solutions (Annex IV §7)",
  },
  {
    number: 6,
    title: "EU Declaration of Conformity",
    covers: "Article 47 signed conformity declaration (Annex IV §8)",
  },
  {
    number: 7,
    title: "Post-Market Monitoring Plan",
    covers: "Article 72 monitoring obligations and procedures (Annex IV §9)",
  },
];

const SECTION_TITLES: Record<number, string> = {
  1: "General description of the AI system",
  2: "Detailed description of elements and development process",
  3: "Monitoring, functioning and control",
  4: "Appropriateness of performance metrics",
  5: "Risk management system",
  6: "Relevant changes through lifecycle",
  7: "Harmonised standards applied",
  8: "EU declaration of conformity",
  9: "Post-market monitoring",
};

const ACCEPTED_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
]);

const ACCEPTED_EXTENSIONS = new Set([".pdf", ".docx", ".txt"]);

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function validateFile(file: File): string | null {
  const ext = "." + file.name.split(".").pop()?.toLowerCase();
  if (!ACCEPTED_TYPES.has(file.type) && !ACCEPTED_EXTENSIONS.has(ext)) {
    return `"${file.name}" is not supported. Please upload PDF, DOCX, or TXT files.`;
  }
  if (file.size > 10 * 1024 * 1024) {
    return `"${file.name}" exceeds the 10 MB limit.`;
  }
  return null;
}

type Stage2Phase = "intro" | "reviewing" | "analyzing" | "done";

// ── Component ────────────────────────────────────────────────────────────────

export default function Stage2Page() {
  const { id: sessionId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [phase, setPhase] = useState<Stage2Phase>("intro");
  const [uploadedFiles, setUploadedFiles] = useState<File[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [currentSection, setCurrentSection] = useState(0);
  const [sectionResults, setSectionResults] = useState<Record<number, GapAnalysisResponse>>({});
  const [sectionErrors, setSectionErrors] = useState<Record<number, string>>({});
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [exitDialogOpen, setExitDialogOpen] = useState(false);
  const dragCounter = useRef(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const startStage2 = useStartStage2(sessionId ?? "");
  const uploadDoc = useUploadDocument(sessionId ?? "");
  const generateReport = useGenerateReport(sessionId ?? "");
  const { data: interviewStatus } = useInterviewStatus(sessionId);

  // ── Guard: must be stage 2 ─────────────────────────────────────────────
  useEffect(() => {
    if (!interviewStatus || !sessionId) return;
    if (interviewStatus.stage < 2 && interviewStatus.riskClassification === "pending") {
      navigate(`/app/session/${sessionId}/stage1`, { replace: true });
    }
  }, [interviewStatus, sessionId, navigate]);

  // ── Initialise Stage 2 on backend ──────────────────────────────────────
  useEffect(() => {
    if (!sessionId) return;
    startStage2.mutateAsync().catch(() => {
      // Stage 2 may already be started — ignore the error
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  // ── Network status ─────────────────────────────────────────────────────
  useEffect(() => {
    const onOnline = () => setIsOnline(true);
    const onOffline = () => setIsOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  // ── Scroll to bottom when results arrive ───────────────────────────────
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [sectionResults, phase]);

  // ── File handling ──────────────────────────────────────────────────────
  const addFiles = useCallback((incoming: FileList | File[]) => {
    setFileError(null);
    const valid: File[] = [];
    for (const file of Array.from(incoming)) {
      const err = validateFile(file);
      if (err) {
        setFileError(err);
        return;
      }
      valid.push(file);
    }
    setUploadedFiles((prev) => {
      const names = new Set(prev.map((f) => f.name));
      const deduped = valid.filter((f) => !names.has(f.name));
      return [...prev, ...deduped];
    });
    if (valid.length > 0) setPhase("reviewing");
  }, []);

  const removeFile = useCallback((name: string) => {
    setUploadedFiles((prev) => {
      const next = prev.filter((f) => f.name !== name);
      if (next.length === 0) setPhase("intro");
      return next;
    });
  }, []);

  const onDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current++;
    setIsDragging(true);
  }, []);

  const onDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    dragCounter.current--;
    if (dragCounter.current === 0) setIsDragging(false);
  }, []);

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      dragCounter.current = 0;
      if (e.dataTransfer.files.length > 0) addFiles(e.dataTransfer.files);
    },
    [addFiles],
  );

  const onInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (e.target.files && e.target.files.length > 0) addFiles(e.target.files);
      e.target.value = "";
    },
    [addFiles],
  );

  // ── Analysis ───────────────────────────────────────────────────────────
  async function runAnalysis() {
    if (uploadedFiles.length === 0 || !sessionId) return;
    setPhase("analyzing");
    setGlobalError(null);
    setSectionResults({});
    setSectionErrors({});

    // Use the largest file as the primary document for all sections
    const primaryFile = uploadedFiles.reduce((a, b) => (b.size > a.size ? b : a));

    let failCount = 0;

    for (let section = 1; section <= 9; section++) {
      setCurrentSection(section);
      try {
        const result = await uploadDoc.mutateAsync({
          sectionNumber: section,
          file: primaryFile,
        });
        setSectionResults((prev) => ({ ...prev, [section]: result }));
      } catch (err) {
        failCount++;
        setSectionErrors((prev) => ({
          ...prev,
          [section]: getErrorMessage(err, `Section ${section} analysis failed.`),
        }));
      }
    }

    setCurrentSection(0);

    if (failCount > 5) {
      setGlobalError(
        "More than 5 sections failed. Please check your connection and try again.",
      );
    }

    setPhase("done");
  }

  async function handleGenerateReport() {
    try {
      await generateReport.mutateAsync();
      navigate(`/app/session/${sessionId}/report`);
    } catch (err) {
      setGlobalError(getErrorMessage(err, "Report generation failed. Please try again."));
    }
  }

  // ── Guard ──────────────────────────────────────────────────────────────
  if (!sessionId) {
    navigate("/app", { replace: true });
    return null;
  }

  const doneCount = Object.keys(sectionResults).length;
  const totalGaps = Object.values(sectionResults).reduce((sum, r) => sum + r.gapCount, 0);
  const avgCompliance =
    doneCount > 0
      ? Math.round(
          Object.values(sectionResults).reduce((sum, r) => sum + r.compliancePercentage, 0) /
            doneCount,
        )
      : 0;

  return (
    <div className="flex flex-col h-screen bg-[var(--color-regu-bg)] overflow-hidden">
      {/* ── Atmospheric background ──────────────────────────────────────── */}
      <div className="fixed inset-0 pointer-events-none" aria-hidden>
        <div
          className="absolute inset-0"
          style={{
            background:
              "radial-gradient(40% 60% at 25% 15%, rgba(42,82,190,0.14), transparent 70%)",
          }}
        />
        <svg className="absolute inset-0 w-full h-full opacity-[0.03]" aria-hidden>
          <filter id="noise">
            <feTurbulence baseFrequency="0.9" numOctaves={2} stitchTiles="stitch" />
          </filter>
          <rect width="100%" height="100%" filter="url(#noise)" />
        </svg>
        <ElegantShape
          delay={0}
          width={400}
          height={100}
          rotate={15}
          gradient="from-[#2A52BE]/[0.08]"
          className="top-1/3 -right-32"
        />
      </div>

      {/* ── Top bar ─────────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-30 border-b border-[var(--color-regu-border)] bg-[var(--color-regu-bg)]/90 backdrop-blur-xl">
        {!isOnline && (
          <div className="flex items-center justify-center gap-2 bg-[var(--color-regu-warn)]/10 px-4 py-1.5 text-xs text-[var(--color-regu-warn)]">
            <WifiOff size={12} aria-hidden />
            Connection lost. Reconnecting...
          </div>
        )}
        <div className="flex items-center justify-between px-6 py-3">
          <Link to="/" className="flex items-center gap-2" aria-label="REGU home">
            <ShieldCheck size={18} className="text-[var(--color-regu-accent)]" aria-hidden />
            <span className="text-[var(--color-regu-fg)] font-semibold text-base font-[family-name:var(--font-heading)] tracking-tight">
              Regu
            </span>
          </Link>
          <StepIndicator currentStep={2} />
          <button
            onClick={() => setExitDialogOpen(true)}
            className="text-xs text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg-muted)] transition-colors"
            type="button"
          >
            Exit analysis
          </button>
        </div>
      </header>

      {/* ── Report generation progress bar ──────────────────────────────── */}
      {generateReport.isPending && (
        <div className="fixed top-0 left-0 right-0 z-50 h-0.5 bg-[var(--color-regu-accent)]/20">
          <div className="h-full bg-[var(--color-regu-accent)] animate-[indeterminate_1.5s_ease-in-out_infinite]" />
        </div>
      )}

      {/* ── Main scroll area ─────────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto relative z-10">
        <div className="mx-auto max-w-[720px] px-6 py-10 space-y-8">

          {/* ── AI intro bubble ─────────────────────────────────────────── */}
          <AiBubble>
            <p className="text-sm text-[var(--color-regu-fg)] mb-4">
              Your system has been classified as{" "}
              <span className="font-semibold text-[var(--color-regu-accent)]">high-risk</span>{" "}
              under the EU AI Act. To proceed, you need to provide technical documentation
              covering the areas below. Upload your documents and I'll analyze coverage
              against all 9 Annex IV requirements.
            </p>
            <ul className="space-y-2">
              {REQUIRED_DOCUMENTS.map((doc) => (
                <li key={doc.number} className="flex gap-3 text-sm">
                  <span className="shrink-0 w-5 h-5 rounded-full bg-[var(--color-regu-accent)]/15 text-[var(--color-regu-accent)] text-[10px] font-semibold flex items-center justify-center mt-0.5">
                    {doc.number}
                  </span>
                  <span>
                    <span className="text-[var(--color-regu-fg)] font-medium">{doc.title}</span>
                    <span className="text-[var(--color-regu-fg-subtle)] ml-1 text-xs">
                      — {doc.covers}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
            <p className="text-xs text-[var(--color-regu-fg-subtle)] mt-4">
              You can combine everything into a single comprehensive file or upload
              separate documents. PDF, DOCX, or TXT — up to 10 MB each.
            </p>
          </AiBubble>

          {/* ── Drop zone (visible in intro and reviewing phases) ─────────── */}
          {(phase === "intro" || phase === "reviewing") && (
            <div
              onDragEnter={onDragEnter}
              onDragLeave={onDragLeave}
              onDragOver={onDragOver}
              onDrop={onDrop}
              className={cn(
                "rounded-2xl border-2 border-dashed p-6 transition-colors duration-150",
                isDragging
                  ? "border-[var(--color-regu-accent)] bg-[var(--color-regu-surface-2)]"
                  : "border-[var(--color-regu-border)] bg-[var(--color-regu-surface)]",
              )}
            >
              {/* File list */}
              {uploadedFiles.length > 0 && (
                <ul className="space-y-2 mb-4">
                  {uploadedFiles.map((f) => (
                    <li
                      key={f.name}
                      className="flex items-center gap-3 rounded-xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface-2)] px-3 py-2.5 text-sm"
                    >
                      <FileText size={16} className="shrink-0 text-[var(--color-regu-fg-subtle)]" aria-hidden />
                      <span className="flex-1 truncate text-[var(--color-regu-fg)]">{f.name}</span>
                      <span className="shrink-0 text-xs text-[var(--color-regu-fg-subtle)]">{formatSize(f.size)}</span>
                      <button
                        onClick={() => removeFile(f.name)}
                        className="shrink-0 rounded p-1 text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg)] hover:bg-[var(--color-regu-surface)] transition-colors"
                        aria-label={`Remove ${f.name}`}
                        type="button"
                      >
                        <X size={13} aria-hidden />
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              {/* Drop prompt */}
              <button
                onClick={() => inputRef.current?.click()}
                className="w-full flex flex-col items-center gap-2 py-4 text-sm text-[var(--color-regu-fg-muted)] cursor-pointer"
                type="button"
              >
                <FileText size={22} className="text-[var(--color-regu-fg-subtle)]" aria-hidden />
                <span>
                  {uploadedFiles.length > 0 ? "Add more documents or " : "Drag and drop here, or "}
                  <span className="text-[var(--color-regu-accent)] underline underline-offset-2">
                    browse
                  </span>
                </span>
                <span className="text-xs text-[var(--color-regu-fg-subtle)]">PDF, DOCX, or TXT up to 10 MB</span>
              </button>

              {fileError && (
                <p className="mt-2 text-xs text-[var(--color-regu-danger)] text-center" role="alert">
                  {fileError}
                </p>
              )}

              <input
                ref={inputRef}
                type="file"
                multiple
                accept="application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                onChange={onInputChange}
                className="sr-only"
                tabIndex={-1}
                aria-hidden
              />
            </div>
          )}

          {/* ── AI reviewing bubble ──────────────────────────────────────── */}
          {phase === "reviewing" && uploadedFiles.length > 0 && (
            <AiBubble>
              <p className="text-sm text-[var(--color-regu-fg)] mb-4">
                I've received{" "}
                <span className="font-semibold">{uploadedFiles.length}</span>{" "}
                {uploadedFiles.length === 1 ? "document" : "documents"}. I'll analyze{" "}
                {uploadedFiles.length > 1 ? "the most comprehensive file " : "it "}
                against all 9 Annex IV requirements and identify any compliance gaps.
                Ready to start?
              </p>
              <div className="flex gap-3">
                <Button variant="primary" size="md" onClick={runAnalysis}>
                  Start Analysis
                </Button>
                <Button
                  variant="ghost"
                  size="md"
                  onClick={() => {
                    setUploadedFiles([]);
                    setPhase("intro");
                  }}
                >
                  Change Files
                </Button>
              </div>
            </AiBubble>
          )}

          {/* ── Analyzing phase ──────────────────────────────────────────── */}
          {(phase === "analyzing" || phase === "done") && (
            <>
              <AiBubble>
                <p className="text-sm text-[var(--color-regu-fg)]">
                  {phase === "analyzing"
                    ? "Analyzing your documentation against Annex IV..."
                    : `Analysis complete. ${doneCount} of 9 sections analyzed${
                        totalGaps > 0 ? `, ${totalGaps} gaps found` : ", no gaps found"
                      }.`}
                </p>
                {phase === "analyzing" && currentSection > 0 && (
                  <div className="flex items-center gap-3 mt-3">
                    <Loader2 size={14} className="text-[var(--color-regu-accent)] animate-spin" aria-hidden />
                    <span className="text-xs text-[var(--color-regu-fg-muted)]">
                      Section {currentSection} of 9 — {SECTION_TITLES[currentSection]}
                    </span>
                  </div>
                )}
              </AiBubble>

              {/* Section result cards */}
              <div className="space-y-3">
                {Array.from({ length: 9 }, (_, i) => i + 1).map((section) => {
                  const result = sectionResults[section];
                  const error = sectionErrors[section];
                  const isActive = phase === "analyzing" && currentSection === section;
                  const isPast = result || error;

                  if (!isPast && !isActive) return null;

                  return (
                    <SectionCard
                      key={section}
                      section={section}
                      title={SECTION_TITLES[section]}
                      result={result}
                      error={error}
                      isActive={isActive}
                    />
                  );
                })}
              </div>

              {/* Global error */}
              {globalError && (
                <div className="flex items-start gap-3 rounded-xl border border-[var(--color-regu-danger)]/30 bg-[var(--color-regu-danger)]/5 px-4 py-3">
                  <AlertCircle size={16} className="shrink-0 text-[var(--color-regu-danger)] mt-0.5" aria-hidden />
                  <p className="text-sm text-[var(--color-regu-danger)]">{globalError}</p>
                </div>
              )}

              {/* Done CTA */}
              {phase === "done" && (
                <AiBubble>
                  <p className="text-sm text-[var(--color-regu-fg)] mb-4">
                    {doneCount > 0
                      ? `Documentation review complete. Average compliance: ${avgCompliance}%. Ready to generate your full compliance report?`
                      : "The analysis encountered errors. Please try again or contact support."}
                  </p>
                  {doneCount > 0 && (
                    <div className="flex gap-3">
                      <Button
                        variant="primary"
                        size="md"
                        onClick={handleGenerateReport}
                        disabled={generateReport.isPending}
                      >
                        {generateReport.isPending ? (
                          <span className="flex items-center gap-2">
                            <Loader2 size={14} className="animate-spin" aria-hidden />
                            Generating...
                          </span>
                        ) : (
                          "Generate Report"
                        )}
                      </Button>
                      <Button
                        variant="ghost"
                        size="md"
                        onClick={() => {
                          setUploadedFiles([]);
                          setSectionResults({});
                          setSectionErrors({});
                          setGlobalError(null);
                          setPhase("intro");
                        }}
                      >
                        Upload New Documents
                      </Button>
                    </div>
                  )}
                  {doneCount === 0 && (
                    <Button
                      variant="primary"
                      size="md"
                      onClick={() => {
                        setSectionResults({});
                        setSectionErrors({});
                        setGlobalError(null);
                        setPhase("reviewing");
                      }}
                    >
                      Try Again
                    </Button>
                  )}
                </AiBubble>
              )}
            </>
          )}

          <div ref={bottomRef} />
        </div>
      </div>

      {/* ── Exit dialog ──────────────────────────────────────────────────── */}
      {exitDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--color-regu-bg)]/80 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-2xl border border-[var(--color-regu-border-hi)] bg-[var(--color-regu-surface)] p-6 shadow-2xl">
            <h3 className="text-lg font-semibold text-[var(--color-regu-fg)] mb-2 font-[family-name:var(--font-heading)]">
              Exit analysis
            </h3>
            <p className="text-sm text-[var(--color-regu-fg-muted)] mb-6 leading-relaxed">
              Progress is saved. You can return using your session link. Exit now?
            </p>
            <div className="flex gap-3 justify-end">
              <Button variant="ghost" size="sm" onClick={() => setExitDialogOpen(false)}>
                Cancel
              </Button>
              <Button variant="primary" size="sm" onClick={() => navigate("/")}>
                Exit
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── AiBubble ────────────────────────────────────────────────────────────────

function AiBubble({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[10px] uppercase tracking-[0.1em] text-[var(--color-regu-fg-subtle)]">
        REGU
      </span>
      <div className="rounded-2xl rounded-tl-sm border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] px-5 py-4">
        {children}
      </div>
    </div>
  );
}

// ── SectionCard ─────────────────────────────────────────────────────────────

function SectionCard({
  section,
  title,
  result,
  error,
  isActive,
}: {
  section: number;
  title: string;
  result: GapAnalysisResponse | undefined;
  error: string | undefined;
  isActive: boolean;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div
      className={cn(
        "rounded-xl border bg-[var(--color-regu-surface)] transition-colors duration-200",
        isActive
          ? "border-[var(--color-regu-accent)]/40"
          : result
            ? "border-[var(--color-regu-border)]"
            : "border-[var(--color-regu-danger)]/30",
      )}
    >
      <button
        onClick={() => result && setExpanded((v) => !v)}
        className={cn(
          "w-full flex items-center gap-3 px-4 py-3 text-left",
          result ? "cursor-pointer" : "cursor-default",
        )}
        type="button"
        disabled={!result}
      >
        {/* Status icon */}
        <div className="shrink-0">
          {isActive ? (
            <Loader2 size={16} className="text-[var(--color-regu-accent)] animate-spin" aria-hidden />
          ) : result ? (
            <CheckCircle2 size={16} className="text-[var(--color-regu-success,#22c55e)]" aria-hidden />
          ) : (
            <AlertCircle size={16} className="text-[var(--color-regu-danger)]" aria-hidden />
          )}
        </div>

        {/* Title */}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-[var(--color-regu-fg)] truncate">
            {section}. {title}
          </p>
          {isActive && (
            <p className="text-xs text-[var(--color-regu-fg-muted)]">Analyzing...</p>
          )}
          {error && (
            <p className="text-xs text-[var(--color-regu-danger)]">{error}</p>
          )}
        </div>

        {/* Compliance badge */}
        {result && (
          <div className="shrink-0 flex items-center gap-3">
            <span className="text-xs text-[var(--color-regu-fg-muted)]">
              {result.metRequirements}/{result.totalRequirements} met
            </span>
            <StatusChip
              variant={
                result.compliancePercentage >= 75
                  ? "resolved"
                  : result.compliancePercentage >= 40
                    ? "minor"
                    : "critical"
              }
            >
              {result.compliancePercentage}%
            </StatusChip>
          </div>
        )}
      </button>

      {/* Expanded gap table */}
      {expanded && result && (
        <div className="border-t border-[var(--color-regu-border)]/50 px-4 pb-4 pt-3">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-[var(--color-regu-border)]/50">
                <th className="text-left pb-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
                  Requirement
                </th>
                <th className="text-left pb-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
                  Status
                </th>
                <th className="text-left pb-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider hidden sm:table-cell">
                  Detail
                </th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((item) => (
                <tr
                  key={item.requirementId}
                  className="border-b border-[var(--color-regu-border)]/30 last:border-0"
                >
                  <td className="py-2 pr-3 text-[var(--color-regu-fg)] font-mono text-[11px] whitespace-nowrap">
                    {item.requirementId}
                  </td>
                  <td className="py-2 pr-3">
                    <StatusChip variant={item.found ? "resolved" : severityToVariant(item.severity)}>
                      {item.found ? "Met" : item.severity || "Gap"}
                    </StatusChip>
                  </td>
                  <td className="py-2 text-[var(--color-regu-fg-muted)] hidden sm:table-cell">
                    {item.found ? item.extractedValue : item.gapDescription}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
