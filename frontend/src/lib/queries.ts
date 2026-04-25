/**
 * React Query hooks for every REGU backend endpoint.
 *
 * Conventions:
 * - Session-aware query keys prevent cross-session cache bleed.
 * - Mutations: retry 0 (user-triggered retries handle repeats).
 * - Queries: retry 1, staleTime 30s.
 */

import { useMutation, useQuery, QueryClient } from "@tanstack/react-query";
import {
  api,
  type StartInterviewResponse,
  type ApiNextStepResponse,
  type SubmitAnswerRequest,
  type ClassificationSummaryDto,
  type InterviewStatusResponse,
  type Stage2StatusResponse,
  type GapAnalysisResponse,
  type SubmitDocumentRequest,
  type SubmitQAAnswerRequest,
  type ReportResponse,
  type CitationDto,
} from "./api";

// ── QueryClient ─────────────────────────────────────────────────────────────

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});

// ── Interview mutations ─────────────────────────────────────────────────────

/** POST /interviews — start a new session */
export function useStartInterview() {
  return useMutation<StartInterviewResponse, Error>({
    mutationFn: async () => {
      const { data } = await api.post<StartInterviewResponse>(
        "/interviews",
        {},
      );
      return data;
    },
  });
}

/** POST /interviews/:id/answer */
export function useSubmitAnswer(sessionId: string) {
  return useMutation<ApiNextStepResponse, Error, SubmitAnswerRequest>({
    mutationFn: async (body) => {
      const { data } = await api.post<ApiNextStepResponse>(
        `/interviews/${sessionId}/answer`,
        body,
      );
      return data;
    },
  });
}

/** POST /interviews/:id/skip-to-stage2 */
export function useSkipToStage2() {
  return useMutation<
    ClassificationSummaryDto,
    Error,
    { sessionId: string }
  >({
    mutationFn: async ({ sessionId }) => {
      const { data } = await api.post<ClassificationSummaryDto>(
        `/interviews/${sessionId}/skip-to-stage2`,
        {},
      );
      return data;
    },
  });
}

// ── Interview queries ───────────────────────────────────────────────────────

/** GET /interviews/:id/status */
export function useInterviewStatus(sessionId: string | undefined) {
  return useQuery<InterviewStatusResponse>({
    queryKey: ["interview", sessionId, "status"],
    queryFn: async () => {
      const { data } = await api.get<InterviewStatusResponse>(
        `/interviews/${sessionId}/status`,
      );
      return data;
    },
    enabled: !!sessionId,
  });
}

// ── Stage 2 mutations ───────────────────────────────────────────────────────

/** POST /interviews/:id/stage2/start */
export function useStartStage2(sessionId: string) {
  return useMutation<Stage2StatusResponse, Error>({
    mutationFn: async () => {
      const { data } = await api.post<Stage2StatusResponse>(
        `/interviews/${sessionId}/stage2/start`,
        {},
      );
      return data;
    },
  });
}

/** POST /interviews/:id/stage2/sections/:n/upload (multipart) */
export function useUploadDocument(sessionId: string) {
  return useMutation<
    GapAnalysisResponse,
    Error,
    { sectionNumber: number; file: File }
  >({
    mutationFn: async ({ sectionNumber, file }) => {
      const form = new FormData();
      // Backend expects the field name "document"
      form.append("document", file);
      const { data } = await api.post<GapAnalysisResponse>(
        `/interviews/${sessionId}/stage2/sections/${sectionNumber}/upload`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } },
      );
      return data;
    },
  });
}

/** POST /interviews/:id/stage2/sections/:n/document (pasted text) */
export function useSubmitDocument(sessionId: string) {
  return useMutation<
    GapAnalysisResponse,
    Error,
    { sectionNumber: number; documentText: string }
  >({
    mutationFn: async ({ sectionNumber, documentText }) => {
      const body: SubmitDocumentRequest = { documentText };
      const { data } = await api.post<GapAnalysisResponse>(
        `/interviews/${sessionId}/stage2/sections/${sectionNumber}/document`,
        body,
      );
      return data;
    },
  });
}

/** POST /interviews/:id/stage2/sections/:n/qa */
export function useSubmitQA(sessionId: string) {
  return useMutation<
    GapAnalysisResponse | null,
    Error,
    SubmitQAAnswerRequest
  >({
    mutationFn: async (body) => {
      const res = await api.post(
        `/interviews/${sessionId}/stage2/sections/${body.sectionNumber}/qa`,
        body,
      );
      // Backend returns 204 if more requirements remain (no body)
      if (res.status === 204) return null;
      return res.data as GapAnalysisResponse;
    },
  });
}

// ── Stage 2 queries ─────────────────────────────────────────────────────────

/** GET /interviews/:id/stage2/status */
export function useStage2Status(sessionId: string | undefined) {
  return useQuery<Stage2StatusResponse>({
    queryKey: ["interview", sessionId, "stage2", "status"],
    queryFn: async () => {
      const { data } = await api.get<Stage2StatusResponse>(
        `/interviews/${sessionId}/stage2/status`,
      );
      return data;
    },
    enabled: !!sessionId,
  });
}

/** GET /interviews/:id/stage2/sections/:n/gaps */
export function useSectionGaps(
  sessionId: string | undefined,
  sectionNumber: number | undefined,
) {
  return useQuery<GapAnalysisResponse>({
    queryKey: ["interview", sessionId, "stage2", "section", sectionNumber, "gaps"],
    queryFn: async () => {
      const { data } = await api.get<GapAnalysisResponse>(
        `/interviews/${sessionId}/stage2/sections/${sectionNumber}/gaps`,
      );
      return data;
    },
    enabled: !!sessionId && sectionNumber != null,
  });
}

// ── Report mutations & queries ──────────────────────────────────────────────

/** POST /interviews/:id/report — may take 30-60s */
export function useGenerateReport(sessionId: string) {
  return useMutation<ReportResponse, Error>({
    mutationFn: async () => {
      const { data } = await api.post<ReportResponse>(
        `/interviews/${sessionId}/report`,
        {},
      );
      return data;
    },
  });
}

/** GET /reports/:reportId */
export function useReport(reportId: string | undefined) {
  return useQuery<ReportResponse>({
    queryKey: ["report", reportId],
    queryFn: async () => {
      const { data } = await api.get<ReportResponse>(
        `/reports/${reportId}`,
      );
      return data;
    },
    enabled: !!reportId,
  });
}

/** GET /reports/:reportId/citations */
export function useCitations(reportId: string | undefined) {
  return useQuery<CitationDto[]>({
    queryKey: ["report", reportId, "citations"],
    queryFn: async () => {
      const { data } = await api.get<CitationDto[]>(
        `/reports/${reportId}/citations`,
      );
      return data;
    },
    enabled: !!reportId,
  });
}
