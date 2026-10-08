"use client";

import { useMemo, useState } from "react";
import { CheckCircle2, CircleDashed, PieChart } from "lucide-react";
import { cn } from "cn";
import { prettifyEnum } from "@/lib/format";

/**
 * Professional per-skill circle chart.
 * Every skill gets its own slice + color. Hovering a slice or
 * legend row spotlights it and updates the center readout.
 *
 * Honesty rules (no fabricated numbers):
 * - The ring is purely categorical: one slice per skill. Slice sizes never
 *   pretend to be scores.
 * - The big center number is the REAL coverage (alignment%) when a target
 *   career exists, or the detected-skill count otherwise.
 * - Legend rows show REAL per-skill facts only: tested score vs target,
 *   "on CV but not tested", or "missing (W% of role)". Bars render only for
 *   real tested scores.
 */

/* Vivid but harmonious 12-tone professional palette */
const SKILL_PALETTE = [
  "#10b981", // emerald
  "#0ea5e9", // sky
  "#8b5cf6", // violet
  "#f59e0b", // amber
  "#ec4899", // pink
  "#14b8a6", // teal
  "#6366f1", // indigo
  "#f43f5e", // rose
  "#84cc16", // lime
  "#06b6d4", // cyan
  "#f97316", // orange
  "#a855f7", // purple
];

const OTHER_COLOR = "#94a3b8"; // slate for "+N more"

function polar(cx, cy, r, angleDeg) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)];
}

function SkillDonut({ skills = [], totalWeight = 1, size = 216, active, onActive, center }) {
  const cx = size / 2;
  const cy = size / 2;
  const outer = size / 2 - 10;
  const inner = outer - 30;
  const mid = (outer + inner) / 2;

  const gapDeg = skills.length > 1 ? 2.5 : 0;
  // Precompute slice angles immutably (no variable reassignment during render).
  const slices = useMemo(() => {
    const sweeps = skills.map((s) => (totalWeight > 0 ? 360 * ((s.weight ?? 1) / totalWeight) : 0));
    const ends = sweeps.reduce(
      (acc, sweep) => [...acc, (acc.length > 0 ? acc[acc.length - 1] : 0) + sweep],
      [],
    );
    return skills.map((skill, i) => {
      const origin = i === 0 ? 0 : ends[i - 1];
      return { skill, sweep: sweeps[i], start: origin + gapDeg / 2, end: ends[i] - gapDeg / 2 };
    });
  }, [skills, totalWeight, gapDeg]);

  const activeSkill = active ? skills.find((s) => s.key === active) : null;

  return (
    <div className="relative inline-flex shrink-0 items-center justify-center" style={{ width: size, height: size }}>
      {/* glow + soft shadow */}
      <div aria-hidden="true" className="absolute rounded-full bg-[radial-gradient(circle_at_50%_32%,var(--color-primary)_0%,transparent_60%)] opacity-10" style={{ inset: 6 }} />
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="drop-shadow-[0_10px_24px_rgb(0_0_0/0.12)]">
        {/* track */}
        <circle cx={cx} cy={cy} r={mid} fill="none" strokeWidth={outer - inner} className="stroke-muted" opacity={0.55} />
        {slices.map(({ skill, start, end }) => {
          const large = end - start > 180 ? 1 : 0;
          const [x1, y1] = polar(cx, cy, mid, start);
          const [x2, y2] = polar(cx, cy, mid, end);
          // pull the slice toward the viewer on hover
          const [mx, my] = polar(cx, cy, mid, (start + end) / 2);
          const isActive = active === skill.key;
          const dx = isActive ? (mx - cx) * 0.06 : 0;
          const dy = isActive ? (my - cy) * 0.06 : 0;
          const baseOpacity = skill.dimmed ? 0.4 : 1;
          const tip = `${skill.name} · ${skill.centerLine}`;
          return (
            <g key={skill.key} transform={`translate(${dx} ${dy})`} style={{ transition: "transform .25s ease, opacity .25s ease" }} opacity={active && !isActive ? 0.3 : baseOpacity}>
              {isActive && (
                <path
                  d={`M ${x1} ${y1} A ${mid} ${mid} 0 ${large} 1 ${x2} ${y2}`}
                  fill="none"
                  stroke={skill.color}
                  strokeWidth={outer - inner + 8}
                  strokeLinecap="round"
                  opacity={0.22}
                />
              )}
              <path
                d={`M ${x1} ${y1} A ${mid} ${mid} 0 ${large} 1 ${x2} ${y2}`}
                fill="none"
                stroke={skill.color}
                strokeWidth={isActive ? outer - inner + 2 : outer - inner}
                strokeLinecap="round"
                className="cursor-pointer"
                style={{ transition: "stroke-width .25s ease, filter .25s ease", filter: isActive ? `drop-shadow(0 0 6px ${skill.color}66)` : "none" }}
                onMouseEnter={() => onActive?.(skill.key)}
                onMouseLeave={() => onActive?.(null)}
                onFocus={() => onActive?.(skill.key)}
                onBlur={() => onActive?.(null)}
                tabIndex={0}
                role="img"
                aria-label={tip}
              >
                <title>{tip}</title>
              </path>
            </g>
          );
        })}
      </svg>
      {/* center card */}
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center px-8 text-center">
        {activeSkill ? (
          <>
            <span className="tnum max-w-full truncate text-xl font-extrabold leading-tight tracking-tight text-foreground">{activeSkill.name}</span>
            <span className="tnum mt-1 text-[26px] font-extrabold leading-none" style={{ color: activeSkill.valueColor || "var(--color-primary)" }}>
              {activeSkill.valueText}
            </span>
            <span className="mt-1.5 inline-flex max-w-full items-center gap-1 truncate rounded-full bg-secondary px-2 py-0.5 text-[10px] font-bold text-secondary-foreground">
              {activeSkill.centerLine}
            </span>
          </>
        ) : (
          <>
            <span className="tnum max-w-full truncate text-xl font-extrabold leading-tight tracking-tight text-foreground">{center.title}</span>
            <span className="tnum mt-1 text-[26px] font-extrabold leading-none" style={{ color: center.color || "var(--color-primary)" }}>
              {center.value}
            </span>
            <span className="mt-1.5 inline-flex max-w-full items-center gap-1 truncate rounded-full bg-secondary px-2 py-0.5 text-[10px] font-bold text-secondary-foreground">
              {center.caption}
            </span>
          </>
        )}
      </div>
    </div>
  );
}

