import { forwardRef } from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  // Base — shared across all variants
  [
    "inline-flex items-center justify-center gap-2",
    "font-medium rounded-lg",
    "transition-all duration-150",
    "cursor-pointer select-none",
    "focus-visible:outline-2 focus-visible:outline-offset-2",
    "disabled:pointer-events-none disabled:opacity-40",
  ],
  {
    variants: {
      variant: {
        primary: [
          "bg-[#2A52BE] text-[#EBEBEB]",
          "shadow-[0_0_40px_rgba(42,82,190,0.35)]",
          "hover:bg-[#4A6FE5] hover:shadow-[0_0_56px_rgba(74,111,229,0.5)] hover:-translate-y-px",
          "active:translate-y-0 active:shadow-[0_0_24px_rgba(42,82,190,0.3)]",
          "focus-visible:outline-[#2A52BE]",
        ],
        ghost: [
          "bg-transparent text-[#EBEBEB]",
          "hover:bg-[rgba(235,235,235,0.06)]",
          "focus-visible:outline-[#2A52BE]",
        ],
        outline: [
          "bg-transparent text-[#EBEBEB]",
          "border border-[rgba(235,235,235,0.16)]",
          "hover:bg-[rgba(235,235,235,0.06)] hover:border-[rgba(235,235,235,0.24)]",
          "focus-visible:outline-[#2A52BE]",
        ],
      },
      size: {
        sm: "h-8 px-3 text-sm",
        md: "h-10 px-5 text-sm",
        lg: "h-12 px-7 text-base",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  /** Render as a child component (e.g. wrap a <Link> without nested button/anchor) */
  asChild?: boolean;
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        ref={ref}
        className={cn(buttonVariants({ variant, size }), className)}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
