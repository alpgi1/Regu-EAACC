#!/bin/bash
curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY" \
-H 'Content-Type: application/json' \
-d '{
  "systemInstruction": {"parts": [{"text": "You are a legal AI. Respond ONLY with valid JSON."}]},
  "contents": [{"parts": [{"text": "Classify: Authorised representative for high risk AI system. \nReturn fields riskCategory, applicableArticles"}]}],
  "generationConfig": {"maxOutputTokens": 2048, "temperature": 0.1, "responseMimeType": "application/json"}
}'
