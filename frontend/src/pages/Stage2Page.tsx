/**
 * Stage2Page — document-driven analysis, chat-style layout.
 *
 * Viewport-filling surface with atmospheric background, sticky top bar,
 * chat message log, and a bottom input dock with three modes.
 * Sections 1–9 are driven by the URL param `:n` and the backend state.
 */

import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ShieldCheck, FileText, ChevronDown, WifiOff } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { FileDrop } from "@/components/ui/file-drop";
import { StepIndicator } from "@/components/ui/step-indicator";
import { StatusChip, severityToVariant } from "@/components/ui/status-chip";
import { ElegantShape } from "@/components/ui/shape-landing-hero";
import { cn } from "@/lib/utils";
import {
  useInterviewStatus,
  useStartStage2,
  useStage2Status,
  useUploadDocument,
  useSubmitDocument,
  useSubmitQA,
  useGenerateReport,
} from "@/lib/queries";
import { SECTION_QUICK_ACTIONS } from "@/lib/constants";
import type { SectionStatusDto, GapAnalysisResponse } from "@/lib/api";
import { getErrorMessage } from "@/lib/api";

// ── Types ───────────────────────────────────────────────────────────────────

interface ChatMessage {
  id: string;
  role: "system" | "user";
  timestamp: Date;
  content: string;
  /** Structured gap analysis attached to a system message */
  gapAnalysis?: GapAnalysisResponse;
  /** File reference for user upload messages */
  file?: { name: string; size: number };
  /** Is this an error message? */
  isError?: boolean;
  /** Retry callback for error messages */
  onRetry?: () => void;
  /** Show a "Continue" button */
  showContinue?: boolean;
}

type InputMode = "upload" | "paste" | "qa";

// ── Helpers ─────────────────────────────────────────────────────────────────

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function sectionStatusLabel(status: string): string {
  switch (status) {
    case "analyzed": return "Complete";
    case "submitted": return "In progress";
    default: return "Not started";
  }
}

function sectionStatusVariant(status: string) {
  switch (status) {
    case "analyzed": return "resolved" as const;
    case "submitted": return "minor" as const;
    default: return "neutral" as const;
  }
}

let msgIdCounter = 0;
function nextMsgId() { return `msg-${++msgIdCounter}-${Date.now()}`; }

// ── Component ───────────────────────────────────────────────────────────────

