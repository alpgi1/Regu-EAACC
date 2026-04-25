package com.regu.orchestration.stage1;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.domain.InterviewAnswer;
import com.regu.orchestration.dto.ClassificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Deterministic Stage 1 classification — no LLM involved.
 *
 * <p>Accumulates option-level flags from all interview answers by matching
 * each answer text back to its question's answer options JSON, then maps
 * the collected flag set to a {@link ClassificationResult} using EU AI Act
 * rule logic encoded in priority order.
 *
 * <p>Only called when every answer in the session matched a predefined option.
 * Free-text answers fall through to the LLM path in Stage1InterviewService.
 */
@Component
public class Stage1RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(Stage1RuleEngine.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Walks all saved answers for a session, matches each to its option in
     * the question JSON, and returns the union of all option-level flags.
     *
     * @param answers       all answers for this session, ordered by answered_at
     * @param questionLoader function that loads a question by its key
     * @return mutable set of collected flags, in insertion order
     */
    public Set<String> accumulateFlags(List<InterviewAnswer> answers,
                                        Function<String, InterviewQuestionDto> questionLoader) {
        Set<String> flags = new LinkedHashSet<>();
        for (InterviewAnswer answer : answers) {
            try {
                InterviewQuestionDto question = questionLoader.apply(answer.getQuestionKey());
                List<String> optionFlags = extractFlagsForAnswer(question, answer.getRawInput());
                flags.addAll(optionFlags);
            } catch (Exception e) {
                log.warn("Could not extract flags for question {} answer '{}': {}",
                        answer.getQuestionKey(), answer.getRawInput(), e.getMessage());
            }
        }
        log.debug("Accumulated flags from {} answers: {}", answers.size(), flags);
        return flags;
    }

    /**
     * Maps a collected flag set to a deterministic {@link ClassificationResult}.
     *
     * <p>Priority order (first match wins):
     * <ol>
     *   <li>high_risk or fria_required — high (Article 6); takes precedence so Annex IV review runs</li>
     *   <li>gpai_systemic_risk — high (Article 51)</li>
     *   <li>PROHIBITED — unacceptable (Article 5); only when no confirmed high-risk path</li>
     *   <li>excluded — minimal, outside scope (Article 2(3))</li>
     *   <li>out_of_scope / out_of_scope_territorial / product_manufacturer_only — minimal</li>
     *   <li>authorised_rep_obligations — limited (Article 22)</li>
     *   <li>notify_nca — limited (Article 6(3))</li>
     *   <li>transparency_obligations — limited (Article 50)</li>
     *   <li>gpai_provider — limited (Article 53)</li>
     *   <li>(default) — minimal (Article 4)</li>
     * </ol>
     */
    public ClassificationResult classifyByRules(Set<String> flags) {
        log.info("Rule-based classification with flags: {}", flags);

        if (flags.contains("high_risk") || flags.contains("fria_required")) {
            List<Integer> articles = new ArrayList<>(List.of(4, 6, 9, 10, 11, 12, 13, 14, 15, 16, 17));
            if (flags.contains("fria_required")) {
                articles.add(27);
            }
            String fria = flags.contains("fria_required")
                    ? " As a public-body deployer, a Fundamental Rights Impact Assessment (FRIA) is also " +
                      "required before deployment (Article 27)."
                    : "";
            String prohibited = flags.contains("PROHIBITED")
                    ? " Note: one or more selected practices also fall under Article 5 prohibited practices — " +
                      "this will be addressed in the Annex IV documentation review."
                    : "";
            return result("high", "Article 6",
                    articles,
                    "The system qualifies as high-risk under Annex I or Annex III. Mandatory obligations include: " +
                    "risk management system (Art. 9), data governance (Art. 10), technical documentation (Art. 11), " +
                    "record-keeping (Art. 12), transparency to deployers (Art. 13), human oversight measures (Art. 14), " +
                    "accuracy/robustness requirements (Art. 15), and registration in the EU database (Art. 16)." +
                    fria + prohibited);
        }

        if (flags.contains("gpai_systemic_risk")) {
            return result("high", "Article 51",
                    List.of(4, 51, 52, 53, 54, 55),
                    "The system is a General-Purpose AI (GPAI) model with systemic risk under Article 51 " +
                    "(training compute ≥ 10²⁵ FLOPs or Commission designation). In addition to general GPAI " +
                    "obligations (Art. 53), systemic-risk obligations apply: adversarial testing, incident reporting " +
                    "to the Commission, cybersecurity measures, and energy-efficiency reporting (Art. 55).");
        }

        if (flags.contains("PROHIBITED")) {
            return result("unacceptable", "Article 5",
                    List.of(5),
                    "One or more selected practices are explicitly prohibited under Article 5 of the EU AI Act " +
                    "(e.g. subliminal manipulation, social scoring, real-time remote biometric identification in " +
                    "public spaces). The system must not be placed on the market or put into service in the EU.");
        }

        if (flags.contains("high_risk_exception")) {
            return result("minimal", "Article 2 + Article 112",
                    List.of(2, 112),
                    "Your AI system falls under the High Risk Exception (Article 2). It is a safety component " +
                    "in a product that already requires third-party conformity assessment under existing EU product " +
                    "safety law (Annex I, Section A). The EU AI Act's high-risk obligations do not apply in full. " +
                    "Only Article 112 applies, which primarily places obligations on the Commission to review the Act. " +
                    "Your main obligation is to monitor legislative updates and maintain compliance as the Act evolves.");
        }

        if (flags.contains("excluded")) {
            return result("minimal", "Article 2(3)",
                    List.of(2),
                    "The system is excluded from the EU AI Act's scope by Article 2(3): it is used exclusively " +
                    "for military, national security, or third-country law enforcement purposes. No EU AI Act " +
                    "obligations apply. Note: national law may still impose requirements.");
        }

        if (flags.contains("out_of_scope_territorial")) {
            return result("minimal", "Article 2(1)",
                    List.of(2),
                    "The system has no qualifying EU nexus under Article 2(1): it is not placed on the EU market, " +
                    "outputs are not used in the EU, and no involved party is established in the EU. " +
                    "The EU AI Act does not apply.");
        }

        if (flags.contains("out_of_scope") || flags.contains("product_manufacturer_only")) {
            return result("minimal", "Article 2",
                    List.of(2),
                    "Based on the provided answers the system falls outside the material scope of the EU AI Act. " +
                    "The product manufacturer role without a safety-component nexus does not trigger provider " +
                    "obligations. Minimal obligations (AI literacy, Article 4) may still apply to any deployers.");
        }

        if (flags.contains("authorised_rep_obligations")) {
            return result("limited", "Article 22",
                    List.of(4, 22, 25),
                    "You act as an Authorised Representative (Article 22) for a non-EU provider. Your obligations " +
                    "are defined by your written mandate: registering high-risk systems in the EU database, " +
                    "cooperating with market surveillance authorities, and ensuring the provider's technical " +
                    "documentation is available. The risk category of the underlying system is determined by the " +
                    "provider and is not assessed here.");
        }

        if (flags.contains("notify_nca")) {
            return result("limited", "Article 6(3)",
                    List.of(4, 6, 50),
                    "The system falls within an Annex III domain but does not pose a significant risk to health, " +
                    "safety, or fundamental rights (Article 6(3) self-assessment). It is therefore not classified " +
                    "as high-risk. However, you must notify your national competent authority (NCA) of this " +
                    "self-assessment. Transparency obligations under Article 50 may still apply.");
        }

        if (flags.contains("transparency_obligations")) {
            return result("limited", "Article 50",
                    List.of(4, 50),
                    "The system triggers transparency obligations under Article 50: AI systems that interact " +
                    "directly with persons, generate synthetic content, produce deep fakes, or perform emotion " +
                    "recognition must disclose their AI nature to users. Specific disclosure formats depend on " +
                    "the system's modality and use context.");
        }

        if (flags.contains("gpai_provider")) {
            return result("limited", "Article 53",
                    List.of(4, 53),
                    "The system is a General-Purpose AI (GPAI) model without systemic risk. General GPAI " +
                    "obligations under Article 53 apply: technical documentation, compliance with copyright law, " +
                    "publication of a sufficiently detailed summary of training data, and adherence to the " +
                    "GPAI Code of Practice once adopted.");
        }

        // Default: no elevated risk flags collected
        return result("minimal", "Article 4",
                List.of(4),
                "Based on your answers, this system does not fall into any prohibited, high-risk, or limited-risk " +
                "category under the EU AI Act. Minimal obligations apply: ensure AI literacy among staff who " +
                "operate or use AI systems (Article 4). Voluntary codes of conduct are encouraged under Article 95.");
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private List<String> extractFlagsForAnswer(InterviewQuestionDto question, String answerText) {
        if (question.answersJson() == null || answerText == null) return List.of();
        try {
            var node = MAPPER.readTree(question.answersJson());
            var options = node.get("options");
            if (options == null || !options.isArray()) return List.of();

            // Support comma-separated multi-select answers
            String[] parts = answerText.split(",");
            Set<String> allFlags = new LinkedHashSet<>();

            for (String part : parts) {
                String normalised = part.trim().toLowerCase();
                if (normalised.isEmpty()) continue;

                for (var option : options) {
                    String value = option.has("value") ? option.get("value").asText() : "";
                    String label = option.has("label") ? option.get("label").asText() : "";

                    boolean matched = normalised.equals(value.toLowerCase())
                            || normalised.equals(label.toLowerCase())
                            || label.toLowerCase().startsWith(normalised)
                            || normalised.startsWith(value.toLowerCase());

                    if (matched) {
                        var flagsNode = option.get("flags");
                        if (flagsNode != null && flagsNode.isArray()) {
                            for (var f : flagsNode) allFlags.add(f.asText());
                        }
                        break; // move to next comma-part
                    }
                }
            }
            return new ArrayList<>(allFlags);
        } catch (Exception e) {
            log.warn("Failed to extract flags for question {}: {}", question.questionKey(), e.getMessage());
        }
        return List.of();
    }

    private static ClassificationResult result(String riskCategory, String primaryLegalBasis,
                                                List<Integer> articles, String reasoning) {
        return new ClassificationResult(
                riskCategory,
                primaryLegalBasis,
                articles,
                "high",
                reasoning,
                List.of()
        );
    }
}
