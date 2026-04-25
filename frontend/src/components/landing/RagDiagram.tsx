import { motion, useReducedMotion } from "framer-motion";

/**
 * RagDiagram — inline SVG of REGU's retrieval-augmented architecture.
 * 720 × 480 viewBox.
 *
 * Layout:
 *   [User input]
 *        ↓
 *   [Voyage embedding]
 *        ↓
 *   [4 chunk tables in a row]
 *        ↓
 *   [Gemini 2.5 Flash]
 *        ↓
 *   [Citation validator]
 *        ↓
 *   [Structured report]
 */

const HAIR = "rgba(255,255,255,0.18)";
const HAIR_SOFT = "rgba(255,255,255,0.06)";
const FILL = "#0E1530";
const BRAND = "#3D5AFE";
const INK_PRIMARY = "#F5F7FA";
const INK_SECONDARY = "#B5BCCB";
const INK_TERTIARY = "#7C849A";

type BoxProps = {
  x: number;
  y: number;
  w: number;
  h: number;
  children: React.ReactNode;
  highlight?: boolean;
};

function Box({ x, y, w, h, children, highlight }: BoxProps) {
  return (
    <g>
      <rect
        x={x}
        y={y}
        width={w}
        height={h}
        rx={10}
        fill={FILL}
        stroke={highlight ? BRAND : HAIR}
        strokeWidth={highlight ? 1.25 : 1}
      />
      {children}
    </g>
  );
}

type LineProps = {
  d: string;
  delay: number;
  reduce: boolean | null;
};

function Line({ d, delay, reduce }: LineProps) {
  return (
    <motion.path
      d={d}
      stroke={BRAND}
      strokeOpacity={0.55}
      strokeWidth={1.5}
      fill="none"
      strokeLinecap="round"
      markerEnd="url(#rag-arrow)"
      initial={reduce ? { pathLength: 1 } : { pathLength: 0 }}
      whileInView={{ pathLength: 1 }}
      viewport={{ once: true, margin: "0px 0px -10% 0px" }}
      transition={{ duration: 0.6, delay, ease: [0.22, 1, 0.36, 1] }}
    />
  );
}

