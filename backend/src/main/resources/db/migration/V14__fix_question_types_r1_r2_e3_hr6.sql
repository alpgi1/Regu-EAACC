-- R1: single_select → multi_select (PDF: "Tick all that apply")
UPDATE interview_questions
SET answers = jsonb_set(answers, '{type}', '"multi_select"')
WHERE question_key = 'R1';

-- R2: single_select → multi_select + separate military/third-country per PDF
UPDATE interview_questions
SET answers = '{
  "type": "multi_select",
  "allow_free_text": true,
  "options": [
    {
      "value": "military_exclusive",
      "label": "AI systems developed and used exclusively for military purposes",
      "next_key": null,
      "is_terminal": true,
      "obligations": [],
      "flags": ["excluded"],
      "keywords": ["military AI","defence AI","armed forces AI","national security AI","Article 2(2) military exclusion"]
    },
    {
      "value": "third_country_law_enforcement",
      "label": "Public authorities or international organisations in third countries using AI systems for law enforcement and judicial cooperation",
      "next_key": null,
      "is_terminal": true,
      "obligations": [],
      "flags": ["excluded"],
      "keywords": ["third country law enforcement","international organisation law enforcement","non-EU police AI","judicial cooperation third country","Article 2(3) exclusion"]
    },
    {
      "value": "research_development",
      "label": "AI research and development activity — the system exists solely for scientific R&D",
      "next_key": "R3",
      "is_terminal": false,
      "obligations": [],
      "flags": ["exclusion_partial"],
      "keywords": ["research","R&D","scientific","academic","experiment","prototype","not deployed","sole purpose research"]
    },
    {
      "value": "open_source",
      "label": "AI components provided under free and open source licences",
      "next_key": "R3",
      "is_terminal": false,
      "obligations": [],
      "flags": ["exclusion_partial"],
      "keywords": ["open source","free licence","MIT","Apache","GPL","open weights","community model"]
    },
    {
      "value": "personal_use",
      "label": "People using AI systems for purely personal, non-professional activity",
      "next_key": "R3",
      "is_terminal": false,
      "obligations": [],
      "flags": ["exclusion_partial"],
      "keywords": ["personal","private","hobby","non-professional","not for work","home use"]
    },
    {
      "value": "none_of_the_above",
      "label": "None of the above — no exclusion applies; the EU AI Act applies in full to this system",
      "next_key": "R3",
      "is_terminal": false,
      "obligations": [],
      "flags": [],
      "keywords": ["no exclusion","full scope","Act applies","not excluded","none of these"]
    }
  ]
}'::jsonb
WHERE question_key = 'R2';

-- E3: single_select → multi_select + add second PDF criteria option
UPDATE interview_questions
SET
    display_text = 'Does your product integrate an AI system AND meet either of the following criteria? Select all that apply.',
    hint_text    = 'This applies ONLY if your product is placed on the market or put into service within the EU, regardless of whether or not you are established within the EU. Under Article 25 point 3 and Annex I, a product manufacturer who places or puts into service an AI system under their own name or trademark is treated as a provider of that AI system.',
    answers      = '{
  "type": "multi_select",
  "allow_free_text": true,
  "options": [
    {
      "value": "placed_on_market_with_product",
      "label": "The AI system was / will be placed on the market together with my product under my manufacturer name or trademark",
      "next_key": "HR6",
      "is_terminal": false,
      "obligations": ["product_manufacturer_obligations"],
      "flags": ["potential_provider","product_manufacturer"],
      "keywords": ["place on market with product","sell AI in product","AI inside product on market","product with AI bundled"]
    },
    {
      "value": "put_into_service_under_trademark",
      "label": "The AI system was / will be put into service under my manufacturer name or trademark after my product has been placed on the market",
      "next_key": "HR6",
      "is_terminal": false,
      "obligations": ["product_manufacturer_obligations"],
      "flags": ["potential_provider","product_manufacturer"],
      "keywords": ["put into service after market","deploy AI in product post-sale","AI update to existing product","firmware AI update"]
    },
    {
      "value": "none_of_the_above",
      "label": "None of the above",
      "next_key": null,
      "is_terminal": true,
      "obligations": [],
      "flags": ["out_of_scope"],
      "keywords": ["internal use only","not releasing product","not on market","no embedded AI","none of these"]
    }
  ]
}'::jsonb
WHERE question_key = 'E3';

-- HR6: complete rewrite — was a single generic option, now 12 specific Annex I Section A categories per PDF
UPDATE interview_questions
SET
    display_text = 'Does your product include an AI system as a ''safety component'' AND fall within any of the following categories? Select all that apply.',
    hint_text    = 'Safety component: a component of a product or of a system which fulfils a safety function for that product or system, or the failure or malfunctioning of which endangers the health and safety of persons or property (Article 3 point 14). If you select any category below, your AI system is high-risk under Article 6(1) and you will be treated as its Provider.',
    answers      = '{
  "type": "multi_select",
  "allow_free_text": false,
  "options": [
    {"value":"machinery","label":"Machinery","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["machinery","industrial machine"]},
    {"value":"toys","label":"Toys","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["toys","children products"]},
    {"value":"recreational_craft_personal_watercraft","label":"Recreational craft and personal watercraft","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["recreational craft","personal watercraft","boat","jet ski"]},
    {"value":"lifts_safety_components","label":"Lifts and safety components of lifts","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["lifts","elevators"]},
    {"value":"explosive_atmospheres_equipment","label":"Equipment and protective systems intended for use in potentially explosive atmospheres","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["explosive atmospheres","ATEX","hazardous areas"]},
    {"value":"radio_equipment","label":"Radio equipment","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["radio equipment","wireless devices"]},
    {"value":"pressure_equipment","label":"Pressure equipment","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["pressure equipment","pressure vessels","boilers"]},
    {"value":"cableway_installations","label":"Cableway installations","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["cableway","cable car","ski lift"]},
    {"value":"personal_protective_equipment","label":"Personal protective equipment","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["PPE","protective equipment","safety gear"]},
    {"value":"gas_appliances","label":"Appliances burning gaseous fuels","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["gas appliances","gaseous fuels","boiler"]},
    {"value":"medical_devices","label":"Medical devices","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["medical devices","MDR","healthcare device"]},
    {"value":"in_vitro_diagnostic_medical_devices","label":"In vitro diagnostic medical devices","next_key":"S1","is_terminal":false,"obligations":["high_risk_provider_obligations","become_provider"],"flags":["high_risk","become_provider"],"keywords":["IVD","in vitro diagnostic","IVDR"]},
    {"value":"none_of_the_above","label":"None of the above","next_key":null,"is_terminal":true,"obligations":["product_manufacturer_obligations"],"flags":["product_manufacturer_only"],"keywords":["none","not a safety component","none of these"]}
  ]
}'::jsonb
WHERE question_key = 'HR6';
