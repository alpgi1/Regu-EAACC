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
  Paperclip,
  Send,
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
  useStage2Chat,
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

// ── Types ────────────────────────────────────────────────────────────────────

type MessageRole = "ai" | "user";
type MessageKind = "text" | "files" | "analysis" | "error" | "done";

interface ChatMessage {
  id: string;
  role: MessageRole;
  kind: MessageKind;
  text?: string;
  files?: { name: string; size: number }[];
  sectionResults?: Record<number, GapAnalysisResponse>;
  sectionErrors?: Record<number, string>;
  onRetry?: () => void;
}

let _id = 0;
const nextId = () => `m${++_id}-${Date.now()}`;

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function validateFile(file: File): string | null {
  const ext = "." + file.name.split(".").pop()?.toLowerCase();
  if (!ACCEPTED_TYPES.has(file.type) && !ACCEPTED_EXTENSIONS.has(ext)) {
    return `"${file.name}" - PDF, DOCX veya TXT yükleyebilirsin.`;
  }
  if (file.size > 10 * 1024 * 1024) {
    return `"${file.name}" 10 MB sınırını aşıyor.`;
  }
  return null;
}

// ── Intro message ────────────────────────────────────────────────────────────

const INTRO_MESSAGE: ChatMessage = {
  id: "intro",
  role: "ai",
  kind: "text",
  text: "__INTRO__",
};

// ── Component ────────────────────────────────────────────────────────────────