function SkillLegendRow({ skill, active, onActive }) {
  return (
    <li
      onMouseEnter={() => onActive?.(skill.key)}
      onMouseLeave={() => onActive?.(null)}
      className={cn(
        "group cursor-default rounded-xl border bg-background/70 px-3 py-2 transition-all duration-200",
        active === skill.key
          ? "border-transparent shadow-[0_8px_20px_rgb(0_0_0/0.10)]"
          : "border-border/50 hover:-translate-y-px hover:shadow-[0_8px_20px_rgb(0_0_0/0.08)]",
      )}
      style={active === skill.key ? { boxShadow: `0 8px 22px ${skill.color}33`, borderColor: `${skill.color}55` } : undefined}
    >
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="inline-flex min-w-0 items-center gap-2">
          <span
            aria-hidden="true"
            className="size-3.5 shrink-0 rounded-[5px] ring-1 ring-black/10 transition-transform duration-200 group-hover:scale-110"
            style={{ background: `linear-gradient(135deg, ${skill.color}, ${skill.color}CC)`, boxShadow: `0 2px 8px ${skill.color}55` }}
          />
          <span className="truncate font-bold tracking-tight">{skill.name}</span>
          {skill.status === "matched" ? (
            <CheckCircle2 className="size-3.5 shrink-0 text-emerald-600" aria-label="Matched" />
          ) : skill.status === "missing" ? (
            <CircleDashed className="size-3.5 shrink-0 text-amber-500" aria-label="Missing" />
          ) : null}
        </span>
        {skill.valueText && (
          <span className="tnum shrink-0 text-sm font-extrabold" style={{ color: skill.valueColor || skill.color }}>{skill.valueText}</span>
        )}
      </div>
      <p className="mt-0.5 truncate text-[11px] font-medium text-muted-foreground">{skill.meta}</p>
      {typeof skill.barPercent === "number" && (
        <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted/80" role="img" aria-label={`${skill.name} tested score ${skill.barPercent}%`}>
          <div
            className="h-full rounded-full transition-[width] duration-500"
            style={{ width: `${Math.min(100, Math.max(skill.barPercent, 0))}%`, background: `linear-gradient(90deg, ${skill.color}CC, ${skill.color})` }}
          />
        </div>
      )}
    </li>
  );
}

