import { BrowserRouter, Routes, Route } from "react-router-dom";
import Landing from "@/pages/Landing";

/**
 * App router.
 *
 * /      → Landing page (public marketing)
 * /app   → Analysis app placeholder (not yet built)
 * /privacy, /terms, /cookies, /dpa → Legal pages (not yet built)
 */
export default function App() {
  return (
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

        {/* Analysis app — not yet built */}
        <Route
          path="/app"
          element={
            <div className="min-h-screen bg-[#060814] flex items-center justify-center">
              <div className="text-center max-w-md px-6">
                <p className="text-xs uppercase tracking-[0.14em] text-[rgba(235,235,235,0.38)] mb-4">
                  Coming soon
                </p>
                <h1 className="text-2xl font-semibold text-[#EBEBEB] mb-3 font-[family-name:var(--font-heading)]">
                  Analysis app — not yet built
                </h1>
                <p className="text-[rgba(235,235,235,0.62)] text-sm leading-relaxed">
                  The compliance analysis tool is under active development.
                  Check back soon or{" "}
                  <a href="/#contact" className="text-[#4A6FE5] hover:underline">
                    get in touch
                  </a>{" "}
                  to be notified when it launches.
                </p>
              </div>
            </div>
          }
        />

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
  );
}