export default function Stage2Page() {
  const { id: sessionId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [messages, setMessages] = useState<ChatMessage[]>([INTRO_MESSAGE]);
  const [inputText, setInputText] = useState("");
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [analyzingSection, setAnalyzingSection] = useState(0);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [exitDialogOpen, setExitDialogOpen] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const startStage2 = useStartStage2(sessionId ?? "");
  const uploadDoc = useUploadDocument(sessionId ?? "");
  const stage2Chat = useStage2Chat(sessionId ?? "");
  const generateReport = useGenerateReport(sessionId ?? "");
  const { data: interviewStatus } = useInterviewStatus(sessionId);

  // ── Redirect if not stage 2 ────────────────────────────────────────────
  useEffect(() => {
    if (!interviewStatus || !sessionId) return;
    if (interviewStatus.stage < 2 && interviewStatus.riskClassification === "pending") {
      navigate(`/app/session/${sessionId}/stage1`, { replace: true });
    }
  }, [interviewStatus, sessionId, navigate]);

  // ── Init Stage 2 on backend ────────────────────────────────────────────
  useEffect(() => {
    if (!sessionId) return;
    startStage2.mutateAsync().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  // ── Network ────────────────────────────────────────────────────────────
  useEffect(() => {
    const up = () => setIsOnline(true);
    const down = () => setIsOnline(false);
    window.addEventListener("online", up);
    window.addEventListener("offline", down);
    return () => { window.removeEventListener("online", up); window.removeEventListener("offline", down); };
  }, []);

  // ── Scroll to bottom ───────────────────────────────────────────────────
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, analyzingSection]);

  // ── Auto-resize textarea ───────────────────────────────────────────────
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [inputText]);

  // ── File attachment ────────────────────────────────────────────────────
  const attachFiles = useCallback((incoming: FileList | File[]) => {
    setFileError(null);
    for (const file of Array.from(incoming)) {
      const err = validateFile(file);
      if (err) { setFileError(err); return; }
    }
    setPendingFiles((prev) => {
      const names = new Set(prev.map((f) => f.name));
      return [...prev, ...Array.from(incoming).filter((f) => !names.has(f.name))];
    });
  }, []);

  const removeFile = (name: string) =>
    setPendingFiles((prev) => prev.filter((f) => f.name !== name));

  // ── Add message ────────────────────────────────────────────────────────
  const addMsg = (msg: Omit<ChatMessage, "id">) =>
    setMessages((prev) => [...prev, { ...msg, id: nextId() }]);

  // ── Analysis loop ──────────────────────────────────────────────────────
  async function runAnalysis(files: File[]) {
    const primaryFile = files.reduce((a, b) => (b.size > a.size ? b : a));
    const results: Record<number, GapAnalysisResponse> = {};
    const errors: Record<number, string> = {};

    setIsAnalyzing(true);

    for (let section = 1; section <= 9; section++) {
      setAnalyzingSection(section);
      try {
        const result = await uploadDoc.mutateAsync({ sectionNumber: section, file: primaryFile });
        results[section] = result;
      } catch (err) {
        errors[section] = getErrorMessage(err, `Section ${section} failed.`);
      }
    }

    setAnalyzingSection(0);
    setIsAnalyzing(false);

    const doneCount = Object.keys(results).length;
    addMsg({ role: "ai", kind: "analysis", sectionResults: results, sectionErrors: errors });

    if (doneCount > 0) {
      addMsg({
        role: "ai",
        kind: "done",
        text: `${doneCount} of 9 sections analyzed. Ready to generate your compliance report?`,
      });
    } else {
      addMsg({
        role: "ai",
        kind: "error",
        text: "The analysis encountered errors on all sections. Please try uploading again.",
      });
    }
  }

  // ── Send ───────────────────────────────────────────────────────────────
  async function handleSend() {
    if (isAnalyzing) return;
    const text = inputText.trim();
    const files = [...pendingFiles];
    if (!text && files.length === 0) return;

    setInputText("");
    setPendingFiles([]);
    setFileError(null);

    if (files.length > 0) {
      addMsg({
        role: "user",
        kind: "files",
        text: text || undefined,
        files: files.map((f) => ({ name: f.name, size: f.size })),
      });
      addMsg({
        role: "ai",
        kind: "text",
        text: `Received ${files.length} document${files.length > 1 ? "s" : ""}. Analyzing against all 9 Annex IV requirements...`,
      });
      await runAnalysis(files);
    } else {
      addMsg({ role: "user", kind: "text", text });
      try {
        const { answer } = await stage2Chat.mutateAsync({ question: text });
        addMsg({ role: "ai", kind: "text", text: answer });
      } catch (err) {
        addMsg({
          role: "ai",
          kind: "error",
          text: getErrorMessage(err, "Could not reach the AI assistant. Please try again."),
        });
      }
    }
  }

  async function handleGenerateReport() {
    try {
      await generateReport.mutateAsync();
      navigate(`/app/session/${sessionId}/report`);
    } catch (err) {
      addMsg({
        role: "ai",
        kind: "error",
        text: getErrorMessage(err, "Report generation failed. Please try again."),
      });
    }
  }

  if (!sessionId) { navigate("/app", { replace: true }); return null; }

  const canSend = !isAnalyzing && (inputText.trim().length > 0 || pendingFiles.length > 0);

  return (
    <div className="flex flex-col h-screen bg-[var(--color-regu-bg)] overflow-hidden">
      {/* ── Background ──────────────────────────────────────────────────── */}
      <div className="fixed inset-0 pointer-events-none" aria-hidden>
        <div className="absolute inset-0" style={{ background: "radial-gradient(40% 60% at 25% 15%, rgba(42,82,190,0.14), transparent 70%)" }} />
        <svg className="absolute inset-0 w-full h-full opacity-[0.03]" aria-hidden>
          <filter id="noise"><feTurbulence baseFrequency="0.9" numOctaves={2} stitchTiles="stitch" /></filter>
          <rect width="100%" height="100%" filter="url(#noise)" />
        </svg>
        <ElegantShape delay={0} width={400} height={100} rotate={15} gradient="from-[#2A52BE]/[0.08]" className="top-1/3 -right-32" />
      </div>

      {/* ── Header ──────────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-30 border-b border-[var(--color-regu-border)] bg-[var(--color-regu-bg)]/90 backdrop-blur-xl">
        {!isOnline && (
          <div className="flex items-center justify-center gap-2 bg-[var(--color-regu-warn)]/10 px-4 py-1.5 text-xs text-[var(--color-regu-warn)]">
            <WifiOff size={12} aria-hidden /> Connection lost. Reconnecting...
          </div>
        )}
        <div className="flex items-center justify-between px-6 py-3">
          <Link to="/" className="flex items-center gap-2" aria-label="REGU home">
            <ShieldCheck size={18} className="text-[var(--color-regu-accent)]" aria-hidden />
            <span className="text-[var(--color-regu-fg)] font-semibold text-base font-[family-name:var(--font-heading)] tracking-tight">Regu</span>
          </Link>
          <StepIndicator currentStep={2} />
          <button onClick={() => setExitDialogOpen(true)} className="text-xs text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg-muted)] transition-colors" type="button">
            Exit analysis
          </button>
        </div>
      </header>

      {/* ── Report progress bar ──────────────────────────────────────────── */}
      {generateReport.isPending && (
        <div className="fixed top-0 left-0 right-0 z-50 h-0.5 bg-[var(--color-regu-accent)]/20">
          <div className="h-full bg-[var(--color-regu-accent)] animate-[indeterminate_1.5s_ease-in-out_infinite]" />
        </div>
      )}

      {/* ── Messages ────────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto relative z-10" role="log" aria-live="polite">
        <div className="mx-auto max-w-[720px] px-4 py-8 space-y-5">
          {messages.map((msg) => (
            <MessageBubble
              key={msg.id}
              msg={msg}
              onGenerateReport={handleGenerateReport}
              isGenerating={generateReport.isPending}
              onRetryAnalysis={() => {
                if (msg.files) {
                  // can't retry without File objects after state cleared; prompt user
                  addMsg({ role: "ai", kind: "text", text: "Please re-upload your documents to retry the analysis." });
                }
              }}
            />
          ))}

          {/* Analyzing indicator */}
          {isAnalyzing && (
            <div className="flex items-center gap-3 px-1">
              <Loader2 size={14} className="text-[var(--color-regu-accent)] animate-spin shrink-0" aria-hidden />
              <span className="text-sm text-[var(--color-regu-fg-muted)]">
                Analyzing section {analyzingSection} of 9 - {SECTION_TITLES[analyzingSection] ?? "..."}
              </span>
            </div>
          )}

          <div ref={bottomRef} />
        </div>
      </div>

      {/* ── Input dock ──────────────────────────────────────────────────── */}
      <div className="sticky bottom-0 z-20 border-t border-[var(--color-regu-border)] bg-[var(--color-regu-surface)]/90 backdrop-blur-md">
        <div className="mx-auto max-w-[720px] px-4 py-3 space-y-2">
          {/* Attached files */}
          {pendingFiles.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {pendingFiles.map((f) => (
                <div key={f.name} className="flex items-center gap-1.5 rounded-lg border border-[var(--color-regu-border)] bg-[var(--color-regu-surface-2)] px-2.5 py-1.5 text-xs">
                  <FileText size={12} className="text-[var(--color-regu-fg-subtle)] shrink-0" aria-hidden />
                  <span className="text-[var(--color-regu-fg)] max-w-[140px] truncate">{f.name}</span>
                  <span className="text-[var(--color-regu-fg-subtle)] shrink-0">{formatSize(f.size)}</span>
                  <button
                    onClick={() => removeFile(f.name)}
                    className="ml-1 text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg)] transition-colors"
                    aria-label={`Remove ${f.name}`}
                    type="button"
                  >
                    <X size={11} aria-hidden />
                  </button>
                </div>
              ))}
            </div>
          )}

          {/* File error */}
          {fileError && (
            <p className="text-xs text-[var(--color-regu-danger)]" role="alert">{fileError}</p>
          )}

          {/* Input row */}
          <div className="flex items-end gap-2">
            {/* Attach */}
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={isAnalyzing}
              className="shrink-0 mb-1.5 p-2 rounded-lg text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg)] hover:bg-[var(--color-regu-surface-2)] transition-colors disabled:opacity-40"
              aria-label="Attach document"
              type="button"
            >
              <Paperclip size={18} aria-hidden />
            </button>

            {/* Text */}
            <textarea
              ref={textareaRef}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSend(); }
              }}
              placeholder={
                isAnalyzing
                  ? "Analyzing..."
                  : pendingFiles.length > 0
                    ? "Add a note (optional) then send to start analysis..."
                    : "Ask about required documents or attach files to analyze..."
              }
              disabled={isAnalyzing}
              rows={1}
              className={cn(
                "flex-1 resize-none rounded-xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] px-3.5 py-2.5 text-sm text-[var(--color-regu-fg)] placeholder:text-[var(--color-regu-fg-subtle)]",
                "focus:outline-none focus:border-[var(--color-regu-accent)]/50 transition-colors",
                "disabled:opacity-50 overflow-hidden",
              )}
            />

            {/* Send */}
            <button
              onClick={handleSend}
              disabled={!canSend}
              className={cn(
                "shrink-0 mb-1.5 p-2.5 rounded-xl transition-colors",
                canSend
                  ? "bg-[var(--color-regu-accent)] text-white hover:bg-[var(--color-regu-accent)]/90"
                  : "bg-[var(--color-regu-surface-2)] text-[var(--color-regu-fg-subtle)]",
              )}
              aria-label="Send"
              type="button"
            >
              <Send size={16} aria-hidden />
            </button>
          </div>

          <p className="text-[10px] text-[var(--color-regu-fg-subtle)] text-center pb-1">
            PDF, DOCX, TXT · max 10 MB each · Enter to send · Shift+Enter for new line
          </p>
        </div>
      </div>

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
        onChange={(e) => { if (e.target.files) attachFiles(e.target.files); e.target.value = ""; }}
        className="sr-only"
        tabIndex={-1}
        aria-hidden
      />

      {/* ── Exit dialog ──────────────────────────────────────────────────── */}
      {exitDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--color-regu-bg)]/80 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-2xl border border-[var(--color-regu-border-hi)] bg-[var(--color-regu-surface)] p-6 shadow-2xl">
            <h3 className="text-lg font-semibold text-[var(--color-regu-fg)] mb-2 font-[family-name:var(--font-heading)]">Exit analysis</h3>
            <p className="text-sm text-[var(--color-regu-fg-muted)] mb-6 leading-relaxed">Progress is saved. You can return using your session link.</p>
            <div className="flex gap-3 justify-end">
              <Button variant="ghost" size="sm" onClick={() => setExitDialogOpen(false)}>Cancel</Button>
              <Button variant="primary" size="sm" onClick={() => navigate("/")}>Exit</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── MessageBubble ────────────────────────────────────────────────────────────

