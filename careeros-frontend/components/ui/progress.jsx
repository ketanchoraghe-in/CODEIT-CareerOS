import { cn } from "cn";

const toneClasses = {
  primary: "bg-primary",
  gold: "bg-chart-2",
  green: "bg-chart-3",
  muted: "bg-muted-foreground/60",
};

function Progress({
  value = 0,
  max = 100,
  tone = "primary",
  className,
  indicatorClassName,
  ...props
}) {
  const normalized = Math.max(0, Math.min(max, value));
  const percent = max > 0 ? Math.round((normalized / max) * 100) : 0;

  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={normalized}
      aria-label={props["aria-label"] || "Progress"}
      className={cn("h-2 w-full overflow-hidden rounded-full bg-muted", className)}
      {...props}
    >
      <div
        className={cn("h-full rounded-full transition-all duration-500 ease-out", toneClasses[tone], indicatorClassName)}
        style={{ width: `${percent}%` }}
      />
    </div>
  );
}

export { Progress };