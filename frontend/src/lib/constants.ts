/**
 * Static constants for the analysis flow.
 *
 * All text here is hard-coded from the EU AI Act (Articles 5, 6, 51, Annex III).
 * No LLM-generated content.
 */

// ── Risk tier definitions (for the entry modal accordion) ───────────────────

export interface RiskTierDef {
  tier: string;
  definition: string;
  example: string;
}

export const RISK_TIERS: RiskTierDef[] = [
  {
    tier: "Unacceptable",
    definition:
      "AI practices that pose a clear threat to safety, livelihoods, or rights and are prohibited outright.",
    example:
      "Social scoring by public authorities, real-time remote biometric identification in public spaces (with narrow exceptions).",
  },
  {
    tier: "High",
    definition:
      "AI systems listed in Annex III or used as safety components of products covered by EU harmonisation legislation. Subject to strict requirements before market placement.",
    example:
      "AI used in recruitment, credit scoring, critical infrastructure, law enforcement, or medical devices.",
  },
  {
    tier: "Limited",
    definition:
      "AI systems with specific transparency obligations. Users must be informed they are interacting with AI.",
    example:
      "Chatbots, emotion recognition systems, deep-fake generators.",
  },
  {
    tier: "Minimal",
    definition:
      "AI systems with no additional obligations beyond existing law. Voluntary codes of conduct are encouraged.",
    example:
      "AI-powered spam filters, inventory management, video game AI.",
  },
  {
    tier: "Out of scope",
    definition:
      "Systems that do not fall under the regulation's definition of an AI system, or are explicitly excluded.",
    example:
      "Military AI, AI developed solely for research purposes before market placement.",
  },
];

// ── Classification tier summaries (for the report-ready card in Stage 1) ────

export const TIER_SUMMARIES: Record<string, string> = {
  unacceptable:
    "This system falls under a prohibited AI practice. Deployment within the EU is not permitted under the AI Act.",
  high:
    "This system is classified as high-risk. Providers must meet Annex IV documentation, conformity assessment, and post-market monitoring requirements before placing it on the market.",
  limited:
    "This system carries transparency obligations. Users must be informed they are interacting with an AI system.",
  minimal:
    "This system is classified as minimal-risk. No mandatory requirements under the AI Act, though voluntary codes of conduct are encouraged.",
  out_of_scope:
    "This system does not appear to fall within the scope of the EU AI Act.",
};

// ── Stage 2 section quick-action chips ──────────────────────────────────────

export const SECTION_QUICK_ACTIONS: Record<number, string[]> = {
  1: [
    "General system description",
    "Intended purpose statement",
    "Hardware and software specs",
    "Version and release history",
  ],
  2: [
    "Design specifications",
    "System architecture overview",
    "Development methodology",
    "Key design choices rationale",
  ],
  3: [
    "Training data description",
    "Data collection methodology",
    "Data preparation and labelling",
    "Statistical properties summary",
  ],
  4: [
    "Validation and testing strategy",
    "Metrics and acceptance criteria",
    "Test datasets used",
    "Known limitations and biases",
  ],
  5: [
    "Risk management process",
    "Identified risks catalogue",
    "Mitigation measures applied",
    "Residual risk assessment",
  ],
  6: [
    "Monitoring capabilities",
    "Human oversight measures",
    "Override and intervention ability",
    "Escalation procedures",
  ],
  7: [
    "Cybersecurity measures",
    "Robustness testing results",
    "Accuracy and performance metrics",
    "Fallback and redundancy plans",
  ],
  8: [
    "Quality management system",
    "Post-market monitoring plan",
    "Incident reporting procedures",
    "Regulatory compliance tracking",
  ],
  9: [
    "EU declaration of conformity",
    "CE marking documentation",
    "Contact and responsible person",
    "Conformity assessment procedure",
  ],
};
