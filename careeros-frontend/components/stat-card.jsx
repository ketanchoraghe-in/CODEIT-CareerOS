import { ArrowUpRight, Lock } from "lucide-react";
import { cn } from "cn";
import { Skeleton } from "@/components/ui/skeleton";

const toneClasses = {
  primary: { icon: "bg-primary/10 text-primary", value: "text-foreground" },
  gold: { icon: "bg-chart-2/15 text-chart-2", value: "text-foreground" },
  green: { icon: "bg-chart-3/15 text-chart-3", value: "text-foreground" },
  muted: { icon: "bg-muted text-muted-foreground", value: "text-muted-foreground" },
};

function StatCard({
  icon: Icon,
  label,
  value,
  sub,
  href,
  tone = "primary",
  loading = false,
  locked = false,
  lockLabel,
  className,
}) {
  const content = (
    <div
      className={cn(
        "flex h-full flex-col gap-3 rounded-xl border p-5 transition",
        locked
          ? "border-dashed border-border/80 bg-card/50"
          : "border-border/60 bg-card shadow-card hover:shadow-card-hover",
        className,
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <div
          className={cn(
            "flex size-10 items-center justify-center rounded-lg",
            locked ? "bg-muted text-muted-foreground" : toneClasses[tone].icon,
          )}
        >
          <Icon className="size-5" aria-hidden="true" />
        </div>
        {locked ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-muted-foreground">
            <Lock className="size-3" aria-hidden="true" />
            {lockLabel || "later sprint"}
          </span>
        ) : (
          href && <ArrowUpRight className="size-4 text-muted-foreground" aria-hidden="true" />
        )}
      </div>
      {loading ? (
        <div className="space-y-2">
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-3 w-24" />
        </div>
      ) : (
        <div>
          <p
            className={cn(
              "text-2xl font-bold tracking-tight tnum",
              locked ? "text-muted-foreground/50" : toneClasses[tone].value,
            )}
          >
            {value ?? "—"}
          </p>
          <p className="mt-0.5 text-sm font-medium text-muted-foreground">{label}</p>
          {sub && <p className="mt-1.5 text-xs text-muted-foreground">{sub}</p>}
        </div>
      )}
    </div>
  );

  if (href) {
    return (
      <a href={href} className="group block h-full">
        {content}
      </a>
    );
  }
  return content;
}

export { StatCard };