# LANDING_REBUILD_NOTES

Phase 8 — landing page rebuild against the Munich-pitch brief.

## Files added

- `src/components/landing/Nav.tsx`
- `src/components/landing/Hero.tsx`
- `src/components/landing/UrgencyBar.tsx`
- `src/components/landing/Stakes.tsx`
- `src/components/landing/Methodology.tsx`
- `src/components/landing/HowItWorks.tsx`
- `src/components/landing/RagDiagram.tsx` — inline SVG, animated stroke-dasharray + box fade
- `src/components/landing/UseCases.tsx`
- `src/components/landing/Comparison.tsx`
- `src/components/landing/Security.tsx`
- `src/components/landing/FAQ.tsx`
- `src/components/landing/FinalCTA.tsx`
- `src/components/landing/Contact.tsx`
- `src/components/landing/Footer.tsx`
- `src/lib/lenis.ts` — Lenis init/destroy + `scrollToSection`
- `src/pages/ComingSoon.tsx` — placeholder for footer/CTA links not yet built
- `src/pages/Landing.legacy.tsx` — preserved copy of the prior `Landing.tsx`

## Files modified

- `src/index.css` — added landing tokens (`--bg-base`, `--ink-*`, `--brand-*`, `--warn`/`--danger`/`--ok`, type scale, motion tokens) under `@layer base`. Added `.font-serif` utility class. Added accordion keyframes. Tightened reduced-motion override to 0.001ms.
- `index.html` — Google Fonts link expanded to include `Instrument+Serif:ital@0;1`.
- `src/pages/Landing.tsx` — completely replaced. Now composes new landing sections + initializes/destroys Lenis.
- `src/App.tsx` — added `/coming-soon` route mapped to `ComingSoon`.

## Dependencies installed

- `lenis@^1.x` (smooth scroll)
- `@paper-design/shaders-react@^0.0.76` (Hero `MeshGradient`)
- `@radix-ui/react-accordion@^1.x` (FAQ)

No other deps added. Existing infra (Tailwind v4, Framer Motion, Radix Slot, lucide-react, react-router-dom, axios, react-query) left untouched.

## Decisions where the brief was ambiguous

1. **MeshGradient API differences.** The brief specified `wireframe="true"` and `backgroundColor` props on `MeshGradient`. The installed `@paper-design/shaders-react@0.0.76` does not expose those props. I substituted equivalent visual intent: two layered `MeshGradient` instances with different `distortion`/`swirl`/`speed` values, with an opaque `#050814` background `<div>` placed underneath, plus a vignette + bottom fade overlay. Color palette and slow-speed calmness preserved.
2. **`ComingSoon` page.** The brief said footer-placeholder pages should link to `/coming-soon` rendering "This page is coming soon. For now, email founders@regu.eu." I implemented this as a real route using the same design tokens (italic-serif accent, ghost CTA back to `/`).
3. **Skip-to-main-content link.** The existing `App.tsx` had a skip link using legacy hex colors. I left it as-is (it's not on the landing page itself, it's a global a11y affordance) — touching it was out of scope.
4. **Legacy Landing kept as `Landing.legacy.tsx`.** It still imports the old `components/sections/*` files, all of which remain on disk and compile cleanly. Nothing imports `Landing.legacy.tsx`. It can be deleted alongside the unused `components/sections/` directory in a future cleanup pass — kept intentionally per brief instruction.
5. **UseCases entrance animation.** Originally implemented with a CSS keyframe firing on mount; switched to Framer Motion `useInView`-style on-scroll reveal (matching `Stakes`) so the animation grammar stays consistent with the brief's "fade+lift on scroll-into-view, once per session" rule.
6. **UrgencyBar dismissal.** Per the brief's "no `localStorage`" rule, dismissal is local React state only — refreshing the page brings the bar back. The brief explicitly allowed this.
7. **Sign-in route.** No auth implemented; the "Sign in" CTA in the nav routes to `/coming-soon` (consistent with the footer pattern), per brief §11 ("Auth out of scope").

## Verified

- `npm run typecheck` — passes.
- `npm run build` — passes (`dist/` written; CSS 59.71 kB / JS 627.18 kB pre-gzip).
- Dev server boot — `vite ready in 384 ms`. `GET /` and `GET /coming-soon` both return `200`.
- All Tailwind v4 lint suggestions about `bg-[var(...)]` → `bg-(...)` are stylistic preferences (canonical-class warnings), not errors. The `[var(--token)]` form is left in place for explicit readability and to match the brief's authoring style.

## Known issues / TODOs

- **Bundle size warning** — main JS chunk is 627 kB pre-gzip (194 kB gzipped). Vite warns about >500 kB. Acceptable for the Munich pitch; can be addressed later via:
  - dynamic-import code-splitting between `/` and `/app`
  - splitting `@paper-design/shaders-react` into a lazy chunk used only by Hero
  - splitting Framer Motion (`m` import + `LazyMotion`) — not currently used
- **Demo video** — both "Watch demo" CTAs route to `/coming-soon`. Wire to a real Drawer or a hosted video when one exists.
- **Contact form backend** — `onSubmit` shows the success state without making a network request. Wire to Formspree or a backend endpoint before launch.
- **Lighthouse** — not run. `prefers-reduced-motion` paths are wired (Hero static fallback, no Lenis init, count-ups skip animation, RAG diagram lines/boxes render with initial state = final state, `UrgencyBar` opacity-only transition); manual reduced-motion check still recommended on the day.
- **Legacy components** — `src/components/sections/*` and `src/pages/Landing.legacy.tsx` remain on disk, unused on the live route. Schedule a cleanup PR after the pitch is done.
- **Two unused legacy `lucide-react` icons** in `Hero.tsx` of the legacy file — irrelevant since legacy file is not imported.
