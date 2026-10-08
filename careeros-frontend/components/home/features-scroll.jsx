"use client";

import {
  BarChart3,
  Check,
  CircleCheck,
  ClipboardCheck,
  Download,
  FolderKanban,
  FolderOpen,
  Rocket,
  Send,
  Sparkles,
  Timer,
  TrendingUp,
} from "lucide-react";
import { cn } from "cn";

/* ------------------------------------------------------------------ */
/*  Shared bits                                                        */
/* ------------------------------------------------------------------ */

function VisualShell({ children }) {
  return (
    <div className="relative overflow-hidden border-t border-border/60 bg-gradient-to-b from-brand-500/[0.07] to-transparent px-5 pt-5 pb-6">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-60"
        style={{
          backgroundImage: "radial-gradient(oklch(0.6 0.17 148 / 0.16) 1px, transparent 1px)",
          backgroundSize: "14px 14px",
          maskImage: "radial-gradient(16rem 10rem at 50% 40%, black 30%, transparent 75%)",
          WebkitMaskImage: "radial-gradient(16rem 10rem at 50% 40%, black 30%, transparent 75%)",
        }}
      />
      <div className="relative mx-auto w-full max-w-[300px] transition-transform duration-500 group-hover:-translate-y-1">
        {children}
      </div>
    </div>
  );
}

function MockCard({ className, children }) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "rounded-xl border border-border/70 bg-white p-3.5 shadow-[0_8px_24px_-12px_oklch(0.4_0.12_150/0.35)]",
        className,
      )}
    >
      {children}
    </div>
  );
}

function SkillBar({ name, value, highlight }) {
  return (
    <div>
      <div className="flex items-center justify-between text-[10px] font-bold">
        <span className="text-slate-700">{name}</span>
        <span className={highlight ? "text-amber-600" : "text-brand-700"}>{value}%</span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
        <div
          className={cn(
            "h-full rounded-full",
            highlight
              ? "bg-gradient-to-r from-amber-500 to-orange-400"
              : "bg-gradient-to-r from-brand-700 via-brand-500 to-brand-400",
          )}
          style={{ width: `${value}%` }}
        />
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Six product-style visuals (pure UI, zero stock imagery)            */
/* ------------------------------------------------------------------ */

function AssessVisual() {
  return (
    <MockCard>
      <div className="flex items-center justify-between">
        <span className="rounded-md bg-brand-600/10 px-2 py-0.5 text-[9px] font-black tracking-wider text-brand-700 uppercase">
          Q 12 / 30
        </span>
        <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-0.5 text-[9px] font-bold text-slate-600">
          <Timer className="size-3" /> 04:32
        </span>
      </div>
      <p className="mt-2.5 text-[11px] leading-snug font-bold text-slate-800">
        Which OOP pillar binds data + methods together?
      </p>
      <div className="mt-2.5 space-y-1.5">
        <div className="flex items-center gap-2 rounded-lg border border-slate-200 px-2.5 py-1.5 text-[10px] font-medium text-slate-500">
          <span className="size-3 shrink-0 rounded-full border border-slate-300" />
          Inheritance
        </div>
        <div className="flex items-center gap-2 rounded-lg border border-brand-500 bg-brand-500/[0.06] px-2.5 py-1.5 text-[10px] font-bold text-slate-800">
          <span className="flex size-3 shrink-0 items-center justify-center rounded-full border border-brand-600">
            <span className="size-1.5 rounded-full bg-brand-600" />
          </span>
          Encapsulation
          <Check className="ml-auto size-3 text-brand-600" />
        </div>
      </div>
      <div className="mt-2.5 flex items-center gap-2">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-100">
          <div className="h-full w-2/5 rounded-full bg-gradient-to-r from-brand-700 to-brand-400" />
        </div>
        <span className="rounded-full bg-pine-950 px-2.5 py-1 text-[9px] font-bold text-white">Next</span>
      </div>
    </MockCard>
  );
}

function GapsVisual() {
  return (
    <div className="relative">
      <MockCard className="space-y-2.5">
        <SkillBar name="Problem Solving" value={86} />
        <SkillBar name="Java" value={72} />
        <SkillBar name="System Design" value={38} highlight />
        <p className="inline-flex items-center gap-1 rounded-md bg-amber-500/10 px-2 py-1 text-[9px] font-bold text-amber-700">
          Biggest gap: System Design (−34%)
        </p>
      </MockCard>
      <div className="anim-float-soft absolute -top-3 -right-2 flex items-center gap-1.5 rounded-xl border border-border/70 bg-white px-2.5 py-1.5 text-[9px] font-black text-brand-700 shadow-popover">
        <TrendingUp className="size-3.5" /> +12% readiness
      </div>
    </div>
  );
}

function AiVisual() {
  return (
    <MockCard className="space-y-2">
      <div className="ml-auto w-fit max-w-[88%] rounded-2xl rounded-br-md bg-primary px-3 py-2 text-[10px] leading-snug font-medium text-white">
        Which skill next for a Java Dev role?
      </div>
      <div className="w-fit max-w-[92%] rounded-2xl rounded-bl-md border border-border/60 bg-slate-50 px-3 py-2 text-[10px] leading-snug text-slate-700 shadow-sm">
        <span className="font-bold text-brand-700">Focus first:</span> Spring Boot, then REST
        APIs — your largest gaps.
      </div>
      <div className="flex w-fit items-center gap-1 rounded-2xl rounded-bl-md border border-border/60 bg-slate-50 px-3 py-2 shadow-sm">
        {[0, 1, 2].map((d) => (
          <span
            key={d}
            className="size-1.5 animate-bounce rounded-full bg-brand-500"
            style={{ animationDelay: `${d * 0.18}s` }}
          />
        ))}
      </div>
      <div className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[10px] text-slate-400">
        Ask anything…
        <span className="ml-auto flex size-5 items-center justify-center rounded-full bg-brand-600 text-white">
          <Send className="size-2.5" />
        </span>
      </div>
    </MockCard>
  );
}

function ProjectsVisual() {
  const rows = [
    { title: "REST API Clone", tags: ["Spring", "MySQL"], status: "In Progress", live: true },
    { title: "Portfolio Site", tags: ["Next.js"], status: "Completed", live: false },
  ];
  return (
    <MockCard className="space-y-2">
      {rows.map((row) => (
        <div
          key={row.title}
          className="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-white px-2.5 py-2"
        >
          <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-brand-600/10 text-brand-700">
            <FolderOpen className="size-3.5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[10px] font-bold text-slate-800">{row.title}</p>
            <div className="mt-1 flex gap-1">
              {row.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded bg-brand-600/10 px-1.5 py-px text-[8px] font-black text-brand-700"
                >
                  {tag}
                </span>
              ))}
            </div>
          </div>
          <span
            className={cn(
              "flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[8px] font-black",
              row.live ? "bg-amber-500/10 text-amber-700" : "bg-brand-600/10 text-brand-700",
            )}
          >
            <span className={cn("size-1.5 rounded-full", row.live ? "bg-amber-500" : "bg-brand-600")} />
            {row.status}
          </span>
        </div>
      ))}
    </MockCard>
  );
}

