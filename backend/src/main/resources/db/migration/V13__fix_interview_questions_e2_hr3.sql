-- Fix E2: question text was framed as if only deployers/distributors reach it.
-- Per FLI Compliance Checker PDF, E2 is asked of ALL entity types (including providers)
-- and covers modifications made by the entity OR any downstream party.
UPDATE interview_questions
SET
    display_text = 'Do you (OR a downstream deployer, distributor, or importer) make any of the following modifications to your system? Select all that apply.',
    hint_text    = 'If a deployer, distributor, or importer performs any of these modifications, they become legally treated as the Provider of that modified system under Article 25. The original provider is then relieved of provider obligations for that modified version, but must supply the new provider with technical documentation, access, and materials (''Handover'' obligation). Select ''None of the above'' if no party in your supply chain has made such a change.',
    answers      = '{
  "type": "multi_select",
  "allow_free_text": true,
  "options": [
    {
      "value": "putting_different_name_trademark",
      "label": "Putting a different name or trademark on the system",
      "next_key": "HR1",
      "is_terminal": false,
      "obligations": ["become_provider"],
      "flags": ["become_provider"],
      "keywords": ["rebrand","different name","own trademark","white label","rename AI","market under own brand","OEM branding"]
    },
    {
      "value": "modifying_intended_purpose",
      "label": "Modifying the intended purpose of a system already in operation",
      "next_key": "HR1",
      "is_terminal": false,
      "obligations": ["become_provider"],
      "flags": ["become_provider"],
      "keywords": ["change purpose","modify intended purpose","repurpose AI","new use case","extend use beyond original scope","out-of-scope use"]
    },
    {
      "value": "performing_substantial_modification",
      "label": "Performing a substantial modification (see Article 3 point 23) to the system",
      "next_key": "HR1",
      "is_terminal": false,
      "obligations": ["become_provider"],
      "flags": ["become_provider"],
      "keywords": ["substantial modification","retrain model","fine-tune","significant architectural change","major update affecting compliance"]
    },
    {
      "value": "none_of_the_above",
      "label": "None of the above",
      "next_key": "HR1",
      "is_terminal": false,
      "obligations": [],
      "flags": [],
      "keywords": ["no modification","not modified","unchanged","original system","none of these"]
    }
  ]
}'::jsonb
WHERE question_key = 'E2';

-- Fix HR3: YES answer previously routed to S1 with high_risk flag.
-- Per FLI PDF, if the product already requires third-party conformity assessment
-- under existing EU product law (Annex I Section A), only Article 112 applies
-- (High Risk Exception). This is END, not a full high-risk classification.
UPDATE interview_questions
SET
    answers = '{
  "type": "single_select",
  "allow_free_text": true,
  "options": [
    {
      "value": "yes_third_party_required",
      "label": "Yes — the product requires a third-party conformity assessment by a notified body under the applicable regulation",
      "next_key": null,
      "is_terminal": true,
      "obligations": ["high_risk_exception"],
      "flags": ["high_risk_exception"],
      "keywords": ["notified body","third-party assessment","CE marking notified body","conformity assessment required","independent certification","type-examination","MDR notified body","Article 6(1)"]
    },
    {
      "value": "no_self_declaration_sufficient",
      "label": "No — the product only requires a manufacturer''s self-declaration of conformity (no notified body involved)",
      "next_key": "HR4",
      "is_terminal": false,
      "obligations": [],
      "flags": [],
      "keywords": ["self-declaration","manufacturer declaration","no notified body","self-certified","DoC only","self-assessment sufficient","Article 6(1) not met"]
    }
  ]
}'::jsonb
WHERE question_key = 'HR3';
