/**
 * Stage1Page — form-style interview, one question at a time.
 *
 * Visual register: professional questionnaire, not chatbot.
 * Centered column, option cards (not radios), keyboard navigable,
 * collapsible free-text area, AnimatePresence transitions.
 *
 * Resumability: on mount, calls GET /interviews/:id/status and
 * reconciles — if the server is further along, redirect forward.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { ChevronDown, Copy, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { StepIndicator } from "@/components/ui/step-indicator";
import { StatusChip } from "@/components/ui/status-chip";
import { cn, EASE_SMOOTH } from "@/lib/utils";
import {
  useInterviewStatus,
  useSubmitAnswer,
  useGenerateReport,
} from "@/lib/queries";
import { TIER_SUMMARIES } from "@/lib/constants";
import type { QuestionDto, ApiNextStepResponse, ClassificationSummaryDto } from "@/lib/api";
import { getErrorMessage } from "@/lib/api";

// ── Helpers ─────────────────────────────────────────────────────────────────

function riskBadgeVariant(risk: string | undefined) {
  switch (risk) {
    case "unacceptable": return "critical" as const;
    case "high": return "major" as const;
    case "limited":
    case "minimal": return "minor" as const;
    default: return "neutral" as const;
  }
}

// ── Component ───────────────────────────────────────────────────────────────

export default function Stage1Page() {
  const { id: sessionId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const prefersReduced = useReducedMotion();

  // ── Session resumability ──────────────────────────────────────────────
  const { data: status } = useInterviewStatus(sessionId);

  useEffect(() => {
    if (!status || !sessionId) return;
    // If the backend says we're past Stage 1, redirect forward
    if (status.stage === 2) {
      navigate(`/app/session/${sessionId}/stage2`, { replace: true });
      return;
    }
    if (status.riskClassification && status.riskClassification !== "pending") {
      // Classification is done — go to report
      navigate(`/app/session/${sessionId}/report`, { replace: true });
    }
  }, [status, sessionId, navigate]);

  // ── Current question state ────────────────────────────────────────────
  // The first question comes from the StartInterviewResponse stored before navigation.
  // Subsequent questions arrive from the submit-answer response.
  // We keep the current question in local state so AnimatePresence works.

  const [question, setQuestion] = useState<QuestionDto | null>(null);
  const [questionIndex, setQuestionIndex] = useState(0);
  const [selectedOptions, setSelectedOptions] = useState<string[]>([]);
  const [freeText, setFreeText] = useState("");
  const [freeTextOpen, setFreeTextOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);

  // Terminal states
  const [terminalState, setTerminalState] = useState<
    | { type: "stage2"; classification: ClassificationSummaryDto }
    | { type: "report"; classification: ClassificationSummaryDto | null; reportId: string | null }
    | null
  >(null);

  const submitAnswer = useSubmitAnswer(sessionId ?? "");
  const generateReport = useGenerateReport(sessionId ?? "");

  // Load first question from sessionStorage (set by EntryModal before navigation)
  useEffect(() => {
    const stored = sessionStorage.getItem("regu-first-question");
    if (stored) {
      try {
        const q = JSON.parse(stored) as QuestionDto;
        setQuestion(q);
        setQuestionIndex(status?.answeredQuestions ?? 0);
      } catch { /* noop */ }
      sessionStorage.removeItem("regu-first-question");
    }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  // Re-derive questionIndex from status when available
  useEffect(() => {
    if (status && !terminalState) {
      setQuestionIndex(status.answeredQuestions);
    }
  }, [status, terminalState]);

  // ── Keyboard navigation for options ───────────────────────────────────
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const handleOptionKeyDown = useCallback(
    (e: React.KeyboardEvent, index: number, options: { answerId: string }[]) => {
      let next = -1;
      if (e.key === "ArrowDown" || e.key === "ArrowRight") {
        e.preventDefault();
        next = (index + 1) % options.length;
      } else if (e.key === "ArrowUp" || e.key === "ArrowLeft") {
        e.preventDefault();
        next = (index - 1 + options.length) % options.length;
      } else if (e.key === "Enter") {
        e.preventDefault();
        handleSubmit();
        return;
      }
      if (next >= 0) {
        optionRefs.current[next]?.focus();
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [freeText],
  );

  // ── Submit handler ────────────────────────────────────────────────────
  async function handleSubmit() {
    if (!question || !sessionId) return;

    const hasFreeText = freeText.trim().length >= 10;
    let body: { questionKey: string; answerId?: string; freeText?: string };
    if (hasFreeText) {
      body = { questionKey: question.questionKey, freeText: freeText.trim() };
    } else {
      // Multi-select: join all selected options with comma
      body = { questionKey: question.questionKey, answerId: selectedOptions.join(",") };
    }

    setError(null);

    try {
      const result: ApiNextStepResponse = await submitAnswer.mutateAsync(body);

      if (result.status === "next_question" && result.nextQuestion) {
        setQuestion(result.nextQuestion);
        setQuestionIndex((i) => i + 1);
        setSelectedOptions([]);
        setFreeText("");
        setFreeTextOpen(false);
        optionRefs.current = [];
      } else if (result.status === "stage2_required") {
        setTerminalState({ type: "stage2", classification: result.classification! });
      } else if (result.status === "report_ready" || result.status === "classified") {
        setTerminalState({
          type: "report",
          classification: result.classification,
          reportId: result.reportId,
        });
      }
    } catch (err) {
      setError(getErrorMessage(err, "Failed to submit answer."));
    }
  }

  // ── Report generation for non-high-risk terminal ──────────────────────
  async function handleGenerateReport() {
    if (!sessionId) return;
    setError(null);
    try {
      await generateReport.mutateAsync();
      navigate(`/app/session/${sessionId}/report`);
    } catch (err) {
      setError(getErrorMessage(err, "Failed to generate report."));
    }
  }

  // ── Copy session link ─────────────────────────────────────────────────
  function copySessionLink() {
    navigator.clipboard.writeText(window.location.href);
    setCopiedLink(true);
    setTimeout(() => setCopiedLink(false), 2000);
  }

  // ── Guard: nothing to show yet ────────────────────────────────────────
  if (!sessionId) {
    navigate("/app", { replace: true });
    return null;
  }

  const isMultiSelect = question?.questionType === "multi_select";

  const isSubmitDisabled =
    submitAnswer.isPending ||
    (selectedOptions.length === 0 && freeText.trim().length < 10);

  const hasFreeTextContent = freeText.trim().length > 0;

  // ── Animation variants ────────────────────────────────────────────────
  const cardVariants = {
    enter: prefersReduced ? {} : { opacity: 0, y: 8 },
    center: { opacity: 1, y: 0, transition: { duration: 0.24, ease: EASE_SMOOTH } },
    exit: prefersReduced ? {} : { opacity: 0, y: -8, transition: { duration: 0.2, ease: EASE_SMOOTH } },
  };

  return (
    <div className="min-h-screen bg-[var(--color-regu-bg)]">
      <main
        id="main-content"
        className="mx-auto max-w-[720px] px-6 py-12"
      >
        {/* Step indicator */}
        <div className="mb-8 flex flex-col items-center gap-2">
          <StepIndicator currentStep={1} />
          {!terminalState && question && (
            <p className="text-xs text-[var(--color-regu-fg-subtle)]">
              Question {questionIndex + 1}
            </p>
          )}
        </div>

        {/* Question / Terminal card with AnimatePresence */}
        <AnimatePresence mode="wait">
          {/* ── Terminal: Stage 2 transition ──────────────────────────── */}
          {terminalState?.type === "stage2" && (
            <motion.div
              key="stage2-transition"
              variants={cardVariants}
              initial="enter"
              animate="center"
              exit="exit"
              className="rounded-2xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] p-10"
            >
              <h2 className="text-[clamp(1.5rem,3vw,2.25rem)] font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                Your system appears to be high-risk.
              </h2>
              <p className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed mb-3">
                High-risk AI systems under the EU AI Act are subject to
                extensive requirements, including technical documentation
                mandated by Annex IV.
              </p>
              <p className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed mb-8">
                The next stage will walk through your documentation
                section by section, analyzing coverage against Annex IV
                requirements and identifying gaps.
              </p>
              <Button
                variant="primary"
                size="lg"
                onClick={() => navigate(`/app/session/${sessionId}/stage2`)}
              >
                Continue to documentation
              </Button>
            </motion.div>
          )}

          {/* ── Terminal: Report ready ────────────────────────────────── */}
          {terminalState?.type === "report" && (
            <motion.div
              key="report-ready"
              variants={cardVariants}
              initial="enter"
              animate="center"
              exit="exit"
              className="rounded-2xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] p-10"
            >
              <h2 className="text-[clamp(1.5rem,3vw,2.25rem)] font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-4">
                Classification complete.
              </h2>
              {terminalState.classification && (
                <div className="mb-4 flex items-center gap-3">
                  <span className="text-lg font-semibold text-[var(--color-regu-fg)] capitalize tabular-nums">
                    {terminalState.classification.riskCategory.replace("_", " ")}
                  </span>
                  <StatusChip variant={riskBadgeVariant(terminalState.classification.riskCategory)}>
                    {terminalState.classification.riskCategory.replace("_", " ")}
                  </StatusChip>
                </div>
              )}
              <p className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed mb-8">
                {TIER_SUMMARIES[terminalState.classification?.riskCategory ?? ""] ??
                  "Classification determined."}
              </p>
              {error && (
                <p className="text-sm text-[var(--color-regu-danger)] mb-4">{error}</p>
              )}
              <Button
                variant="primary"
                size="lg"
                onClick={handleGenerateReport}
                disabled={generateReport.isPending}
              >
                {generateReport.isPending ? "Generating report..." : "View your report"}
              </Button>
            </motion.div>
          )}

          {/* ── Active question ───────────────────────────────────────── */}
          {!terminalState && question && (
            <motion.div
              key={question.questionKey}
              variants={cardVariants}
              initial="enter"
              animate="center"
              exit="exit"
              className="rounded-2xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] p-10"
            >
              {/* Question text */}
              <h2
                id={`q-${question.questionKey}`}
                className="text-[clamp(1.5rem,3vw,2.25rem)] font-semibold text-[var(--color-regu-fg)] font-[family-name:var(--font-heading)] tracking-tight mb-2"
              >
                {question.questionText}
              </h2>
              {question.explanation && (
                <p className="text-sm text-[var(--color-regu-fg-muted)] leading-relaxed mb-6 max-w-prose">
                  {question.explanation}
                </p>
              )}

              {/* Options */}
              {question.options && question.options.length > 0 && (
                <div
                  role={isMultiSelect ? "group" : "radiogroup"}
                  aria-labelledby={`q-${question.questionKey}`}
                  className="flex flex-col gap-3 mb-6"
                >
                  {isMultiSelect && (
                    <p className="text-xs text-regu-fg-subtle mb-1">
                      Select all that apply
                    </p>
                  )}
                  {question.options.map((opt, i) => {
                    const isSelected = selectedOptions.includes(opt.answerId);
                    const isNoneOption = opt.answerId === "none_of_the_above";
                    return (
                      <button
                        key={opt.answerId}
                        ref={(el) => { optionRefs.current[i] = el; }}
                        type="button"
                        role={isMultiSelect ? "checkbox" : "radio"}
                        aria-checked={isSelected}
                        onClick={() => {
                          setFreeText("");
                          if (!isMultiSelect) {
                            // Single-select: replace
                            setSelectedOptions([opt.answerId]);
                          } else if (isNoneOption) {
                            // "None" clears everything and selects only itself
                            setSelectedOptions(isSelected ? [] : ["none_of_the_above"]);
                          } else {
                            // Multi-select: toggle; clear "none_of_the_above" if present
                            setSelectedOptions((prev) => {
                              const withoutNone = prev.filter(v => v !== "none_of_the_above");
                              return withoutNone.includes(opt.answerId)
                                ? withoutNone.filter(v => v !== opt.answerId)
                                : [...withoutNone, opt.answerId];
                            });
                          }
                        }}
                        onKeyDown={(e) => handleOptionKeyDown(e, i, question.options!)}
                        tabIndex={isSelected || (i === 0 && selectedOptions.length === 0) ? 0 : -1}
                        className={cn(
                          "w-full text-left rounded-xl border p-5 transition-all duration-150",
                          "bg-[var(--color-regu-surface-2)]",
                          isSelected
                            ? "border-[var(--color-regu-accent)] shadow-[inset_4px_0_0_var(--color-regu-accent)]"
                            : "border-[var(--color-regu-border)] hover:border-[var(--color-regu-accent)]/40 hover:bg-[var(--color-regu-surface-2)]/80",
                          hasFreeTextContent && !isSelected && "opacity-50",
                        )}
                      >
                        <div className="flex items-center gap-3">
                          {isMultiSelect && (
                            <span className={cn(
                              "shrink-0 w-4 h-4 rounded border transition-colors",
                              isSelected
                                ? "bg-regu-accent border-regu-accent"
                                : "border-regu-fg-subtle"
                            )} aria-hidden />
                          )}
                          <span className="text-sm font-medium text-regu-fg">
                            {opt.label}
                          </span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}

              {/* Free-text collapse */}
              {question.questionType !== "free_text" && question.options && question.options.length > 0 && (
                <details
                  open={freeTextOpen}
                  onToggle={(e) => setFreeTextOpen((e.target as HTMLDetailsElement).open)}
                  className="mb-6"
                >
                  <summary className="flex items-center gap-2 cursor-pointer text-sm text-[var(--color-regu-fg-muted)] hover:text-[var(--color-regu-fg)] transition-colors select-none list-none [&::-webkit-details-marker]:hidden">
                    <ChevronDown
                      size={14}
                      className={cn("transition-transform duration-200", freeTextOpen && "rotate-180")}
                      aria-hidden
                    />
                    Or describe in your own words
                  </summary>
                  <div className="mt-3">
                    <Textarea
                      value={freeText}
                      onChange={(e) => {
                        setFreeText(e.target.value);
                        if (e.target.value.trim().length > 0) setSelectedOption(null);
                      }}
                      placeholder="Describe your AI system's use case and context..."
                      className="min-h-[100px]"
                    />
                    <p className="mt-1.5 text-[10px] text-[var(--color-regu-fg-subtle)]">
                      The system will map your answer to the closest option.
                    </p>
                  </div>
                </details>
              )}

              {/* Pure free-text questions */}
              {(question.questionType === "free_text" || !question.options || question.options.length === 0) && (
                <div className="mb-6">
                  <Textarea
                    value={freeText}
                    onChange={(e) => setFreeText(e.target.value)}
                    placeholder="Describe your answer..."
                    className="min-h-[120px]"
                  />
                </div>
              )}

              {/* Error */}
              {error && (
                <p className="text-sm text-[var(--color-regu-danger)] mb-4">{error}</p>
              )}
            </motion.div>
          )}
        </AnimatePresence>

        {/* Footer row — outside AnimatePresence so it doesn't animate */}
        {!terminalState && question && (
          <div className="mt-6 flex items-center justify-between">
            <button
              type="button"
              onClick={copySessionLink}
              className="flex items-center gap-1.5 text-xs text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg-muted)] transition-colors"
            >
              {copiedLink ? (
                <>
                  <Check size={12} aria-hidden />
                  Link copied
                </>
              ) : (
                <>
                  <Copy size={12} aria-hidden />
                  Save and continue later
                </>
              )}
            </button>
            <Button
              variant="primary"
              size="md"
              onClick={handleSubmit}
              disabled={isSubmitDisabled}
            >
              {submitAnswer.isPending ? "Classifying..." : "Submit answer"}
            </Button>
          </div>
        )}
      </main>
    </div>
  );
}