function ProgressVisual() {
  const bars = [34, 48, 42, 60, 55, 72, 88];
  const R = 26;
  const C = 2 * Math.PI * R;
  return (
    <MockCard>
      <div className="flex items-center gap-3.5">
        <div className="relative size-20 shrink-0">
          <svg viewBox="0 0 64 64" className="size-full -rotate-90">
            <circle cx="32" cy="32" r={R} fill="none" strokeWidth="7" className="stroke-slate-100" />
            <circle
              cx="32"
              cy="32"
              r={R}
              fill="none"
              strokeWidth="7"
              strokeLinecap="round"
              stroke="url(#progressGrad)"
              strokeDasharray={C}
              strokeDashoffset={C * (1 - 0.72)}
            />
            <defs>
              <linearGradient id="progressGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stopColor="var(--color-brand-700)" />
                <stop offset="100%" stopColor="var(--color-brand-400)" />
              </linearGradient>
            </defs>
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-sm font-black text-slate-800">72%</span>
            <span className="text-[7px] font-bold tracking-wider text-slate-400 uppercase">ready</span>
          </div>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[10px] font-black text-slate-800">This week</p>
          <div className="mt-2 flex h-12 items-end gap-1.5">
            {bars.map((h, i) => (
              <div
                key={i}
                className={cn(
                  "flex-1 rounded-sm",
                  i === bars.length - 1
                    ? "bg-gradient-to-t from-brand-700 to-brand-400"
                    : "bg-brand-600/15",
                )}
                style={{ height: `${h}%` }}
              />
            ))}
          </div>
          <div className="mt-1 flex justify-between text-[7px] font-bold text-slate-400">
            <span>M</span>
            <span>W</span>
            <span>F</span>
            <span>S</span>
          </div>
        </div>
      </div>
    </MockCard>
  );
}