export function CvSkillsChart({ detected = [], matched = [], missing = [], hasTarget = false, careerName = "", alignment = 0, className }) {
  const [active, setActive] = useState(null);

  const skills = useMemo(() => {
    // With target: every framework skill = own slice (matched solid, missing dimmed).
    // Values are REAL backend facts: tested score vs target, or role weight.
    if (Boolean(hasTarget) && matched.length + missing.length > 0) {
      const out = [];
      matched.forEach((s, i) => {
        const assessed = Boolean(s.assessed) && typeof s.scorePercent === "number";
        const score = assessed ? s.scorePercent : null;
        const target = typeof s.targetPercent === "number" ? s.targetPercent : null;
        out.push({
          key: `fw-matched-${s.skillName}-${i}`,
          name: s.skillName,
          status: "matched",
          color: SKILL_PALETTE[out.length % SKILL_PALETTE.length],
          dimmed: false,
          weight: 1,
          meta: s.skillCategory
            ? `${prettifyEnum(s.skillCategory)} · ${assessed ? `tested ${score}% · target ${target ?? "—"}%` : "on your CV · not tested yet"}`
            : assessed ? `Tested ${score}% · target ${target ?? "—"}%` : "On your CV · not tested yet",
          valueText: assessed ? `${score}%` : "On CV",
          valueColor: undefined,
          barPercent: assessed ? score : null,
          centerLine: assessed ? `Tested ${score}% · target ${target ?? "—"}%` : "On your CV · not tested yet",
        });
      });
      missing.forEach((s, i) => {
        const weight = typeof s.weightPercent === "number" ? s.weightPercent : null;
        out.push({
          key: `fw-missing-${s.skillName}-${i}`,
          name: s.skillName,
          status: "missing",
          color: SKILL_PALETTE[out.length % SKILL_PALETTE.length],
          dimmed: true,
          weight: 1,
          meta: s.skillCategory
            ? `${prettifyEnum(s.skillCategory)} · ${weight !== null ? `${weight}% of role` : "not on CV"}`
            : weight !== null ? `${weight}% of role · not on CV` : "Not on your CV",
          valueText: "Missing",
          valueColor: "#d97706",
          barPercent: null,
          centerLine: weight !== null ? `Missing · ${weight}% of role` : "Missing from your CV",
        });
      });
      return out;
    }
    // No target: every detected skill = own slice, grouped order by category.
    // No scores exist here, so rows show identity only — never fake percents.
    const sorted = [...detected].sort((a, b) => String(a.skillCategory || "").localeCompare(String(b.skillCategory || "")));
    const total = sorted.length;
    const MAX_SLICES = 11;
    const head = sorted.slice(0, MAX_SLICES);
    const tail = sorted.slice(MAX_SLICES);
    const out = head.map((s, i) => ({
      key: `det-${s.skillId ?? s.skillName}-${i}`,
      name: s.skillName,
      meta: prettifyEnum(s.skillCategory) || "Detected on CV",
      status: "detected",
      color: SKILL_PALETTE[i % SKILL_PALETTE.length],
      dimmed: false,
      weight: 1,
      valueText: null,
      valueColor: undefined,
      barPercent: null,
      centerLine: prettifyEnum(s.skillCategory) || "Detected on your CV",
    }));
    if (tail.length > 0) {
      out.push({
        key: "__other__",
        name: `+${tail.length} more`,
        meta: tail.slice(0, 3).map((s) => s.skillName).join(", "),
        status: "detected",
        color: OTHER_COLOR,
        dimmed: false,
        weight: tail.length,
        valueText: null,
        valueColor: undefined,
        barPercent: null,
        centerLine: `${tail.length} more detected skills`,
      });
    }
    return out;
  }, [detected, matched, missing, hasTarget]);

  if (skills.length === 0) return null;

  // Cap ring at 12 colorful slices; fold the rest into "+N more"
  const MAX_RING = 12;
  const ring = skills.slice(0, MAX_RING);
  const overflow = skills.length - ring.length;
  const totalWeight = ring.reduce((sum, s) => sum + (s.weight ?? 1), 0);

  const matchedCount = matched.length;
  const frameworkTotal = matchedCount + missing.length;
  const hasCoverage = Boolean(hasTarget) && frameworkTotal > 0;
  const heading = hasCoverage ? "Skills coverage" : "Detected skills";
  const sub = hasCoverage
    ? `${matchedCount} of ${frameworkTotal}${careerName ? ` ${careerName}` : ""} skills on your CV · ${alignment}% aligned`
    : `${detected.length} ${detected.length === 1 ? "skill" : "skills"} detected · one slice each`;
  const center = hasCoverage
    ? {
        title: `${matchedCount} of ${frameworkTotal} skills`,
        value: `${alignment}%`,
        caption: careerName ? `of ${careerName} on your CV` : "of target role on your CV",
        color: undefined,
      }
    : {
        title: `${detected.length} ${detected.length === 1 ? "skill" : "skills"}`,
        value: "CV",
        caption: "detected on your CV",
        color: undefined,
      };
  const caption = hasCoverage
    ? "One slice per role skill · solid = on your CV · dimmed = missing"
    : "One slice per detected skill";

  return (
    <div className={cn("overflow-hidden rounded-2xl border border-border/60 bg-gradient-to-br from-primary/[0.08] via-card to-card shadow-[0_2px_16px_rgb(0_0_0/0.05)]", className)}>
      {/* header */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/50 px-4 pb-3 pt-4 sm:px-5">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-[0_4px_12px_var(--color-primary)/40]">
            <PieChart className="size-4" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <p className="text-sm font-extrabold tracking-tight">{heading}</p>
            <p className="truncate text-xs text-muted-foreground">{sub}</p>
          </div>
        </div>
        {hasCoverage ? (
          <span className="tnum inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-extrabold text-emerald-700 dark:text-emerald-400">
            {alignment}% aligned
          </span>
        ) : (
          <span className="inline-flex items-center rounded-full bg-secondary px-2.5 py-1 text-xs font-bold text-secondary-foreground">
            {detected.length} detected
          </span>
        )}
      </div>
      <div className="flex flex-col items-center gap-5 p-4 sm:p-5 xl:flex-row xl:items-center">
        <div className="flex shrink-0 flex-col items-center gap-2" onMouseLeave={() => setActive(null)}>
          <SkillDonut skills={ring} totalWeight={totalWeight} size={216} active={active} onActive={setActive} center={center} />
          <div className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-[0.14em] text-muted-foreground">
            <span aria-hidden="true" className="inline-block size-1.5 rounded-full bg-primary" />
            {caption}
          </div>
        </div>
        <ul className={cn("grid w-full min-w-0 flex-1 gap-2", ring.length > 4 ? "sm:grid-cols-2" : "grid-cols-1")}>
          {ring.map((skill) => (
            <SkillLegendRow key={skill.key} skill={skill} active={active} onActive={setActive} />
          ))}
        </ul>
      </div>
      {overflow > 0 && (
        <p className="border-t border-border/50 px-4 py-2.5 text-[11px] leading-relaxed text-muted-foreground sm:px-5">
          +{overflow} more {overflow === 1 ? "skill" : "skills"} beyond the {ring.length} colored slices — see the badges below.
        </p>
      )}
    </div>
  );
}
