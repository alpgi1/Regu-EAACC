#!/bin/bash
# Script to load interview questions directly into the database
# Bypasses the full ingestion pipeline (which requires Voyage AI API key for embeddings)

CORPUS_DIR="/Users/alpgiraycelik/Desktop/REGU Eu Ai Act/regu/corpus/interview_questions/stage1"
CONTAINER="regu-postgres"
DB="regu_db"
USER="regu_user"

for file in "$CORPUS_DIR"/q_*.json; do
  echo "Processing: $(basename "$file")"
  
  # Extract fields from JSON using python3
  python3 -c "
import json, sys

with open('$file') as f:
    q = json.load(f)

key = q['question_key']
stage = q['stage']
section = q['section']
display = q['display_text'].replace(\"'\", \"''\")
hint = q.get('hint_text') or ''
hint = hint.replace(\"'\", \"''\")
answers = json.dumps(q['answers']).replace(\"'\", \"''\")
preconditions = json.dumps(q['preconditions']) if q.get('preconditions') else 'NULL'
if preconditions != 'NULL':
    preconditions = preconditions.replace(\"'\", \"''\")
    preconditions = f\"'{preconditions}'::jsonb\"
linked_articles = q.get('linked_articles')
if linked_articles:
    arr = '{' + ','.join(str(x) for x in linked_articles) + '}'
    linked_articles_sql = f\"'{arr}'::integer[]\"
else:
    linked_articles_sql = 'NULL'
is_terminal = 'true' if q.get('is_terminal') else 'false'

sql = f\"\"\"INSERT INTO interview_questions (question_key, stage, section, display_text, hint_text, answers, preconditions, linked_rule_chunk, linked_articles, is_terminal)
VALUES ('{key}', {stage}, '{section}', '{display}', '{hint}', '{answers}'::jsonb, {preconditions}, NULL, {linked_articles_sql}, {is_terminal})
ON CONFLICT (question_key) DO NOTHING;\"\"\"

print(sql)
" | docker exec -i "$CONTAINER" psql -U "$USER" -d "$DB" -q
done

echo ""
echo "=== Verification ==="
docker exec "$CONTAINER" psql -U "$USER" -d "$DB" -c "SELECT question_key, section, is_terminal FROM interview_questions ORDER BY question_key;"