function ReportVisual() {
  return (
    <MockCard>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-[8px] font-black tracking-widest text-slate-400 uppercase">Readiness report</p>
          <p className="mt-0.5 text-lg leading-none font-black text-slate-800">
            87<span className="text-[10px] font-bold text-slate-400">/100</span>
          </p>
        </div>
        <span className="rounded-full bg-brand-600/10 px-2.5 py-1 text-[9px] font-black text-brand-700">
          INDUSTRY READY
        </span>
      </div>
      <div className="mt-2.5 space-y-1.5">
        {["Skills verified", "2 projects shipped", "CV + LinkedIn polished"].map((line) => (
          <p key={line} className="flex items-center gap-1.5 text-[10px] font-semibold text-slate-600">
            <CircleCheck className="size-3.5 shrink-0 text-brand-600" />
            {line}
          </p>
        ))}
      </div>
      <p className="mt-2.5 flex items-center justify-center gap-1.5 rounded-lg bg-pine-950 py-2 text-[10px] font-bold text-white">
        <Download className="size-3" /> Download Report
      </p>
    </MockCard>
  );
}

/* ------------------------------------------------------------------ */
/*  Cards + section                                                    */
/* ------------------------------------------------------------------ */

const features = [
  {
    icon: ClipboardCheck,
    title: "Assess Your Skills",
    description:
      "Take role-specific assessments mapped to your target career and get skill-wise scores.",
    visual: <AssessVisual />,
  },
  {
    icon: BarChart3,
    title: "Find Your Skill Gaps",
    description:
      "See exactly where you stand versus the competency required for your target role.",
    visual: <GapsVisual />,
  },
  {
    icon: Sparkles,
    title: "Get AI Guidance",
    description:
      "Ask the CODEIT Career AI Assistant what to learn next — answers grounded in your data.",
    visual: <AiVisual />,
  },
  {
    icon: FolderKanban,
    title: "Learn & Build Projects",
    description:
      "Get recommended projects that close your gaps with the technologies employers expect.",
    visual: <ProjectsVisual />,
  },
  {
    icon: TrendingUp,
    title: "Track Your Progress",
    description:
      "Watch your career readiness improve as you complete roadmaps, projects and assessments.",
    visual: <ProgressVisual />,
  },
  {
    icon: Rocket,
    title: "Become Industry Ready",
    description:
      "Generate a Career Readiness Report and walk into interviews knowing exactly where you stand.",
    visual: <ReportVisual />,
  },
];

function FeatureCard({ feature }) {
  const Icon = feature.icon;
  return (
    <div className="group h-full rounded-2xl bg-gradient-to-b from-brand-600/45 via-brand-600/15 to-brand-600/5 p-px shadow-card transition-all duration-300 hover:-translate-y-1.5 hover:from-brand-600/70 hover:via-brand-500/35 hover:to-brand-500/15 hover:shadow-card-hover">
      <div className="flex h-full flex-col overflow-hidden rounded-[calc(1rem-1px)] bg-card">
        <div className="flex flex-1 flex-col p-6 pb-5">
        <div className="flex items-center gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary transition-transform duration-300 group-hover:scale-110">
            <Icon className="size-5" aria-hidden="true" />
          </span>
          <h3 className="text-lg font-extrabold tracking-tight text-balance transition-colors group-hover:text-brand-700">
            {feature.title}
          </h3>
        </div>
        <p className="mt-2.5 text-[15px] leading-relaxed text-pretty text-muted-foreground">
          {feature.description}
        </p>
      </div>
      <VisualShell>{feature.visual}</VisualShell>
      </div>
    </div>
  );
}

/**
 * PathSection — "Your complete path from classroom to career".
 * Static 6-card grid; every visual is hand-built product UI in the
 * site's own brand tokens, so the cards always look native.
 */
export function PathSection() {
  return (
    <section id="features" className="overflow-x-clip border-t border-border/60">
      <div className="mx-auto max-w-7xl px-4 py-16 sm:py-20">
        <div className="mx-auto max-w-2xl text-center">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            <Sparkles className="size-3.5" aria-hidden="true" />
            Everything in one place
          </span>
          <h2 className="mt-5 text-2xl font-bold tracking-tight text-balance sm:text-4xl">
            Your complete path from <span className="text-primary">classroom to career</span>
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-muted-foreground">
            Assess your skills, close the gaps that matter and prove you&apos;re ready —
            assessments, roadmaps, projects and reports, all in one place.
          </p>
        </div>
        <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature) => (
            <FeatureCard key={feature.title} feature={feature} />
          ))}
        </div>
      </div>
    </section>
  );
}
