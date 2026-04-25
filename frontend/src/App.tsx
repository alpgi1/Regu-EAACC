import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "@/lib/queries";
import Landing from "@/pages/Landing";
import EntryPage from "@/pages/EntryPage";
import Stage1Page from "@/pages/Stage1Page";
import Stage2Page from "@/pages/Stage2Page";
import ReportPage from "@/pages/ReportPage";

/**
 * App router.
 *
 * /                              → Landing page (public marketing)
 * /app                           → Entry modal (risk-level gate)
 * /app/session/:id/stage1        → Stage 1 interview
 * /app/session/:id/stage2        → Stage 2 document analysis
 * /app/session/:id/stage2/:n     → Stage 2 at specific section
 * /app/session/:id/report        → Final report view
 * /privacy, /terms, etc.         → Legal pages (not yet built)
 */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* Skip-to-main link for keyboard users */}
        <a
          href="#main-content"
          className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[100] focus:px-4 focus:py-2 focus:rounded-lg focus:bg-[#2A52BE] focus:text-white focus:text-sm focus:font-medium"
        >
          Skip to main content
        </a>

        <Routes>
          <Route path="/" element={<Landing />} />

          {/* Analysis app */}
          <Route path="/app" element={<EntryPage />} />
          <Route path="/app/session/:id/stage1" element={<Stage1Page />} />
          <Route path="/app/session/:id/stage2" element={<Stage2Page />} />
          <Route path="/app/session/:id/stage2/:n" element={<Stage2Page />} />
          <Route path="/app/session/:id/report" element={<ReportPage />} />

          {/* Legal pages — not yet built */}
          {["/privacy", "/terms", "/cookies", "/dpa"].map((path) => (
            <Route
              key={path}
              path={path}
              element={
                <div className="min-h-screen bg-[#060814] flex items-center justify-center">
                  <p className="text-[rgba(235,235,235,0.62)]">
                    Page not yet available. <a href="/" className="text-[#4A6FE5] hover:underline">Return home</a>
                  </p>
                </div>
              }
            />
          ))}
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
