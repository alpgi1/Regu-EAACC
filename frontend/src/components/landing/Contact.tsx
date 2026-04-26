import { useState } from "react";
import { Mail, MapPin, Calendar, Check } from "lucide-react";
import { cn } from "@/lib/utils";

export default function Contact() {
  const [submitted, setSubmitted] = useState(false);

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSubmitted(true);
  }

  return (
    <section
      id="contact"
      className="bg-[var(--bg-base)] py-20 md:py-32 border-t border-[var(--hairline)]"
    >
      <div className="max-w-[1200px] mx-auto px-6 md:px-10 grid grid-cols-1 lg:grid-cols-12 gap-12">
        <div className="lg:col-span-5">
          <p
            className="uppercase tracking-[0.14em] text-[var(--ink-tertiary)] font-medium mb-6"
            style={{ fontSize: "var(--text-micro)" }}
          >
            Get in touch
          </p>
          <h2
            className="font-semibold tracking-[-0.025em] text-[var(--ink-primary)]"
            style={{
              fontSize: "clamp(2rem, 4vw, 3rem)",
              lineHeight: "1.05",
            }}
          >
            Talking to a human helps.
          </h2>
          <p
            className="mt-6 text-[var(--ink-secondary)] max-w-[44ch]"
            style={{ fontSize: "var(--text-body-lg)" }}
          >
            Whether you're scoping your AI Act risk for the first time or
            stuck on a specific Annex IV section - we read every message.
          </p>

          <div className="mt-10 flex flex-col gap-4">
            <a
              href="mailto:alpgiray.dev@gmail.com"
              className="flex items-center gap-3 text-[15px] text-[var(--ink-secondary)] hover:text-[var(--ink-primary)] transition-colors duration-[var(--dur-fast)]"
            >
              <Mail size={16} className="text-[var(--ink-tertiary)]" aria-hidden />
              alpgiray.dev@gmail.com
            </a>
            <a
              href="mailto:cnumanberk@gmail.com"
              className="flex items-center gap-3 text-[15px] text-[var(--ink-secondary)] hover:text-[var(--ink-primary)] transition-colors duration-[var(--dur-fast)]"
            >
              <Mail size={16} className="text-[var(--ink-tertiary)]" aria-hidden />
              cnumanberk@gmail.com
            </a>
            <div className="flex items-center gap-3 text-[15px] text-[var(--ink-secondary)]">
              <MapPin size={16} className="text-[var(--ink-tertiary)]" aria-hidden />
              Built in the EU
            </div>
            <a
              href="/coming-soon"
              className="flex items-center gap-3 text-[15px] text-[var(--ink-secondary)] hover:text-[var(--ink-primary)] transition-colors duration-[var(--dur-fast)]"
            >
              <Calendar size={16} className="text-[var(--ink-tertiary)]" aria-hidden />
              Book a call
            </a>
          </div>
        </div>

        <div className="lg:col-span-7">
          {submitted ? (
            <div
              className="rounded-2xl border border-[var(--hairline)] bg-[var(--bg-elev-1)] p-10 flex flex-col items-start gap-4"
              role="status"
              aria-live="polite"
            >
              <span
                className="h-10 w-10 rounded-full inline-flex items-center justify-center"
                style={{
                  background: "rgba(60,203,127,0.12)",
                  border: "1px solid rgba(60,203,127,0.28)",
                  color: "var(--ok)",
                }}
              >
                <Check size={18} aria-hidden />
              </span>
              <h3
                className="font-semibold text-[var(--ink-primary)]"
                style={{ fontSize: "1.25rem", letterSpacing: "-0.01em" }}
              >
                Thanks - message received.
              </h3>
              <p className="text-[var(--ink-secondary)] text-[15px] leading-relaxed">
                We'll reply within one business day. If it's urgent, email{" "}
                <a href="mailto:alpgiray.dev@gmail.com" className="text-[var(--brand-400)] hover:underline">alpgiray.dev@gmail.com</a>
                {" "}or{" "}
                <a href="mailto:cnumanberk@gmail.com" className="text-[var(--brand-400)] hover:underline">cnumanberk@gmail.com</a>
                {" "}directly.
              </p>
            </div>
          ) : (
            <form
              onSubmit={onSubmit}
              className="rounded-2xl border border-[var(--hairline)] bg-[var(--bg-elev-1)] p-6 md:p-8 flex flex-col gap-5"
            >
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <Field label="Name" name="name" required />
                <Field
                  label="Work email"
                  name="email"
                  type="email"
                  required
                />
              </div>
              <Field label="Company" name="company" />
              <FieldArea label="What are you building?" name="message" />
              <button
                type="submit"
                className={cn(
                  "h-12 px-6 self-start rounded-full font-medium text-[15px]",
                  "bg-[var(--brand-500)] text-white",
                  "hover:bg-[var(--brand-400)] transition-colors duration-[var(--dur-fast)]"
                )}
              >
                Send
              </button>
            </form>
          )}
        </div>
      </div>
    </section>
  );
}

const inputClass = cn(
  "w-full rounded-lg px-4 py-3 text-[15px] text-[var(--ink-primary)]",
  "bg-[var(--bg-elev-2)] border border-[var(--hairline)]",
  "placeholder:text-[var(--ink-tertiary)]",
  "transition-[border-color,box-shadow] duration-[var(--dur-fast)]",
  "focus:outline-none focus:border-[var(--brand-500)] focus:shadow-[0_0_0_3px_var(--brand-glow)]"
);

function Field({
  label,
  name,
  type = "text",
  required,
}: {
  label: string;
  name: string;
  type?: string;
  required?: boolean;
}) {
  return (
    <label className="flex flex-col gap-2">
      <span
        className="text-[var(--ink-tertiary)] uppercase tracking-[0.08em]"
        style={{ fontSize: "var(--text-micro)" }}
      >
        {label}
        {required && " *"}
      </span>
      <input type={type} name={name} required={required} className={inputClass} />
    </label>
  );
}

function FieldArea({ label, name }: { label: string; name: string }) {
  return (
    <label className="flex flex-col gap-2">
      <span
        className="text-[var(--ink-tertiary)] uppercase tracking-[0.08em]"
        style={{ fontSize: "var(--text-micro)" }}
      >
        {label}
      </span>
      <textarea
        name={name}
        rows={5}
        className={cn(inputClass, "resize-y min-h-[120px]")}
      />
    </label>
  );
}