function MessageBubble({
  msg,
  onGenerateReport,
  isGenerating,
  onRetryAnalysis,
}: {
  msg: ChatMessage;
  onGenerateReport: () => void;
  isGenerating: boolean;
  onRetryAnalysis: () => void;
}) {
  const isAi = msg.role === "ai";

  if (msg.kind === "text" || msg.kind === "error" || msg.kind === "done") {
    const isIntro = msg.text === "__INTRO__";

    return (
      <div className={cn("flex flex-col gap-1", isAi ? "items-start" : "items-end")}>
        {isAi && (
          <span className="text-[10px] uppercase tracking-[0.1em] text-[var(--color-regu-fg-subtle)] px-1">REGU</span>
        )}
        <div className={cn(
          "rounded-2xl px-4 py-3 text-sm leading-relaxed max-w-[88%]",
          isAi
            ? cn(
                "rounded-tl-sm border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] text-[var(--color-regu-fg)]",
                msg.kind === "error" && "border-[var(--color-regu-danger)]/30 text-[var(--color-regu-danger)]",
              )
            : "bg-[var(--color-regu-accent)]/15 text-[var(--color-regu-fg)] rounded-tr-sm",
        )}>
          {isIntro ? <IntroContent /> : <span className="whitespace-pre-line">{msg.text}</span>}

          {msg.kind === "done" && (
            <div className="mt-3 flex gap-2">
              <Button variant="primary" size="sm" onClick={onGenerateReport} disabled={isGenerating}>
                {isGenerating ? (
                  <span className="flex items-center gap-1.5"><Loader2 size={13} className="animate-spin" aria-hidden />Generating...</span>
                ) : "Generate Report"}
              </Button>
            </div>
          )}
        </div>
      </div>
    );
  }

  if (msg.kind === "files") {
    return (
      <div className="flex flex-col items-end gap-1">
        <span className="text-[10px] uppercase tracking-[0.1em] text-[var(--color-regu-fg-subtle)] px-1">You</span>
        <div className="rounded-2xl rounded-tr-sm bg-[var(--color-regu-accent)]/15 px-4 py-3 text-sm max-w-[88%]">
          {msg.text && <p className="text-[var(--color-regu-fg)] mb-2">{msg.text}</p>}
          <div className="space-y-1.5">
            {msg.files?.map((f) => (
              <div key={f.name} className="flex items-center gap-2 text-[var(--color-regu-fg-muted)]">
                <FileText size={13} className="shrink-0" aria-hidden />
                <span className="truncate text-xs">{f.name}</span>
                <span className="shrink-0 text-xs opacity-60">{formatSize(f.size)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (msg.kind === "analysis") {
    const results = msg.sectionResults ?? {};
    const errors = msg.sectionErrors ?? {};
    const doneCount = Object.keys(results).length;
    const totalGaps = Object.values(results).reduce((s, r) => s + r.gapCount, 0);

    return (
      <div className="flex flex-col gap-1 items-start">
        <span className="text-[10px] uppercase tracking-[0.1em] text-[var(--color-regu-fg-subtle)] px-1">REGU</span>
        <div className="w-full max-w-[88%] space-y-2">
          <p className="text-xs text-[var(--color-regu-fg-muted)] px-1">
            {doneCount} of 9 sections analyzed · {totalGaps} gap{totalGaps !== 1 ? "s" : ""} found
          </p>
          {Array.from({ length: 9 }, (_, i) => i + 1).map((section) => {
            const result = results[section];
            const error = errors[section];
            if (!result && !error) return null;
            return (
              <SectionCard key={section} section={section} result={result} error={error} />
            );
          })}
          {doneCount === 0 && (
            <button onClick={onRetryAnalysis} className="text-xs text-[var(--color-regu-accent)] underline underline-offset-2" type="button">
              Retry analysis
            </button>
          )}
        </div>
      </div>
    );
  }

  return null;
}

// ── IntroContent ─────────────────────────────────────────────────────────────

function IntroContent() {
  return (
    <div>
      <p className="text-[var(--color-regu-fg)] mb-4">
        Your system has been classified as{" "}
        <span className="font-semibold text-[var(--color-regu-accent)]">high-risk</span>{" "}
        under the EU AI Act. I'll analyze your documentation against all 9 Annex IV requirements.
      </p>
      <p className="text-[var(--color-regu-fg-muted)] text-xs mb-3">
        You'll typically need documentation covering these areas:
      </p>
      <ul className="space-y-2 mb-4">
        {REQUIRED_DOCUMENTS.map((doc) => (
          <li key={doc.number} className="flex gap-2.5 text-sm">
            <span className="shrink-0 w-5 h-5 rounded-full bg-[var(--color-regu-accent)]/15 text-[var(--color-regu-accent)] text-[10px] font-semibold flex items-center justify-center mt-0.5">
              {doc.number}
            </span>
            <span>
              <span className="text-[var(--color-regu-fg)] font-medium">{doc.title}</span>
              <span className="text-[var(--color-regu-fg-subtle)] ml-1 text-xs">- {doc.covers}</span>
            </span>
          </li>
        ))}
      </ul>
      <p className="text-xs text-[var(--color-regu-fg-subtle)]">
        Ask me anything about these requirements, or attach your documents using the paperclip button below and send them for analysis.
      </p>
    </div>
  );
}

// ── SectionCard ──────────────────────────────────────────────────────────────

function SectionCard({
  section,
  result,
  error,
}: {
  section: number;
  result: GapAnalysisResponse | undefined;
  error: string | undefined;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className={cn(
      "rounded-xl border bg-[var(--color-regu-surface)] overflow-hidden",
      result ? "border-[var(--color-regu-border)]" : "border-[var(--color-regu-danger)]/30",
    )}>
      <button
        onClick={() => result && setExpanded((v) => !v)}
        className={cn("w-full flex items-center gap-3 px-3.5 py-2.5 text-left", result && "cursor-pointer")}
        type="button"
        disabled={!result}
      >
        <div className="shrink-0">
          {result
            ? <CheckCircle2 size={15} className="text-[var(--color-regu-success,#22c55e)]" aria-hidden />
            : <AlertCircle size={15} className="text-[var(--color-regu-danger)]" aria-hidden />}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs font-medium text-[var(--color-regu-fg)] truncate">
            {section}. {SECTION_TITLES[section]}
          </p>
          {error && <p className="text-[10px] text-[var(--color-regu-danger)]">{error}</p>}
        </div>
        {result && (
          <div className="shrink-0 flex items-center gap-2">
            <span className="text-[10px] text-[var(--color-regu-fg-muted)]">{result.metRequirements}/{result.totalRequirements}</span>
            <StatusChip variant={result.compliancePercentage >= 75 ? "resolved" : result.compliancePercentage >= 40 ? "minor" : "critical"}>
              {result.compliancePercentage}%
            </StatusChip>
          </div>
        )}
      </button>

      {expanded && result && (
        <div className="border-t border-[var(--color-regu-border)]/50 px-3.5 pb-3 pt-2.5">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-[var(--color-regu-border)]/50">
                <th className="text-left pb-1.5 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider text-[10px]">Req</th>
                <th className="text-left pb-1.5 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider text-[10px]">Status</th>
                <th className="text-left pb-1.5 text-[var(--color-regu-fg-subtle)] font-medium uppercase tracking-wider text-[10px] hidden sm:table-cell">Detail</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((item) => (
                <tr key={item.requirementId} className="border-b border-[var(--color-regu-border)]/30 last:border-0">
                  <td className="py-1.5 pr-2 text-[var(--color-regu-fg)] font-mono text-[10px] whitespace-nowrap">{item.requirementId}</td>
                  <td className="py-1.5 pr-2">
                    <StatusChip variant={item.found ? "resolved" : severityToVariant(item.severity)}>
                      {item.found ? "Met" : item.severity || "Gap"}
                    </StatusChip>
                  </td>
                  <td className="py-1.5 text-[var(--color-regu-fg-muted)] hidden sm:table-cell text-[11px]">
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
