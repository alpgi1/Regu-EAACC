/**
 * Contact — two-column: left column (contact info) + right column (form).
 * Form submission is a no-op; shows inline success state on click.
 * TODO: POST /api/v1/contact (not yet implemented)
 */

import { useState } from "react";
import { m as motion, useReducedMotion } from "framer-motion";
import { Mail, MapPin, Calendar } from "lucide-react";
import { Section } from "@/components/ui/section";
import { Button } from "@/components/ui/button";
import { cn, EASE_SMOOTH } from "@/lib/utils";

const reveal = {
  hidden: { opacity: 0, y: 16, filter: "blur(6px)" },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    filter: "blur(0px)",
    transition: { duration: 0.55, delay: i * 0.1, ease: EASE_SMOOTH },
  }),
};

interface FieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  id: string;
}

function Field({ label, id, ...props }: FieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label
        htmlFor={id}
        className="text-xs font-medium uppercase tracking-[0.1em] text-[rgba(235,235,235,0.38)]"
      >
        {label}
      </label>
      <input
        id={id}
        {...props}
        className={cn(
          "rounded-lg px-4 py-2.5 text-sm",
          "bg-[#0A0F1F] border border-[rgba(235,235,235,0.08)]",
          "text-[#EBEBEB] placeholder:text-[rgba(235,235,235,0.28)]",
          "focus-visible:outline-none focus-visible:border-[#2A52BE] focus-visible:ring-1 focus-visible:ring-[#2A52BE]",
          "transition-colors duration-150",
          props.className
        )}
      />
    </div>
  );
}

interface TextAreaFieldProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  id: string;
}

function TextAreaField({ label, id, ...props }: TextAreaFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label
        htmlFor={id}
        className="text-xs font-medium uppercase tracking-[0.1em] text-[rgba(235,235,235,0.38)]"
      >
        {label}
      </label>
      <textarea
        id={id}
        {...props}
        className={cn(
          "rounded-lg px-4 py-2.5 text-sm resize-none",
          "bg-[#0A0F1F] border border-[rgba(235,235,235,0.08)]",
          "text-[#EBEBEB] placeholder:text-[rgba(235,235,235,0.28)]",
          "focus-visible:outline-none focus-visible:border-[#2A52BE] focus-visible:ring-1 focus-visible:ring-[#2A52BE]",
          "transition-colors duration-150",
          props.className
        )}
      />
    </div>
  );
}

export default function Contact() {
  const prefersReduced = useReducedMotion();
  const [submitted, setSubmitted] = useState(false);
  const [form, setForm] = useState({
    name: "",
    email: "",
    company: "",
    message: "",
  });

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
    setForm((prev) => ({ ...prev, [e.target.id]: e.target.value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // TODO: POST /api/v1/contact (not yet implemented)
    setSubmitted(true);
    setForm({ name: "", email: "", company: "", message: "" });
  }

  return (
    <Section id="contact" className="bg-[#060814]" eyebrow="Get in touch">
      <div className="grid lg:grid-cols-2 gap-12 items-start">
        {/* ── Left column ─────────────────────────────────────────────── */}
        <motion.div
          custom={0}
          variants={prefersReduced ? undefined : reveal}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, margin: "-80px" }}
          className="flex flex-col gap-6"
        >
          <h2
            className={cn(
              "font-[family-name:var(--font-heading)] font-bold",
              "text-[clamp(1.75rem,4vw,3rem)]",
              "leading-tight tracking-[-0.025em] text-[#EBEBEB]"
            )}
          >
            Talking to a human helps.
          </h2>
          <p className="text-[rgba(235,235,235,0.62)] leading-relaxed max-w-md">
            If you're navigating the EU AI Act for the first time, a 20-minute
            conversation is worth more than an hour of reading. Book a call or
            drop us a line — no sales pitch, just answers.
          </p>

          <ul className="flex flex-col gap-4 mt-2 list-none" role="list">
            <li className="flex items-center gap-3">
              <Mail size={16} className="text-[#2A52BE] flex-shrink-0" aria-hidden />
              <a
                href="mailto:founders@regu.eu" // TODO: replace with real address
                className="text-sm text-[rgba(235,235,235,0.62)] hover:text-[#EBEBEB] transition-colors"
              >
                founders@regu.eu
              </a>
            </li>
            <li className="flex items-center gap-3">
              <MapPin size={16} className="text-[#2A52BE] flex-shrink-0" aria-hidden />
              <span className="text-sm text-[rgba(235,235,235,0.62)]">
                Built in the EU
              </span>
            </li>
            <li className="flex items-center gap-3">
              <Calendar size={16} className="text-[#2A52BE] flex-shrink-0" aria-hidden />
              <a
                href="https://cal.com/regu" // TODO: replace with real Cal link
                target="_blank"
                rel="noopener noreferrer"
                className="text-sm text-[rgba(235,235,235,0.62)] hover:text-[#EBEBEB] transition-colors"
              >
                Book a call
              </a>
            </li>
          </ul>
        </motion.div>

        {/* ── Right column: contact form ─────────────────────────────── */}
        <motion.div
          custom={1}
          variants={prefersReduced ? undefined : reveal}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, margin: "-80px" }}
        >
          <div
            className={cn(
              "rounded-2xl p-8",
              "bg-[#0A0F1F] border border-[rgba(235,235,235,0.08)]"
            )}
          >
            {submitted ? (
              <div
                className="flex flex-col items-center justify-center gap-4 py-12 text-center"
                role="status"
                aria-live="polite"
              >
                <div className="w-10 h-10 rounded-full bg-[rgba(48,164,108,0.15)] border border-[rgba(48,164,108,0.3)] flex items-center justify-center">
                  <span className="text-[#30A46C] text-lg" aria-hidden>✓</span>
                </div>
                <p className="text-[#EBEBEB] font-medium">
                  Thanks — we'll reply within one business day.
                </p>
                <button
                  onClick={() => setSubmitted(false)}
                  className="text-xs text-[rgba(235,235,235,0.38)] hover:text-[rgba(235,235,235,0.62)] transition-colors mt-2"
                >
                  Send another message
                </button>
              </div>
            ) : (
              <form
                onSubmit={handleSubmit}
                className="flex flex-col gap-5"
                noValidate
                aria-label="Contact form"
              >
                <Field
                  label="Name"
                  id="name"
                  type="text"
                  autoComplete="name"
                  required
                  placeholder="Ada Lovelace"
                  value={form.name}
                  onChange={handleChange}
                />
                <Field
                  label="Work email"
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  placeholder="ada@company.eu"
                  value={form.email}
                  onChange={handleChange}
                />
                <Field
                  label="Company"
                  id="company"
                  type="text"
                  autoComplete="organization"
                  placeholder="Acme AI GmbH"
                  value={form.company}
                  onChange={handleChange}
                />
                <TextAreaField
                  label="What are you building?"
                  id="message"
                  rows={4}
                  required
                  placeholder="We're building a CV screening tool for enterprise HR teams..."
                  value={form.message}
                  onChange={handleChange}
                />
                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  className="self-start mt-1"
                >
                  Send
                </Button>
              </form>
            )}
          </div>
        </motion.div>
      </div>
    </Section>
  );
}
