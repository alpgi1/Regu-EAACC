/**
 * Logo - the REGU brand mark, used across Nav, Footer, and Hero.
 * Source: /public/regu_logo.png (1024×1024 PNG with transparency).
 */
export default function Logo({
  size = 22,
  className,
}: {
  size?: number;
  className?: string;
}) {
  return (
    <img
      src="/regu_logo.png"
      alt="REGU"
      width={size}
      height={size}
      className={className}
      style={{
        width: size,
        height: size,
        display: "block",
        objectFit: "contain",
      }}
    />
  );
}
