import Image from "next/image";
import { cn } from "cn";

export function Logo({ className = "h-8 w-8" }) {
  return (
    <Image
      src="/images/codeit-logo.png"
      alt=""
      width={1254}
      height={1254}
      aria-hidden="true"
      className={cn("rounded-lg object-contain", className)}
    />
  );
}

function LogoWordmark({ className, size = "text-lg", tone = "dark" }) {
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <Logo className="h-7 w-7" />
      <span
        className={cn(
          size,
          "font-bold leading-none tracking-tight",
          tone === "light" ? "text-white" : "text-foreground",
        )}
      >
        CODEIT&nbsp;<span className="text-primary">CareerOS</span>
      </span>
    </span>
  );
}

export { LogoWordmark };