"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import {
  motion,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
  useSpring,
  useTransform,
} from "motion/react";
import {
  ArrowRight,
  BarChart3,
  Check,
  ClipboardCheck,
  GraduationCap,
  Rocket,
  Sparkles,
} from "lucide-react";
import { cn } from "cn";

const journeySteps = [
  {
    icon: ClipboardCheck,
    title: "Assess",
    text: "Take a role-specific assessment mapped to your target career.",
    meta: "Skill-wise scores",
    next: "Next: Analyze",
  },
  {
    icon: BarChart3,
    title: "Analyze",
    text: "See your skill gaps and readiness score on one clear dashboard.",
    meta: "Readiness score",
    next: "Next: Improve",
  },
  {
    icon: Rocket,
    title: "Improve",
    text: "Follow your roadmap, build projects and get AI guidance.",
    meta: "Roadmaps + AI coach",
    next: "Next: Become Ready",
  },
  {
    icon: GraduationCap,
    title: "Become Ready",
    text: "Generate your Career Readiness Report and walk in confident.",
    meta: "Industry-ready report",
    next: "Outcome: Interview-ready",
  },
];

function StepCard({ step, index, isActive, isDone }) {
  const Icon = step.icon;
  const isLast = index === journeySteps.length - 1;
  return (
    <motion.li
      initial={false}
      animate={{ scale: isActive ? 1.02 : 1, opacity: isActive ? 1 : 0.65 }}
      transition={{ type: "spring", stiffness: 260, damping: 26 }}
      className="group relative min-w-0"
    >
      {/* connector line to next card */}
      {index < journeySteps.length - 1 && (
        <span
          aria-hidden="true"
          className="absolute top-full left-10 z-0 h-5 w-px translate-y-0 bg-gradient-to-b from-brand-500/50 to-transparent sm:left-12"
        />
      )}
      <div
        className={cn(
          "relative z-10 h-full overflow-hidden rounded-2xl border p-5 transition-all duration-300 hover:-translate-y-1 sm:p-6",
          isActive
            ? "border-pine-700 bg-pine-950 text-white shadow-popover hover:shadow-card-hover"
            : "border-border/60 bg-card shadow-card hover:border-brand-500/40 hover:shadow-card-hover",
        )}
      >
        {/* glow wash — clipped to card shape, never covers text */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
        >
          <span
            aria-hidden="true"
            className={cn(
              "absolute -top-20 -right-20 size-48 rounded-full blur-3xl transition-opacity duration-500",
              isActive
                ? "bg-brand-500/25 opacity-100"
                : "bg-brand-500/10 opacity-0 group-hover:opacity-100",
            )}
          />
        </span>
        {/* top accent rule — site brand gradient */}
        <span
          aria-hidden="true"
          className={cn(
            "pointer-events-none absolute inset-x-0 top-0 h-[3px] bg-gradient-to-r from-brand-700 via-brand-500 to-brand-400",
            isActive ? "opacity-90" : "opacity-40",
          )}
        />
        {/* ghost number */}
        <span
          aria-hidden="true"
          className={cn(
            "pointer-events-none absolute top-2 right-4 font-mono text-5xl font-black tracking-tighter transition-colors duration-500 select-none sm:text-6xl",
            isActive ? "text-white/10" : "text-foreground/[0.06]",
          )}
        >
          {`0${index + 1}`}
        </span>
        {/* step pill + done tick — in-flow so it is never cut off */}
        <span className="relative z-10 mt-0.5 mb-4 inline-flex w-fit items-center gap-1.5 rounded-full bg-brand-600 px-2.5 py-0.5 text-[11px] font-bold tracking-wider text-white uppercase shadow-md">
          Step {index + 1}
          {isDone && (
            <span className="inline-flex size-3.5 items-center justify-center rounded-full bg-white/25">
              <Check className="size-2.5" aria-hidden="true" />
            </span>
          )}
        </span>
        <div className="relative z-10 flex items-start gap-3.5">
          <span
            className={cn(
              "flex size-11 shrink-0 items-center justify-center rounded-xl shadow-sm transition-transform duration-300 group-hover:scale-110 sm:size-12",
              isActive ? "bg-white/10 text-brand-300" : "bg-primary/10 text-primary",
            )}
          >
            <Icon className="size-5 sm:size-6" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <h3 className="text-base font-extrabold tracking-tight break-words sm:text-lg">{step.title}</h3>
            <p
              className={cn(
                "mt-1 text-sm leading-relaxed break-words",
                isActive ? "text-pine-200" : "text-muted-foreground",
              )}
            >
              {step.text}
            </p>
            <span
              className={cn(
                "mt-2.5 inline-flex max-w-full items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold break-words",
                isActive
                  ? "bg-white/10 text-white"
                  : "border border-border/70 bg-muted/60 text-muted-foreground",
              )}
            >
              <Sparkles className="size-3 shrink-0" aria-hidden="true" />
              <span className="min-w-0">{step.meta}</span>
            </span>
          </div>
        </div>
        <p
          className={cn(
            "relative z-10 mt-4 flex items-center gap-1.5 border-t border-dashed pt-3.5 text-xs font-bold",
            isActive ? "border-white/15 text-pine-200" : "border-border/70 text-muted-foreground",
          )}
        >
          {isLast ? (
            <Check className="size-3.5 text-brand-500" aria-hidden="true" />
          ) : (
            <ArrowRight className="size-3.5 text-brand-600" aria-hidden="true" />
          )}
          {step.next}
        </p>
      </div>
    </motion.li>
  );
}

/**
 * JourneySection — "How it works" as a scroll-driven journey.
 * A sticky rail tracks scroll progress through the four steps: the
 * connecting line fills, dots light up, and the active step takes the
 * dark-green active style and scales slightly. Same content and styling.
 */
export function JourneySection() {
  const stepsRef = useRef(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: stepsRef,
    offset: ["start center", "end center"],
  });
  const smooth = useSpring(scrollYProgress, { stiffness: 110, damping: 26, mass: 0.5 });

  const [active, setActive] = useState(0);
  useMotionValueEvent(smooth, "change", (v) => {
    setActive(
      Math.min(journeySteps.length - 1, Math.max(0, Math.floor(v * journeySteps.length))),
    );
  });

  const shown = reduceMotion ? journeySteps.length - 1 : active;
  const mobileFill = useTransform(smooth, [0, 1], [0.03, 1]);

  const current = journeySteps[shown];

  return (
    <section
      id="how-it-works"
      className="relative overflow-hidden border-t border-border/60"
    >
      {/* faint ambient glow — decorative only, no motion */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(36rem 16rem at 50% 0%, oklch(0.9 0.07 152 / 0.45), transparent 65%)",
        }}
      />
      <div className="relative mx-auto max-w-7xl px-4 py-16 sm:py-20">
        <div className="mx-auto max-w-2xl text-center">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary">
            <Sparkles className="size-3.5" aria-hidden="true" />
            How it works
          </span>
          <h2 className="mt-5 text-2xl font-bold tracking-tight text-balance sm:text-4xl">
            Your journey to <span className="text-primary">interview-ready</span>
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-muted-foreground">
            Four guided steps from &ldquo;where am I today?&rdquo; to hired — clear, focused, and
            built for students.
          </p>
          <div className="mt-5 flex flex-wrap items-center justify-center gap-2 text-[11px] font-semibold">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-white/70 px-3 py-1.5 text-muted-foreground backdrop-blur">
              <Check className="size-3.5 text-brand-600" aria-hidden="true" />
              4 guided steps
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-white/70 px-3 py-1.5 text-muted-foreground backdrop-blur">
              <Check className="size-3.5 text-brand-600" aria-hidden="true" />
              Personalized to your role
            </span>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border/70 bg-white/70 px-3 py-1.5 text-muted-foreground backdrop-blur">
              <Check className="size-3.5 text-brand-600" aria-hidden="true" />
              Free to start
            </span>
          </div>
        </div>

        {/* Mobile progress rail */}
        <div className="mx-auto mt-8 max-w-4xl lg:hidden" aria-hidden="true">
          <div className="rounded-2xl border border-border/60 bg-white/70 p-4 shadow-card backdrop-blur">
            <div className="mb-2.5 flex flex-wrap items-center justify-between gap-2 text-xs font-semibold text-muted-foreground">
              <span className="inline-flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                <span className="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-600 text-[11px] font-bold text-white">
                  {shown + 1}
                </span>
                <span className="whitespace-normal">
                  Step {shown + 1} of {journeySteps.length} —{" "}
                  <span className="text-foreground">{current.title}</span>
                </span>
              </span>
              <span className="tnum shrink-0 rounded-full bg-brand-600/10 px-2 py-0.5 text-brand-700">
                {Math.round(((shown + 1) / journeySteps.length) * 100)}%
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-brand-600/15">
              <motion.div
                style={reduceMotion ? undefined : { scaleX: mobileFill }}
                className="h-full w-full origin-left rounded-full bg-gradient-to-r from-brand-700 via-brand-500 to-brand-400"
              />
            </div>
          </div>
        </div>

        <div ref={stepsRef} className="mx-auto mt-8 max-w-4xl">
          {/* Steps */}
          <ol className="min-w-0 space-y-5 overflow-visible pt-2">
            {journeySteps.map((step, index) => (
              <StepCard
                key={step.title}
                step={step}
                index={index}
                isActive={reduceMotion ? index === journeySteps.length - 1 : index === shown}
                isDone={reduceMotion ? index < journeySteps.length - 1 : index < shown}
              />
            ))}
          </ol>
        </div>

        <div className="mt-10 text-center">
          <Link
            href="/register"
            className="btn-polish inline-flex h-12 items-center justify-center gap-2.5 rounded-full bg-primary px-8 text-base font-bold text-primary-foreground shadow-lg hover:bg-primary/85 active:translate-y-px"
          >
            Start your journey — it&rsquo;s free
            <ArrowRight className="size-5" aria-hidden="true" />
          </Link>
          <p className="mt-3 text-xs text-muted-foreground">
            No credit card needed · Takes just 2 minutes
          </p>
        </div>
      </div>
    </section>
  );
}
