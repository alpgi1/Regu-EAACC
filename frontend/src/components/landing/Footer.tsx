import { Link } from "react-router-dom";
import { scrollToSection } from "@/lib/lenis";

type FooterLink =
  | { label: string; type: "anchor"; id: string }
  | { label: string; type: "external"; href: string }
  | { label: string; type: "route"; to: string };

const COLUMNS: { title: string; links: FooterLink[] }[] = [
  {
    title: "Product",
    links: [
      { label: "Methodology", type: "anchor", id: "methodology" },
      { label: "Platform", type: "anchor", id: "platform" },
      { label: "Pricing", type: "route", to: "/coming-soon" },
      { label: "Changelog", type: "route", to: "/coming-soon" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "About", type: "route", to: "/coming-soon" },
      { label: "Careers", type: "route", to: "/coming-soon" },
      { label: "Press", type: "route", to: "/coming-soon" },
      { label: "Contact", type: "anchor", id: "contact" },
    ],
  },
  {
    title: "Resources",
    links: [
      {
        label: "EU AI Act (full text)",
        type: "external",
        href: "https://eur-lex.europa.eu/eli/reg/2024/1689/oj",
      },
      { label: "Annex IV guide", type: "route", to: "/coming-soon" },
      { label: "FAQ", type: "route", to: "/coming-soon" },
      {
        label: "GitHub",
        type: "external",
        href: "https://github.com/alpgi1/Regu-EAACC",
      },
    ],
  },
  {
    title: "Legal",
    links: [
      { label: "Privacy", type: "route", to: "/coming-soon" },
      { label: "Terms", type: "route", to: "/coming-soon" },
      { label: "Cookies", type: "route", to: "/coming-soon" },
      { label: "DPA", type: "route", to: "/coming-soon" },
    ],
  },
];

function Item({ link }: { link: FooterLink }) {
  const className =
    "text-[14px] text-[var(--ink-secondary)] hover:text-[var(--ink-primary)] transition-colors duration-[var(--dur-fast)]";

  if (link.type === "anchor") {
    return (
      <a
        href={`#${link.id}`}
        onClick={(e) => {
          e.preventDefault();
          scrollToSection(link.id);
        }}
        className={className}
      >
        {link.label}
      </a>
    );
  }
  if (link.type === "external") {
    return (
      <a
        href={link.href}
        target="_blank"
        rel="noopener noreferrer"
        className={className}
      >
        {link.label}
      </a>
    );
  }
  return (
    <Link to={link.to} className={className}>
      {link.label}
    </Link>
  );
}

export default function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="bg-[var(--bg-base)] border-t border-[var(--hairline)] pt-20 pb-12">
      <div className="max-w-[1200px] mx-auto px-6 md:px-10">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-10 md:gap-12">
          <div className="col-span-2 md:col-span-1">
            <Link
              to="/"
              className="inline-flex items-center gap-2 text-[var(--ink-primary)]"
            >
              <span style={{ color: "var(--brand-500)" }}>
                <svg
                  width={18}
                  height={18}
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden
                >
                  <path d="M12 2 4 5v6c0 5 3.5 9.5 8 11 4.5-1.5 8-6 8-11V5l-8-3Z" />
                  <path d="m9 12 2 2 4-4" />
                </svg>
              </span>
              <span className="font-semibold tracking-tight text-[15px]">
                REGU
              </span>
            </Link>
            <p
              className="mt-4 text-[var(--ink-tertiary)] max-w-[28ch]"
              style={{ fontSize: "var(--text-caption)", lineHeight: "1.6" }}
            >
              First-pass EU AI Act compliance, grounded in the regulation.
            </p>
          </div>

          {COLUMNS.map((col) => (
            <div key={col.title} className="flex flex-col gap-3">
              <h3
                className="text-[var(--ink-primary)] font-medium uppercase tracking-[0.08em]"
                style={{ fontSize: "var(--text-micro)" }}
              >
                {col.title}
              </h3>
              <ul className="flex flex-col gap-2.5">
                {col.links.map((l) => (
                  <li key={l.label}>
                    <Item link={l} />
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-16 pt-8 border-t border-[var(--hairline)] flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <p
            className="text-[var(--ink-tertiary)]"
            style={{ fontSize: "var(--text-caption)" }}
          >
            © {year} REGU. Not legal advice.
          </p>
          <Link
            to="/coming-soon"
            className="text-[var(--ink-tertiary)] hover:text-[var(--ink-primary)] transition-colors duration-[var(--dur-fast)]"
            style={{ fontSize: "var(--text-caption)" }}
          >
            Privacy Policy
          </Link>
        </div>
      </div>
    </footer>
  );
}
