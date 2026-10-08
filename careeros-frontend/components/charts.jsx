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
      {days.map((day, index) => {
        const pct = Math.max(0, Math.min(1, (day.count ?? 0) / max));
        return (
          // Index key: narrow weekday labels repeat ("T"ue/"T"hu, "S"at/"S"un),
          // so the label is not unique — the 7-slot order is stable instead.
          <div key={`${day.label}-${index}`} className="flex min-w-0 flex-1 flex-col items-center gap-1" title={`${day.label}: ${day.count} activit${day.count === 1 ? "y" : "ies"}`}>
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

/**
 * Skill radar: your current score vs the required target per skill.
 * Solid green area = you, dashed gold outline = target. Unassessed skills
 * sit at the center with a hollow dot. Pure SVG, scales with `viewBox`.
 */
function SkillRadar({ skills = [], className }) {
  const width = 400;
  const height = 360;
  const cx = width / 2;
  const cy = height / 2 + 6;
  const radius = 118;

  const clean = skills
    .filter((s) => s && s.name)
    .map((s) => ({
      name: s.name,
      score: typeof s.score === "number" ? Math.max(0, Math.min(100, s.score)) : 0,
      assessed: Boolean(s.assessed),
      target: typeof s.target === "number" ? Math.max(0, Math.min(100, s.target)) : 100,
    }));
  const n = clean.length;
  if (n < 3) return null;

  const angle = (i) => (2 * Math.PI * i) / n - Math.PI / 2;
  const point = (i, value) => {
    const r = (Math.max(0, Math.min(100, value)) / 100) * radius;
    return [cx + r * Math.cos(angle(i)), cy + r * Math.sin(angle(i))];
  };
  const poly = (getValue) => clean.map((s, i) => point(i, getValue(s)).join(",")).join(" ");
  const short = (name) => (name.length > 16 ? `${name.slice(0, 15)}…` : name);

  return (
    <div
      className={className}
      role="img"
      aria-label={`Skill radar: ${clean.map((s) => `${s.name} ${s.assessed ? s.score : "not assessed"} of target ${s.target}`).join("; ")}`}
    >
      <svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full">
        <defs>
          <radialGradient id="skillRadarGlow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.14} />
            <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0} />
          </radialGradient>
          <linearGradient id="skillRadarFill" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.32} />
            <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0.1} />
          </linearGradient>
        </defs>
        <circle cx={cx} cy={cy} r={radius} fill="url(#skillRadarGlow)" />
        {/* grid rings */}
        {[25, 50, 75, 100].map((tick) => (
          <g key={tick}>
            <circle
              cx={cx}
              cy={cy}
              r={(tick / 100) * radius}
              fill="none"
              strokeWidth={1}
              strokeDasharray={tick === 100 ? "" : "3 4"}
              className="stroke-border"
              opacity={tick === 100 ? 1 : 0.8}
            />
            <text x={cx + 4} y={cy - (tick / 100) * radius + 3} fontSize={8.5} className="fill-muted-foreground">
              {tick}
            </text>
          </g>
        ))}
        {/* spokes + axis labels */}
        {clean.map((s, i) => {
          const [ex, ey] = point(i, 100);
          const labelR = radius + 24;
          const lx = cx + labelR * Math.cos(angle(i));
          const ly = cy + labelR * Math.sin(angle(i));
          return (
            <g key={`spoke-${i}`}>
              <line x1={cx} y1={cy} x2={ex} y2={ey} strokeWidth={1} className="stroke-border" opacity={0.7} />
              <text
                x={Math.max(34, Math.min(width - 34, lx))}
                y={ly}
                textAnchor="middle"
                dominantBaseline="middle"
                fontSize={10.5}
                fontWeight={700}
                className="fill-foreground"
              >
                {short(s.name)}
              </text>
            </g>
          );
        })}
        {/* target outline */}
        <polygon points={poly((s) => s.target)} fill="none" strokeWidth={2} strokeDasharray="7 5" strokeLinecap="round" className="stroke-amber-500" opacity={0.9}>
          <title>{`Target: ${clean.map((s) => `${s.name} ${s.target}%`).join(", ")}`}</title>
        </polygon>
        {/* your area */}
        <polygon points={poly((s) => s.score)} fill="url(#skillRadarFill)" strokeWidth={2.5} strokeLinejoin="round" className="stroke-primary">
          <title>{`Your scores: ${clean.map((s) => `${s.name} ${s.assessed ? `${s.score}%` : "not assessed"}`).join(", ")}`}</title>
        </polygon>
        {/* vertices */}
        {clean.map((s, i) => {
          const [sx, sy] = point(i, s.score);
          const [tx, ty] = point(i, s.target);
          return (
            <g key={`v-${i}`}>
              <circle cx={tx} cy={ty} r={2.5} className="fill-background stroke-amber-500" strokeWidth={1.5}>
                <title>{`${s.name} target: ${s.target}%`}</title>
              </circle>
              {s.assessed ? (
                <circle cx={sx} cy={sy} r={4} className="fill-primary stroke-background" strokeWidth={2}>
                  <title>{`${s.name}: you ${s.score}% · target ${s.target}%`}</title>
                </circle>
              ) : (
                <circle cx={sx} cy={sy} r={4} className="fill-background stroke-muted-foreground" strokeWidth={1.5} strokeDasharray="2 2">
                  <title>{`${s.name}: not assessed yet · target ${s.target}%`}</title>
                </circle>
              )}
            </g>
          );
        })}
      </svg>
      {/* legend */}
      <div className="mt-1 flex flex-wrap items-center justify-center gap-4 text-xs font-semibold">
        <span className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="inline-block h-2.5 w-5 rounded-full bg-primary" />
          Your score
        </span>
        <span className="inline-flex items-center gap-1.5 text-muted-foreground">
          <span aria-hidden="true" className="inline-block h-0 w-5 border-t-2 border-dashed border-amber-500" />
          Target
        </span>
      </div>
    </div>
  );
}

export { SkillDonut, ScoreTrend, WeekActivity, SkillRadar };
