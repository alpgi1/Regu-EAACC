/**
 * FileDrop — drag-and-drop + click-to-browse file zone.
 *
 * Accepts PDF, DOCX, TXT. Validates MIME on the client side.
 * States: empty, dragging-over, file-selected, rejecting.
 * Calls onFile(file) — does NOT upload by itself.
 * Keyboard: Enter opens file picker.
 * No third-party drag-drop lib — native HTML5 events.
 */

import { useState, useRef, useCallback, type KeyboardEvent } from "react";
import { FileText, X } from "lucide-react";
import { cn } from "@/lib/utils";

const ACCEPTED_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "text/plain",
]);

const ACCEPTED_EXTENSIONS = new Set([".pdf", ".docx", ".txt"]);

const ACCEPT_STRING =
  "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain";

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function validateFile(file: File): string | null {
  const ext = "." + file.name.split(".").pop()?.toLowerCase();
  if (!ACCEPTED_TYPES.has(file.type) && !ACCEPTED_EXTENSIONS.has(ext)) {
    return "Unsupported file type. Please upload a PDF, DOCX, or TXT file.";
  }
  // Backend limit is 10MB
  if (file.size > 10 * 1024 * 1024) {
    return "File exceeds the 10 MB limit.";
  }
  return null;
}

interface FileDropProps {
  onFile: (file: File) => void;
  disabled?: boolean;
  className?: string;
}

export function FileDrop({ onFile, disabled, className }: FileDropProps) {
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<File | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const dragCounter = useRef(0);

  const handleFile = useCallback(
    (file: File) => {
      const err = validateFile(file);
      if (err) {
        setError(err);
        setSelected(null);
        return;
      }
      setError(null);
      setSelected(file);
      onFile(file);
    },
    [onFile],
  );

  const onDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current++;
    setDragging(true);
  }, []);

  const onDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current--;
    if (dragCounter.current === 0) setDragging(false);
  }, []);

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragging(false);
      dragCounter.current = 0;

      const file = e.dataTransfer.files[0];
      if (file) handleFile(file);
    },
    [handleFile],
  );

  const onInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) handleFile(file);
      // Reset so the same file can be re-selected
      e.target.value = "";
    },
    [handleFile],
  );

  const onKeyDown = useCallback(
    (e: KeyboardEvent<HTMLDivElement>) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        inputRef.current?.click();
      }
    },
    [],
  );

  const clear = useCallback(() => {
    setSelected(null);
    setError(null);
  }, []);

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      {!selected ? (
        <div
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-label="Upload a file. Press Enter to browse."
          onDragEnter={onDragEnter}
          onDragLeave={onDragLeave}
          onDragOver={onDragOver}
          onDrop={onDrop}
          onKeyDown={onKeyDown}
          onClick={() => !disabled && inputRef.current?.click()}
          className={cn(
            "flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-6 cursor-pointer transition-colors duration-150",
            "text-sm text-[var(--color-regu-fg-muted)]",
            dragging
              ? "border-[var(--color-regu-accent)] bg-[var(--color-regu-surface-2)]"
              : error
                ? "border-[var(--color-regu-danger)] bg-[var(--color-regu-surface)]"
                : "border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] hover:border-[var(--color-regu-border-hi)]",
            disabled && "pointer-events-none opacity-40",
          )}
        >
          <FileText
            size={24}
            className="text-[var(--color-regu-fg-subtle)]"
            aria-hidden
          />
          <span>
            Drag and drop a file here, or{" "}
            <span className="text-[var(--color-regu-accent)] underline underline-offset-2">
              browse
            </span>
          </span>
          <span className="text-xs text-[var(--color-regu-fg-subtle)]">
            PDF, DOCX, or TXT up to 10 MB
          </span>
        </div>
      ) : (
        <div className="flex items-center gap-3 rounded-xl border border-[var(--color-regu-border)] bg-[var(--color-regu-surface)] px-4 py-3 text-sm">
          <FileText
            size={18}
            className="shrink-0 text-[var(--color-regu-fg-subtle)]"
            aria-hidden
          />
          <span className="truncate text-[var(--color-regu-fg)]">
            {selected.name}
          </span>
          <span className="shrink-0 text-[var(--color-regu-fg-subtle)]">
            {formatSize(selected.size)}
          </span>
          <button
            onClick={(e) => {
              e.stopPropagation();
              clear();
            }}
            className="ml-auto shrink-0 rounded p-1 text-[var(--color-regu-fg-subtle)] hover:text-[var(--color-regu-fg)] hover:bg-[var(--color-regu-surface-2)] transition-colors"
            aria-label="Remove selected file"
            type="button"
          >
            <X size={14} aria-hidden />
          </button>
        </div>
      )}

      {error && (
        <p className="text-xs text-[var(--color-regu-danger)]" role="alert">
          {error}
        </p>
      )}

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT_STRING}
        onChange={onInputChange}
        className="sr-only"
        tabIndex={-1}
        aria-hidden
      />
    </div>
  );
}
