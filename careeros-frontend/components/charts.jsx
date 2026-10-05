"use client";

import { cn } from "cn";

/**
 * Lightweight dependency-free SVG charts in the CareerOS identity
 * (primary green + chart tones). All charts scale with `viewBox`,
 * render nothing when given no data, and expose text alternatives.
 */

function SkillDonut({ segments = [], size = 128, strokeWidth = 14, label, children, className }) {
  const total = segments.reduce((sum, s) => sum + (s.value ?? 0), 0);
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  return (
    <div
      className={cn("relative inline-flex items-center justify-center", className)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={label || "Skills distribution"}
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
        {total > 0 &&
          segments.map((segment, index) => {
            const fraction = (segment.value ?? 0) / total;
            const dash = fraction * circumference;
            const el = (
              <circle
                key={`${segment.label || index}-${index}`}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                strokeWidth={strokeWidth}
                strokeLinecap="butt"
                strokeDasharray={`${dash} ${circumference - dash}`}
                strokeDashoffset={-offset}
                className={segment.className || "stroke-primary"}
              >
                <title>{`${segment.label || "Segment"}: ${segment.value}`}</title>
              </circle>
            );
            offset += dash;
            return el;
          })}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        {children}
      </div>
    </div>
  );
}

function ScoreTrend({ points = [], className }) {
  const width = 420;
  const height = 170;
  const padLeft = 30;
  const padRight = 12;
  const padTop = 12;
  const padBottom = 24;
  const innerWidth = width - padLeft - padRight;
  const innerHeight = height - padTop - padBottom;

  const clean = points.filter((p) => p.value != null);
  const coords = clean.map((p, i) => ({
    ...p,
    x: clean.length === 1 ? padLeft + innerWidth / 2 : padLeft + (i / (clean.length - 1)) * innerWidth,
    y: padTop + (1 - Math.max(0, Math.min(100, p.value)) / 100) * innerHeight,
  }));
  const line = coords.map((c) => `${c.x},${c.y}`).join(" ");
  const area = coords.length > 0 ? `${padLeft},${padTop + innerHeight} ${line} ${coords[coords.length - 1].x},${padTop + innerHeight}` : "";

  return (
    <div className={className} role="img" aria-label={clean.length > 0 ? `Assessment scores over time: ${clean.map((p) => p.value).join(", ")}` : "No assessment scores yet"}>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full">
        {[0, 25, 50, 75, 100].map((tick) => {
          const y = padTop + (1 - tick / 100) * innerHeight;
          return (
            <g key={tick}>
              <line x1={padLeft} x2={width - padRight} y1={y} y2={y} className="stroke-border" strokeWidth={1} strokeDasharray={tick === 0 ? "" : "3 4"} />
              <text x={padLeft - 6} y={y + 3.5} textAnchor="end" fontSize={9} className="fill-muted-foreground">
                {tick}
              </text>
            </g>
          );
        })}
        {area && <polygon points={area} className="fill-primary/10" />}
        {coords.length > 1 && <polyline points={line} fill="none" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" className="stroke-primary" />}
        {coords.map((c, i) => (
          <g key={`${c.label || i}-${i}`}>
            <circle cx={c.x} cy={c.y} r={coords.length === 1 ? 5 : 4} className="fill-primary stroke-background" strokeWidth={2}>
              <title>{`${c.title || c.label || `Attempt ${i + 1}`}: ${c.value}%`}</title>
            </circle>
            {(coords.length <= 6 || i === 0 || i === coords.length - 1) && (
              <text x={c.x} y={height - 8} textAnchor="middle" fontSize={9} className="fill-muted-foreground">
                {c.label}
              </text>
            )}
          </g>
        ))}
      </svg>
    </div>
  );
}

function WeekActivity({ days = [], className }) {
  const max = Math.max(1, ...days.map((d) => d.count ?? 0));
  return (
    <div
      className={cn("flex h-24 items-end gap-1.5", className)}
      role="img"
      aria-label={days.length > 0 ? `Activity this week: ${days.map((d) => `${d.label} ${d.count}`).join(", ")}` : "No activity this week"}
    >
      {days.map((day) => {
        const pct = Math.max(0, Math.min(1, (day.count ?? 0) / max));
        return (
          <div key={day.label} className="flex min-w-0 flex-1 flex-col items-center gap-1" title={`${day.label}: ${day.count} activit${day.count === 1 ? "y" : "ies"}`}>
            <div className="flex h-16 w-full items-end rounded-md bg-muted/60">
              <div
                className={cn("w-full rounded-md transition-[height] duration-500", day.isToday ? "bg-primary" : "bg-primary/45")}
                style={{ height: `${Math.max((day.count ?? 0) > 0 ? 12 : 0, pct * 100)}%` }}
              />
            </div>
            <span className={cn("text-[10px] font-semibold", day.isToday ? "text-primary" : "text-muted-foreground")}>
              {day.label}
            </span>
          </div>
        );
      })}
    </div>
  );
}

export { SkillDonut, ScoreTrend, WeekActivity };
