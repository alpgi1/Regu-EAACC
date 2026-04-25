/**
 * Axios client and TypeScript types for the REGU backend API.
 *
 * Base path: /api/v1 (proxied by Vite to localhost:8080).
 * Types mirror the real Java DTOs in com.regu.api.dto.*.
 */

import axios, { type AxiosError } from "axios";

// ── Axios instance ──────────────────────────────────────────────────────────

export const api = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "/api/v1",
  timeout: 120_000, // 120s — report generation can take 30–60s
  headers: { "Content-Type": "application/json" },
});

// ── Normalised error shape ──────────────────────────────────────────────────

export interface ApiError {
  code: number;
  message: string;
  retryable: boolean;
}

const RETRYABLE_CODES = new Set([408, 429, 500, 502, 503, 504]);

api.interceptors.response.use(
  (res) => res,
  (err: AxiosError<{ message?: string; error?: string }>) => {
    const status = err.response?.status ?? 0;
    const serverMsg =
      err.response?.data?.message ??
      err.response?.data?.error ??
      err.message ??
      "An unexpected error occurred";

    const normalised: ApiError = {
      code: status,
      message: serverMsg,
      retryable: RETRYABLE_CODES.has(status) || status === 0,
    };

    return Promise.reject(normalised);
  },
);

/**
 * Safely extract a human-readable message from any error shape.
 * Works with our ApiError, native Error, or unknown.
 */
export function getErrorMessage(err: unknown, fallback = "An unexpected error occurred"): string {
  if (err && typeof err === "object" && "message" in err && typeof (err as { message: unknown }).message === "string") {
    return (err as { message: string }).message;
  }
  if (err instanceof Error) return err.message;
  if (typeof err === "string") return err;
  return fallback;
}

// ── Risk categories ─────────────────────────────────────────────────────────

export type RiskCategory =
  | "unacceptable"
  | "high"
  | "limited"
  | "minimal"
  | "out_of_scope";

// ── Interview DTOs ──────────────────────────────────────────────────────────

export interface AnswerOptionDto {
  answerId: string;
  label: string;
}

export interface QuestionDto {
  questionKey: string;
  questionText: string;
  /** "single_choice" | "multiple_choice" | "yes_no" | "free_text" */
  questionType: string;
  category: string;
  options: AnswerOptionDto[] | null;
  /** Why this question matters — rendered as clarification text */
  explanation: string | null;
}

/**
 * POST /interviews → 201
 *
 * Backend field is `firstQuestion`, not `nextQuestion` (prompt spec drift).
 */
export interface StartInterviewResponse {
  sessionId: string;
  status: string;
  firstQuestion: QuestionDto;
}

/**
 * POST /interviews/:id/answer → 200
 *
 * status: "next_question" | "classified" | "stage2_required" | "report_ready"
 * Backend has a 4th status "classified" not in the prompt spec — we treat it
 * like "report_ready" (triggers report generation if reportId absent).
 */
export interface ApiNextStepResponse {
  status: "next_question" | "classified" | "stage2_required" | "report_ready";
  nextQuestion: QuestionDto | null;
  classification: ClassificationSummaryDto | null;
  reportId: string | null;
}

export interface ClassificationSummaryDto {
  riskCategory: RiskCategory;
  primaryLegalBasis: string | null;
  confidence: string | null;
  reasoning: string | null;
  applicableArticles: number[];
}

export interface SubmitAnswerRequest {
  questionKey: string;
  answerId?: string;
  freeText?: string;
}

// ── Stage 2 DTOs ────────────────────────────────────────────────────────────

export interface SectionStatusDto {
  sectionNumber: number;
  sectionTitle: string;
  /** "pending" | "submitted" | "analyzed" */
  status: string;
  compliancePercentage: number | null;
}

export interface Stage2StatusResponse {
  sessionId: string;
  /** "in_progress" | "completed" */
  status: string;
  sections: SectionStatusDto[];
}

export interface GapItemDto {
  requirementId: string;
  entityName: string;
  found: boolean;
  extractedValue: string | null;
  gapDescription: string | null;
  severity: string | null;
  recommendation: string | null;
}

export interface GapAnalysisResponse {
  sectionNumber: number;
  sectionTitle: string;
  totalRequirements: number;
  metRequirements: number;
  gapCount: number;
  compliancePercentage: number;
  items: GapItemDto[];
}

export interface SubmitDocumentRequest {
  documentText: string;
}

/**
 * POST /stage2/sections/:n/qa
 * Backend expects { sectionNumber, requirementId, answer }.
 * The sectionNumber is also in the URL path.
 */
export interface SubmitQAAnswerRequest {
  sectionNumber: number;
  requirementId: string;
  answer: string;
}

export interface Stage2ChatRequest {
  question: string;
}

export interface Stage2ChatResponse {
  answer: string;
}

export interface NextRequirementDto {
  requirementId: string;
  entityName: string;
  fallbackPrompt: string | null;
  isOptional: boolean;
  sectionNumber: number;
}

// ── Report DTOs ─────────────────────────────────────────────────────────────

export interface ReportSectionDto {
  title: string;
  content: string;
  citationRefs: string[];
}

export interface CitationDto {
  citationId: string;
  sourceTable: string | null;
  sourceChunkId: number;
  reference: string | null;
  snippet: string;
}

export interface ReportResponse {
  reportId: string;
  analysisId: string | null;
  summary: string;
  classification: ClassificationSummaryDto | null;
  sections: ReportSectionDto[];
  gapAnalyses: GapAnalysisResponse[];
  citations: CitationDto[];
  disclaimer: string;
  generatedAt: string;
  totalProcessingMs: number;
}

// ── Interview status (GET /interviews/:id/status) ───────────────────────────

export interface InterviewStatusResponse {
  sessionId: string;
  status: string;
  stage: number;
  answeredQuestions: number;
  riskClassification: string;
}