export default function Stage2Page() {
  const { id: sessionId, n: sectionParam } = useParams<{ id: string; n?: string }>();
  const navigate = useNavigate();

  const [currentSection, setCurrentSection] = useState(parseInt(sectionParam || "1", 10));
  const [sections, setSections] = useState<SectionStatusDto[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputMode, setInputMode] = useState<InputMode>("upload");
  const [pasteText, setPasteText] = useState("");
  const [qaText, setQaText] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isPending, setIsPending] = useState(false);
  const [sectionDropdownOpen, setSectionDropdownOpen] = useState(false);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [exitDialogOpen, setExitDialogOpen] = useState(false);
  const [sectionComplete, setSectionComplete] = useState(false);

  const chatEndRef = useRef<HTMLDivElement>(null);
  const chatContainerRef = useRef<HTMLDivElement>(null);

  const startStage2 = useStartStage2(sessionId ?? "");
  const uploadDoc = useUploadDocument(sessionId ?? "");
  const submitDoc = useSubmitDocument(sessionId ?? "");
  const submitQA = useSubmitQA(sessionId ?? "");
  const generateReport = useGenerateReport(sessionId ?? "");
  const { data: interviewStatus } = useInterviewStatus(sessionId);

  // ── Resumability ──────────────────────────────────────────────────────
  useEffect(() => {
    if (!interviewStatus || !sessionId) return;
    if (interviewStatus.stage < 2 && interviewStatus.riskClassification === "pending") {
      navigate(`/app/session/${sessionId}/stage1`, { replace: true });
    }
  }, [interviewStatus, sessionId, navigate]);

  // ── Network status ────────────────────────────────────────────────────
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

  // ── Initialize Stage 2 ───────────────────────────────────────────────
  useEffect(() => {
    if (!sessionId || sections.length > 0) return;

    startStage2.mutateAsync().then((res) => {
      setSections(res.sections);
      // Generate intro message for first section
      const sec = res.sections.find((s) => s.sectionNumber === currentSection);
      if (sec) {
        addIntroMessage(sec.sectionNumber, sec.sectionTitle);
      }
    }).catch((err) => {
      addMessage({
        role: "system",
        content: getErrorMessage(err, "Failed to initialize Stage 2."),
        isError: true,
        onRetry: () => window.location.reload(),
      });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  // ── URL sync ──────────────────────────────────────────────────────────
  useEffect(() => {
    const n = parseInt(sectionParam || "1", 10);
    if (n !== currentSection && n >= 1 && n <= 9) {
      setCurrentSection(n);
      setMessages([]);
      setSectionComplete(false);
      const sec = sections.find((s) => s.sectionNumber === n);
      if (sec) addIntroMessage(sec.sectionNumber, sec.sectionTitle);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sectionParam]);

  // ── Scroll to bottom on new message ───────────────────────────────────
  useEffect(() => {
    if (!chatContainerRef.current || !chatEndRef.current) return;
    const container = chatContainerRef.current;
    const bottomThreshold = 200;
    const isNearBottom =
      container.scrollHeight - container.scrollTop - container.clientHeight < bottomThreshold;
    if (isNearBottom) {
      chatEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages]);

  // ── Message helpers ───────────────────────────────────────────────────
  function addMessage(base: Omit<ChatMessage, "id" | "timestamp">) {
    setMessages((prev) => [...prev, { ...base, id: nextMsgId(), timestamp: new Date() }]);
  }

  function addIntroMessage(sectionNumber: number, sectionTitle: string) {
    addMessage({
      role: "system",
      content: `Section ${sectionNumber} of 9 \u2014 ${sectionTitle}\n\nAnnex IV of the EU AI Act requires providers of high-risk systems to document this area. You can proceed in one of three ways:\n\n1. Upload a document (PDF, DOCX, or TXT, up to 10 MB) that covers this area.\n2. Paste the relevant text directly.\n3. Answer a short set of structured questions if you do not have a document yet.\n\nWhichever you choose, we will analyze coverage against Annex IV requirements and identify gaps.`,
    });
  }

  function addGapMessage(result: GapAnalysisResponse, sectionNum: number) {
    const complete = result.compliancePercentage >= 0; // backend always returns a percentage
    addMessage({
      role: "system",
      content: `Analyzed. ${result.metRequirements} of ${result.totalRequirements} requirements met; ${result.gapCount} gaps identified.`,
      gapAnalysis: result,
    });

    // Mark section as complete: backend returns analyzed status
    const sectionStatus = sections.find((s) => s.sectionNumber === sectionNum);
    if (sectionStatus) {
      setSections((prev) =>
        prev.map((s) =>
          s.sectionNumber === sectionNum
            ? { ...s, status: "analyzed", compliancePercentage: result.compliancePercentage }
            : s
        )
      );
    }

    // Check if all sections are now complete
    const updatedSections = sections.map((s) =>
      s.sectionNumber === sectionNum ? { ...s, status: "analyzed" } : s
    );
    const allComplete = updatedSections.every((s) => s.status === "analyzed");

    if (allComplete) {
      handleAllSectionsComplete();
    } else {
      setSectionComplete(true);
      const nextSec = sectionNum < 9 ? sectionNum + 1 : null;
      if (nextSec) {
        addMessage({
          role: "system",
          content: `Section ${sectionNum} complete. Ready to continue to section ${nextSec}.`,
          showContinue: true,
        });
      }
    }
  }

  async function handleAllSectionsComplete() {
    addMessage({
      role: "system",
      content: "Documentation review complete. Generating your compliance report...",
    });
    try {
      await generateReport.mutateAsync();
      navigate(`/app/session/${sessionId}/report`);
    } catch (err) {
      addMessage({
        role: "system",
        content: getErrorMessage(err, "Report generation failed."),
        isError: true,
        onRetry: () => handleAllSectionsComplete(),
      });
    }
  }

  // ── Submit handlers ───────────────────────────────────────────────────
  async function handleUpload() {
    if (!selectedFile || !sessionId) return;
    setIsPending(true);

    addMessage({
      role: "user",
      content: selectedFile.name,
      file: { name: selectedFile.name, size: selectedFile.size },
    });

    try {
      const result = await uploadDoc.mutateAsync({
        sectionNumber: currentSection,
        file: selectedFile,
      });
      setSelectedFile(null);
      addGapMessage(result, currentSection);
    } catch (err) {
      const payload = selectedFile;
      addMessage({
        role: "system",
        content: getErrorMessage(err, "Document analysis failed."),
        isError: true,
        onRetry: () => {
          setSelectedFile(payload);
          handleUpload();
        },
      });
    } finally {
      setIsPending(false);
    }
  }

  async function handlePaste() {
    if (pasteText.trim().length < 10 || !sessionId) return;
    setIsPending(true);

    const text = pasteText.trim();
    addMessage({ role: "user", content: text.length > 300 ? text.slice(0, 300) + "..." : text });

    try {
      const result = await submitDoc.mutateAsync({
        sectionNumber: currentSection,
        documentText: text,
      });
      setPasteText("");
      addGapMessage(result, currentSection);
    } catch (err) {
      addMessage({
        role: "system",
        content: getErrorMessage(err, "Document analysis failed."),
        isError: true,
        onRetry: () => handlePaste(),
      });
    } finally {
      setIsPending(false);
    }
  }

  async function handleQA() {
    if (qaText.trim().length < 3 || !sessionId) return;
    setIsPending(true);

    const answer = qaText.trim();
    addMessage({ role: "user", content: answer });

    try {
      // Judgement call: requirement tracking is server-side.
      // For Q&A flow, we send a generic requirementId; the backend assigns.
      const result = await submitQA.mutateAsync({
        sectionNumber: currentSection,
        requirementId: `S${currentSection}-QA`,
        answer,
      });
      setQaText("");

      if (result) {
        // Got a GapAnalysisResponse — section is complete
        addGapMessage(result, currentSection);
      } else {
        // 204 — more questions remain
        addMessage({
          role: "system",
          content: "Noted. Please provide more detail or continue with the next question.",
        });
      }
    } catch (err) {
      addMessage({
        role: "system",
        content: getErrorMessage(err, "Failed to process answer."),
        isError: true,
        onRetry: () => handleQA(),
      });
    } finally {
      setIsPending(false);
    }
  }

  function handleContinueToNextSection() {
    const next = currentSection + 1;
    if (next > 9) return;
    setSectionComplete(false);
    navigate(`/app/session/${sessionId}/stage2/${next}`);
  }

  function handleQuickAction(text: string) {
    if (inputMode === "paste") {
      setPasteText(text);
    } else {
      setInputMode("qa");
      setQaText(text);
    }
  }

  // ── Guard ─────────────────────────────────────────────────────────────
  if (!sessionId) {
    navigate("/app", { replace: true });
    return null;
  }

  const currentSectionData = sections.find((s) => s.sectionNumber === currentSection);
  const quickActions = SECTION_QUICK_ACTIONS[currentSection] || [];
  const formatTime = (d: Date) => d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  return (
    <div className="flex flex-col h-screen bg-[var(--color-regu-bg)] overflow-hidden">
      {/* ── Atmospheric background ────────────────────────────────────── */}
      <div className="fixed inset-0 pointer-events-none" aria-hidden>
        {/* Layer 1: radial gradient */}
        <div
          className="absolute inset-0"
          style={{
            background:
              "radial-gradient(40% 60% at 25% 15%, rgba(42,82,190,0.14), transparent 70%)",
          }}
        />
        {/* Layer 2: noise texture */}
        <svg className="absolute inset-0 w-full h-full opacity-[0.03]" aria-hidden>
          <filter id="noise">
            <feTurbulence baseFrequency="0.9" numOctaves={2} stitchTiles="stitch" />
          </filter>
          <rect width="100%" height="100%" filter="url(#noise)" />
        </svg>
        {/* Layer 3: single slow-drifting shape */}
        <ElegantShape
          delay={0}
          width={400}
          height={100}
          rotate={15}
          gradient="from-[#2A52BE]/[0.08]"
          className="top-1/3 -right-32"
        />
      </div>

      {/* ── Top bar ───────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-30 border-b border-[var(--color-regu-border)] bg-[var(--color-regu-bg)]/90 backdrop-blur-xl">
        {/* Network offline banner */}
        {!isOnline && (
          <div className="flex items-center justify-center gap-2 bg-[var(--color-regu-warn)]/10 px-4 py-1.5 text-xs text-[var(--color-regu-warn)]">
            <WifiOff size={12} aria-hidden />
            Connection lost. Reconnecting...
          </div>
        )}

        <div className="flex items-center justify-between px-6 py-3">
          {/* Wordmark */}
          <Link
            to="/"
            className="flex items-center gap-2"
            aria-label="REGU home"
          >
            <ShieldCheck size={18} className="text-[var(--color-regu-accent)]" aria-hidden />
            <span className="text-[var(--color-regu-fg)] font-semibold text-base font-[family-name:var(--font-heading)] tracking-tight">
              Regu
            </span>
          </Link>

          {/* Step indicator */}
          <StepIndicator currentStep={2} />

          {/* Exit */}
          <button
            onClick={() => setExitDialogOpen(true)}
            className="text-xs text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg-muted)] transition-colors"
            type="button"
          >
            Exit analysis
          </button>
        </div>

        {/* Section row */}
        <div className="flex items-center justify-between px-6 py-2 border-t border-[var(--color-regu-border)]/50 text-sm">
          <span className="text-[var(--color-regu-fg-muted)]">
            Section {currentSection} of 9
            {currentSectionData && ` \u2014 ${currentSectionData.sectionTitle}`}
          </span>
          <div className="relative">
            <button
              onClick={() => setSectionDropdownOpen(!sectionDropdownOpen)}
              className="flex items-center gap-1 text-xs text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg-muted)] transition-colors"
              type="button"
            >
              Jump to section
              <ChevronDown size={12} className={cn("transition-transform", sectionDropdownOpen && "rotate-180")} aria-hidden />
            </button>
            {sectionDropdownOpen && (
              <div className="absolute right-0 top-full mt-2 w-72 rounded-xl border border-[var(--color-regu-border-hi)] bg-[var(--color-regu-surface)] shadow-2xl z-40 py-1">
                {sections.map((s) => (
                  <button
                    key={s.sectionNumber}
                    onClick={() => {
                      setSectionDropdownOpen(false);
                      navigate(`/app/session/${sessionId}/stage2/${s.sectionNumber}`);
                    }}
                    className={cn(
                      "w-full text-left px-4 py-2.5 text-sm flex items-center justify-between",
                      "hover:bg-[var(--color-regu-surface-2)] transition-colors",
                      s.sectionNumber === currentSection && "bg-[var(--color-regu-surface-2)]",
                    )}
                  >
                    <span className="text-[var(--color-regu-fg)]">
                      {s.sectionNumber}. {s.sectionTitle}
                    </span>
                    <StatusChip variant={sectionStatusVariant(s.status)}>
                      {sectionStatusLabel(s.status)}
                    </StatusChip>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </header>

      {/* ── Report generation progress bar ────────────────────────────── */}
      {generateReport.isPending && (
        <div className="fixed top-0 left-0 right-0 z-50 h-0.5 bg-[var(--color-regu-accent)]/20">
          <div className="h-full bg-[var(--color-regu-accent)] animate-[indeterminate_1.5s_ease-in-out_infinite]" />
        </div>
      )}

      {/* ── Chat area ─────────────────────────────────────────────────── */}
      <div
        ref={chatContainerRef}
        className="flex-1 overflow-y-auto relative z-10"
        role="log"
        aria-live="polite"
        aria-relevant="additions"
      >
        <div className="mx-auto max-w-[860px] px-6 py-8 space-y-6">
          {messages.map((msg) => (
            <MessageRow key={msg.id} message={msg} formatTime={formatTime} onContinue={handleContinueToNextSection} />
          ))}
          {isPending && (
            <div className="flex items-center gap-3 text-sm text-[var(--color-regu-fg-muted)]">
              <span>Analyzing document...</span>
              <div className="h-0.5 w-32 rounded-full bg-[var(--color-regu-border)] overflow-hidden">
                <div className="h-full w-1/2 bg-[var(--color-regu-accent)] animate-[indeterminate_1.5s_ease-in-out_infinite] rounded-full" />
              </div>
            </div>
          )}
          <div ref={chatEndRef} />
        </div>
      </div>

      {/* ── Bottom input dock ─────────────────────────────────────────── */}
      {!sectionComplete && (
        <div className="sticky bottom-0 z-20 border-t border-[var(--color-regu-border)] bg-[var(--color-regu-surface)]/80 backdrop-blur-md">
          <div className="mx-auto max-w-[860px] px-6 py-4">
            {/* Quick action chips */}
            {quickActions.length > 0 && (
              <div className="flex flex-wrap gap-2 mb-3">
                {quickActions.map((action) => (
                  <button
                    key={action}
                    onClick={() => handleQuickAction(action)}
                    className="rounded-full border border-[var(--color-regu-border)] bg-[var(--color-regu-surface-2)] px-3 py-1.5 text-xs text-[var(--color-regu-fg-muted)] hover:border-[var(--color-regu-accent)]/40 hover:text-[var(--color-regu-fg)] transition-colors"
                    type="button"
                  >
                    {action}
                  </button>
                ))}
              </div>
            )}

            {/* Mode tabs */}
            <div className="flex gap-1 mb-3">
              {(["upload", "paste", "qa"] as InputMode[]).map((mode) => (
                <button
                  key={mode}
                  onClick={() => setInputMode(mode)}
                  className={cn(
                    "px-3 py-1.5 rounded-lg text-xs font-medium transition-colors",
                    inputMode === mode
                      ? "bg-[var(--color-regu-surface-2)] text-[var(--color-regu-fg)]"
                      : "text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg-muted)]",
                  )}
                  type="button"
                >
                  {mode === "upload" ? "Upload file" : mode === "paste" ? "Paste text" : "Answer questions"}
                </button>
              ))}
            </div>

            {/* Input area per mode */}
            {inputMode === "upload" && (
              <div className="flex flex-col gap-3">
                <FileDrop
                  onFile={(f) => setSelectedFile(f)}
                  disabled={isPending}
                />
                {selectedFile && (
                  <Button
                    variant="primary"
                    size="md"
                    onClick={handleUpload}
                    disabled={isPending}
                    className="self-end"
                  >
                    {isPending ? "Analyzing..." : "Analyze"}
                  </Button>
                )}
              </div>
            )}

            {inputMode === "paste" && (
              <div className="flex flex-col gap-3">
                <Textarea
                  value={pasteText}
                  onChange={(e) => setPasteText(e.target.value)}
                  placeholder="Paste your documentation text here..."
                  className="min-h-[140px] max-h-[40vh]"
                  disabled={isPending}
                />
                <Button
                  variant="primary"
                  size="md"
                  onClick={handlePaste}
                  disabled={isPending || pasteText.trim().length < 10}
                  className="self-end"
                >
                  {isPending ? "Analyzing..." : "Analyze"}
                </Button>
              </div>
            )}

            {inputMode === "qa" && (
              <div className="flex gap-3">
                <Textarea
                  value={qaText}
                  onChange={(e) => setQaText(e.target.value)}
                  placeholder="Type your answer..."
                  className="min-h-[60px] flex-1"
                  disabled={isPending}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      handleQA();
                    }
                  }}
                />
                <Button
                  variant="primary"
                  size="md"
                  onClick={handleQA}
                  disabled={isPending || qaText.trim().length < 3}
                  className="self-end"
                >
                  {isPending ? "Sending..." : "Send"}
                </Button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Exit confirmation dialog ──────────────────────────────────── */}
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

// ── MessageRow ──────────────────────────────────────────────────────────────

function MessageRow({
  message,
  formatTime,
  onContinue,
}: {
  message: ChatMessage;
  formatTime: (d: Date) => string;
  onContinue: () => void;
}) {
  const isSystem = message.role === "system";

  return (
    <div
      className={cn(
        "flex flex-col gap-1.5 max-w-[85%]",
        isSystem ? "self-start items-start" : "self-end items-end ml-auto",
        message.isError && "border-l-2 border-[var(--color-regu-danger)] pl-4",
      )}
    >
      {/* Label row */}
      <span className="text-[10px] uppercase tracking-[0.1em] text-[var(--color-regu-fg-subtle)]">
        {isSystem ? "REGU" : "You"} · {formatTime(message.timestamp)}
      </span>

      {/* File reference */}
      {message.file && (
        <div className="flex items-center gap-2 rounded-lg border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] px-3 py-2 text-sm">
          <FileText size={14} className="text-[var(--color-regu-fg-subtle)]" aria-hidden />
          <span className="text-[var(--color-regu-fg)]">{message.file.name}</span>
          <span className="text-[var(--color-regu-fg-subtle)]">{formatSize(message.file.size)}</span>
        </div>
      )}

      {/* Body */}
      <div
        className={cn(
          "text-sm leading-relaxed whitespace-pre-line",
          isSystem ? "text-[var(--color-regu-fg)]" : "text-[var(--color-regu-fg)]",
          message.isError && "text-[var(--color-regu-danger)]",
        )}
      >
        {message.content}
      </div>

      {/* Gap analysis table */}
      {message.gapAnalysis && (
        <GapTable analysis={message.gapAnalysis} />
      )}

      {/* Retry button */}
      {message.isError && message.onRetry && (
        <button
          onClick={message.onRetry}
          className="text-xs font-medium text-[var(--color-regu-danger)] underline underline-offset-2 hover:no-underline mt-1"
          type="button"
        >
          Retry
        </button>
      )}

      {/* Continue button */}
      {message.showContinue && (
        <Button
          variant="primary"
          size="sm"
          onClick={onContinue}
          className="mt-2"
        >
          Continue
        </Button>
      )}
    </div>
  );
}

// ── GapTable ────────────────────────────────────────────────────────────────

function GapTable({ analysis }: { analysis: GapAnalysisResponse }) {
  return (
    <div className="w-full rounded-xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface-2)] overflow-hidden mt-2">
      <table className="w-full text-xs">
        <thead>
          <tr className="border-b border-[var(--color-regu-border)]/50">
            <th className="text-left px-3 py-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
              Requirement
            </th>
            <th className="text-left px-3 py-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider">
              Status
            </th>
            <th className="text-left px-3 py-2 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider hidden sm:table-cell">
              Description
            </th>
          </tr>
        </thead>
        <tbody>
          {analysis.items.map((item) => (
            <tr key={item.requirementId} className="border-b border-[var(--color-regu-border)]/30 last:border-0">
              <td className="px-3 py-2 text-[var(--color-regu-fg)] font-mono text-[11px] whitespace-nowrap">
                {item.requirementId}
              </td>
              <td className="px-3 py-2">
                <StatusChip variant={item.found ? "resolved" : severityToVariant(item.severity)}>
                  {item.found ? "Met" : item.severity || "Gap"}
                </StatusChip>
              </td>
              <td className="px-3 py-2 text-[var(--color-regu-fg-muted)] hidden sm:table-cell">
                {item.found ? item.extractedValue : item.gapDescription}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
