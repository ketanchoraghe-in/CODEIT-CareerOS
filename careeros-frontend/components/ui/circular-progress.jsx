import { cn } from "cn";

const toneClasses = {
  primary: { track: "stroke-primary", text: "text-primary" },
  gold: { track: "stroke-chart-2", text: "text-chart-2" },
  green: { track: "stroke-chart-3", text: "text-chart-3" },
  muted: { track: "stroke-muted-foreground/50", text: "text-muted-foreground" },
};

function CircularProgress({
  value = 0,
  max = 100,
  size = 128,
  strokeWidth = 10,
  tone = "primary",
  label,
  sublabel,
  className,
  children,
}) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const percent = max > 0 ? Math.max(0, Math.min(max, value)) / max : 0;
  const dashOffset = circumference * (1 - percent);

  return (
    <div
      className={cn("relative inline-flex items-center justify-center", className)}
      style={{ width: size, height: size }}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      aria-label={label || "Progress"}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={strokeWidth}
          className="stroke-muted"
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={dashOffset}
          className={cn("transition-[stroke-dashoffset] duration-700 ease-out", toneClasses[tone].track)}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        {children || (
          <>
            <span className={cn("text-2xl font-bold tracking-tight", toneClasses[tone].text)}>{value}</span>
            {sublabel && <span className="text-xs font-medium text-muted-foreground">{sublabel}</span>}
          </>
        )}
      </div>
    </div>
  );
}

export { CircularProgress };