export default function RagDiagram() {
  const reduce = useReducedMotion();

  // Y bands
  const Y_INPUT = 18;
  const Y_EMBED = 100;
  const Y_TABLES = 200;
  const Y_LLM = 308;
  const Y_VALID = 376;
  const Y_REPORT = 444;

  const W = 720;
  const CX = W / 2;

  return (
    <svg
      viewBox="0 0 720 480"
      width="100%"
      role="img"
      aria-label="REGU retrieval-augmented architecture diagram"
      className="block max-w-full"
      style={{ height: "auto" }}
    >
      <defs>
        <marker
          id="rag-arrow"
          markerWidth="6"
          markerHeight="6"
          refX="5.5"
          refY="3"
          orient="auto"
          markerUnits="strokeWidth"
        >
          <path d="M0,0 L6,3 L0,6 Z" fill={BRAND} fillOpacity={0.7} />
        </marker>
      </defs>

      {/* Backdrop guide lines */}
      <line
        x1={0}
        y1={Y_TABLES - 32}
        x2={W}
        y2={Y_TABLES - 32}
        stroke={HAIR_SOFT}
        strokeDasharray="2 4"
      />
      <line
        x1={0}
        y1={Y_LLM - 22}
        x2={W}
        y2={Y_LLM - 22}
        stroke={HAIR_SOFT}
        strokeDasharray="2 4"
      />

      {/* --- USER INPUT --- */}
      <Box x={150} y={Y_INPUT} w={420} h={54}>
        <text
          x={CX}
          y={Y_INPUT + 23}
          textAnchor="middle"
          fontSize="11"
          fontWeight={500}
          fill={INK_TERTIARY}
          letterSpacing="1.4"
        >
          USER INPUT
        </text>
        <text
          x={CX}
          y={Y_INPUT + 42}
          textAnchor="middle"
          fontSize="13"
          fill={INK_PRIMARY}
        >
          Natural-language description · PDF · DOCX
        </text>
      </Box>

      <Line
        d={`M${CX} ${Y_INPUT + 54} L${CX} ${Y_EMBED - 4}`}
        delay={0.1}
        reduce={reduce}
      />

      {/* --- EMBEDDING --- */}
      <Box x={230} y={Y_EMBED} w={260} h={64}>
        <text
          x={CX}
          y={Y_EMBED + 22}
          textAnchor="middle"
          fontSize="11"
          fontWeight={500}
          fill={INK_TERTIARY}
          letterSpacing="1.4"
        >
          EMBEDDING
        </text>
        <text
          x={CX}
          y={Y_EMBED + 40}
          textAnchor="middle"
          fontSize="13.5"
          fontWeight={500}
          fill={INK_PRIMARY}
          fontFamily="ui-monospace, SF Mono, Menlo, monospace"
        >
          voyage-3-large
        </text>
        <text
          x={CX}
          y={Y_EMBED + 55}
          textAnchor="middle"
          fontSize="11"
          fill={INK_SECONDARY}
        >
          1024-dim · Matryoshka-truncated
        </text>
      </Box>

      {/* Fan-out */}
      <Line
        d={`M${CX} ${Y_EMBED + 64} L${CX} ${Y_TABLES - 32} L 102 ${Y_TABLES - 32} L 102 ${Y_TABLES - 4}`}
        delay={0.2}
        reduce={reduce}
      />
      <Line
        d={`M${CX} ${Y_EMBED + 64} L${CX} ${Y_TABLES - 32} L 268 ${Y_TABLES - 32} L 268 ${Y_TABLES - 4}`}
        delay={0.24}
        reduce={reduce}
      />
      <Line
        d={`M${CX} ${Y_EMBED + 64} L${CX} ${Y_TABLES - 32} L 434 ${Y_TABLES - 32} L 434 ${Y_TABLES - 4}`}
        delay={0.28}
        reduce={reduce}
      />
      <Line
        d={`M${CX} ${Y_EMBED + 64} L${CX} ${Y_TABLES - 32} L 600 ${Y_TABLES - 32} L 600 ${Y_TABLES - 4}`}
        delay={0.32}
        reduce={reduce}
      />

      {/* --- 4 TABLES --- */}
      {[
        { x: 24, name: "legal_chunks", rows: "885 rows", method: "Hybrid RRF" },
        { x: 190, name: "use_case_chunks", rows: "15+ rows", method: "Vector + meta" },
        { x: 356, name: "guide_chunks", rows: "Commission", method: "Header-aware" },
        { x: 522, name: "decision_rule_chunks", rows: "40 FLI rules", method: "Procedural" },
      ].map((t) => {
        const w = 156;
        const h = 88;
        const cx = t.x + w / 2;
        return (
          <Box key={t.name} x={t.x} y={Y_TABLES} w={w} h={h}>
            <text
              x={cx}
              y={Y_TABLES + 26}
              textAnchor="middle"
              fontSize="12.5"
              fontWeight={500}
              fill={INK_PRIMARY}
              fontFamily="ui-monospace, SF Mono, Menlo, monospace"
            >
              {t.name}
            </text>
            <text
              x={cx}
              y={Y_TABLES + 50}
              textAnchor="middle"
              fontSize="11.5"
              fill={INK_SECONDARY}
            >
              {t.rows}
            </text>
            <text
              x={cx}
              y={Y_TABLES + 68}
              textAnchor="middle"
              fontSize="10.5"
              fill={INK_TERTIARY}
            >
              {t.method}
            </text>
          </Box>
        );
      })}

      {/* Fan-in */}
      <Line
        d={`M 102 ${Y_TABLES + 88} L 102 ${Y_LLM - 22} L${CX} ${Y_LLM - 22} L${CX} ${Y_LLM - 4}`}
        delay={0.5}
        reduce={reduce}
      />
      <Line
        d={`M 268 ${Y_TABLES + 88} L 268 ${Y_LLM - 22} L${CX} ${Y_LLM - 22}`}
        delay={0.54}
        reduce={reduce}
      />
      <Line
        d={`M 434 ${Y_TABLES + 88} L 434 ${Y_LLM - 22} L${CX} ${Y_LLM - 22}`}
        delay={0.58}
        reduce={reduce}
      />
      <Line
        d={`M 600 ${Y_TABLES + 88} L 600 ${Y_LLM - 22} L${CX} ${Y_LLM - 22}`}
        delay={0.62}
        reduce={reduce}
      />

      {/* --- LLM --- */}
      <Box x={210} y={Y_LLM} w={300} h={52} highlight>
        <text
          x={CX}
          y={Y_LLM + 22}
          textAnchor="middle"
          fontSize="13"
          fontWeight={500}
          fill={INK_PRIMARY}
        >
          Gemini 2.5 Flash
        </text>
        <text
          x={CX}
          y={Y_LLM + 39}
          textAnchor="middle"
          fontSize="11"
          fill={INK_SECONDARY}
        >
          structured citation schema
        </text>
      </Box>

      <Line
        d={`M${CX} ${Y_LLM + 52} L${CX} ${Y_VALID - 4}`}
        delay={0.74}
        reduce={reduce}
      />

      {/* --- VALIDATOR --- */}
      <Box x={210} y={Y_VALID} w={300} h={52}>
        <text
          x={CX}
          y={Y_VALID + 22}
          textAnchor="middle"
          fontSize="13"
          fontWeight={500}
          fill={INK_PRIMARY}
        >
          Citation validator
        </text>
        <text
          x={CX}
          y={Y_VALID + 39}
          textAnchor="middle"
          fontSize="11"
          fill={INK_SECONDARY}
        >
          {">"}20% invalid → regenerate once
        </text>
      </Box>

      <Line
        d={`M${CX} ${Y_VALID + 52} L${CX} ${Y_REPORT - 4}`}
        delay={0.86}
        reduce={reduce}
      />

      {/* --- REPORT --- */}
      <Box x={150} y={Y_REPORT} w={420} h={36} highlight>
        <text
          x={CX}
          y={Y_REPORT + 23}
          textAnchor="middle"
          fontSize="13"
          fontWeight={500}
          fill={INK_PRIMARY}
        >
          Structured report — paragraph-level citations
        </text>
      </Box>
    </svg>
  );
}
