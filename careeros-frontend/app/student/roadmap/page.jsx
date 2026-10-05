"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowRight,
  CheckCircle2,
  Circle,
  Clock3,
  List,
  Loader2,
  Map as MapIcon,
  Play,
  RotateCcw,
  Route,
  Sparkles,
  Target,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { prettifyEnum } from "@/lib/format";
import { cn } from "cn";

const STATUS_STYLES = {
  NOT_STARTED: "",
  IN_PROGRESS: "bg-primary/10 text-primary",
  COMPLETED: "bg-chart-3/15 text-chart-3",
};

function statusLabel(status) {
  if (status === "COMPLETED") return "Completed";
  if (status === "IN_PROGRESS") return "In progress";
  return "Not started";
}

/**
 * Plain-language explanation of why a step matters for THIS student,
 * derived only from their own assessment gap — no generic filler.
 */
function whyFor(item, careerName) {
  if (item.status === "COMPLETED") return "Done — this step is behind you. Nice work.";
  if (!item.skillName) {
    return "A hands-on step that ties everything you learned into one result you can show.";
  }
  if (!item.assessed) {
    return `We haven't measured your ${item.skillName} yet — take the ${careerName} assessment to see exactly where you stand, then this step will tell you how far to go.`;
  }
  if (item.gapPercent < 0) {
    return `Needs work: you're at ${item.scorePercent}%, and ${item.targetPercent}% is expected for a ${careerName}. That's ${-item.gapPercent} points to close — this step is how.`;
  }
  return `Strength: you're at ${item.scorePercent}% vs the ${item.targetPercent}% expected. This step keeps it sharp.`;
}

function gapRank(item) {
  // Lower = should come first. Unmeasured steps go after measured ones.
  if (!item.skillName) return Number.MAX_SAFE_INTEGER - 1;
  if (!item.assessed || item.gapPercent == null) return Number.MAX_SAFE_INTEGER;
  return item.gapPercent;
}

/** Shared step action buttons used by both the path and list views. */
function StepActions({ item, busy, onChange }) {
  if (item.status === "NOT_STARTED") {
    return (
      <Button size="sm" disabled={busy} onClick={() => onChange(item.itemId, "IN_PROGRESS")}>
        {busy ? (
          <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
        ) : (
          <Play className="size-3.5" aria-hidden="true" />
        )}
        Start this step
      </Button>
    );
  }
  if (item.status === "IN_PROGRESS") {
    return (
      <>
        <Button size="sm" disabled={busy} onClick={() => onChange(item.itemId, "COMPLETED")}>
          {busy ? (
            <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
          ) : (
            <CheckCircle2 className="size-3.5" aria-hidden="true" />
          )}
          I finished this
        </Button>
        <Button variant="outline" size="sm" disabled={busy} onClick={() => onChange(item.itemId, "NOT_STARTED")}>
          Not started after all
        </Button>
      </>
    );
  }
  return (
    <Button variant="outline" size="sm" disabled={busy} onClick={() => onChange(item.itemId, "IN_PROGRESS")}>
      <RotateCcw className="size-3.5" aria-hidden="true" />
      Reopen
    </Button>
  );
}

export default function StudentRoadmapPage() {
  const [roadmap, setRoadmap] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [pendingId, setPendingId] = useState(null);
  const [view, setView] = useState("path");

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const data = await api.get("/roadmaps/my");
      setRoadmap(data || null);
    } catch (err) {
      setLoadError(err?.message || "Failed to load your roadmap.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  async function setStatus(itemId, status) {
    setPendingId(itemId);
    try {
      await api.put(`/roadmaps/items/${itemId}/status`, { status });
      await load();
      if (status === "COMPLETED") toast.success("Step completed. Your progress is saved!");
      else if (status === "IN_PROGRESS") toast.success("Step started. One step closer!");
    } catch (err) {
      toast.error(err?.message || "Failed to update the step.");
    } finally {
      setPendingId(null);
    }
  }

  if (loading) return <LoadingState label="Loading your roadmap…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Roadmap"
          description="Your personalized learning path."
          breadcrumb={<Breadcrumb items={[{ label: "Roadmap" }]} />}
        />
        <ErrorState title="Couldn't load your roadmap" description={loadError} onRetry={load} />
      </div>
    );
  }

  if (!roadmap?.hasTarget) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Roadmap"
          description="Your personalized learning path."
          breadcrumb={<Breadcrumb items={[{ label: "Roadmap" }]} />}
        />
        <EmptyState
          icon={Target}
          title="Choose your target career first"
          description="Your roadmap is built from the competency framework of your target career. Select one to generate it."
          action={
            <Button size="sm" render={<Link href="/student/careers" />}>
              Choose career
              <ArrowRight className="size-4" aria-hidden="true" />
            </Button>
          }
        />
      </div>
    );
  }

  const phases = roadmap.phases || [];
  const careerName = roadmap.careerName || "your career";
  const totalItems = roadmap.totalItems ?? 0;
  const completedItems = roadmap.completedItems ?? 0;
  const remaining = Math.max(0, totalItems - completedItems);
  const allDone = totalItems > 0 && completedItems >= totalItems;

  // The single most useful next step for this student: resume what they
  // started, otherwise the open step with the biggest measured skill gap.
  const flatItems = phases.flatMap((phase) =>
    (phase.items || []).map((item) => ({ ...item, phaseTitle: phase.title })),
  );
  const openItems = flatItems.filter((item) => item.status !== "COMPLETED");
  const resumed = openItems.find((item) => item.status === "IN_PROGRESS");
  const nextStep = resumed || [...openItems].sort((a, b) => gapRank(a) - gapRank(b))[0] || null;

  // "Which skill first?" — one entry per skill, ordered by biggest measured
  // gap first, unmeasured next, finished skills and the capstone last.
  const skillOrder = (() => {
    const seen = {};
    const entries = [];
    for (const item of flatItems) {
      const key = item.skillName || "__general__";
      if (!seen[key]) {
        seen[key] = {
          key,
          label: item.skillName || "Capstone finish",
          worstGap: null,
          assessed: false,
          allDone: true,
          firstOpenId: null,
        };
        entries.push(seen[key]);
      }
      const entry = seen[key];
      if (item.status !== "COMPLETED") {
        entry.allDone = false;
        if (entry.firstOpenId == null) entry.firstOpenId = item.itemId;
      }
      if (item.assessed && item.gapPercent != null) {
        entry.assessed = true;
        if (entry.worstGap == null || item.gapPercent < entry.worstGap) entry.worstGap = item.gapPercent;
      }
    }
    return entries.sort((a, b) => {
      if (a.allDone !== b.allDone) return a.allDone ? 1 : -1;
      if (a.key === "__general__") return 1;
      if (b.key === "__general__") return -1;
      const gapA = a.assessed ? a.worstGap : Number.MAX_SAFE_INTEGER;
      const gapB = b.assessed ? b.worstGap : Number.MAX_SAFE_INTEGER;
      return gapA - gapB;
    });
  })();

  function skillSub(entry) {
    if (entry.allDone) return "done";
    if (!entry.assessed) return "not measured yet";
    if (entry.worstGap < 0) return `${-entry.worstGap} pts behind`;
    return "on track";
  }

  // Global step order (1..N across phases) for the visual path numbering.
  const stepNumberById = {};
  const statusById = {};
  flatItems.forEach((item, index) => {
    stepNumberById[item.itemId] = index + 1;
    statusById[item.itemId] = item.status;
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Roadmap"
        description={`Your personal 90-day plan to become a ${careerName} — built from your assessment results, easiest wins first.`}
        breadcrumb={<Breadcrumb items={[{ label: "Roadmap" }]} />}
      />

      {/* How this works — one glance, plain words */}
      <Card className="shadow-card">
        <CardContent className="grid gap-3 py-5 sm:grid-cols-3">
          {[
            { n: "1", title: "Take the assessment", text: `We measure your skills against what a ${careerName} needs.` },
            { n: "2", title: "Follow the steps", text: "Each step targets your biggest gap first. Start at the top." },
            { n: "3", title: "Watch yourself grow", text: "Retake assessments — your readiness score climbs as you finish steps." },
          ].map((step) => (
            <div key={step.n} className="flex items-start gap-3">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                {step.n}
              </span>
              <div>
                <p className="text-sm font-semibold">{step.title}</p>
                <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">{step.text}</p>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* Overall journey */}
      <Card className="border-primary/25 shadow-card">
        <CardContent className="flex flex-wrap items-center gap-x-8 gap-y-3 py-5">
          <div className="min-w-52 flex-1">
            <div className="mb-1.5 flex items-center justify-between gap-2 text-sm">
              <span className="font-semibold">
                {allDone
                  ? `You finished the ${careerName} roadmap!`
                  : `Your journey to ${careerName}`}
              </span>
              <span className="tnum text-xs font-semibold text-muted-foreground">
                {completedItems} of {totalItems} steps · {roadmap.progressPercent ?? 0}%
              </span>
            </div>
            <Progress
              value={roadmap.progressPercent ?? 0}
              tone={allDone ? "green" : "primary"}
              className="h-2.5"
              aria-label={`Roadmap progress ${roadmap.progressPercent ?? 0} percent`}
            />
          </div>
          <p className="text-sm text-muted-foreground">
            {allDone ? (
              <>Every step complete — time to retake the assessment and prove it.</>
            ) : (
              <>
                <strong className="tnum text-foreground">{roadmap.inProgressItems ?? 0}</strong> in progress ·{" "}
                <strong className="tnum text-foreground">{remaining}</strong> to go
              </>
            )}
          </p>
        </CardContent>
      </Card>

      {/* Your next step — the one action that matters most right now */}
      {nextStep && !allDone && (
        <Card className="border-primary/40 bg-primary/[0.03] shadow-card">
          <CardContent className="flex flex-wrap items-center gap-4 py-5">
            <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-sm">
              <Sparkles className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-52 flex-1">
              <p className="text-[11px] font-bold uppercase tracking-widest text-primary">
                {nextStep.status === "IN_PROGRESS" ? "Continue where you left off" : "Your best next step"}
              </p>
              <p className="mt-0.5 text-base font-bold leading-snug">{nextStep.title}</p>
              <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                {whyFor(nextStep, careerName)} · From “{nextStep.phaseTitle}” · about {nextStep.estimatedHours} hours.
              </p>
            </div>
            <Button
              disabled={pendingId === nextStep.itemId}
              onClick={() =>
                setStatus(nextStep.itemId, nextStep.status === "IN_PROGRESS" ? "COMPLETED" : "IN_PROGRESS")
              }
            >
              {pendingId === nextStep.itemId ? (
                <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
              ) : nextStep.status === "IN_PROGRESS" ? (
                <CheckCircle2 className="size-3.5" aria-hidden="true" />
              ) : (
                <Play className="size-3.5" aria-hidden="true" />
              )}
              {nextStep.status === "IN_PROGRESS" ? "Mark complete" : "Start now"}
            </Button>
          </CardContent>
        </Card>
      )}
      {/* View switch + skill order: which skill to learn first, second, … */}
      <Card className="shadow-card">
        <CardContent className="space-y-4 py-5">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex rounded-full bg-muted p-1" role="group" aria-label="Roadmap view">
              <button
                type="button"
                onClick={() => setView("path")}
                aria-pressed={view === "path"}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold transition",
                  view === "path"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <MapIcon className="size-3.5" aria-hidden="true" />
                Visual path
              </button>
              <button
                type="button"
                onClick={() => setView("list")}
                aria-pressed={view === "list"}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-semibold transition",
                  view === "list"
                    ? "bg-background text-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                <List className="size-3.5" aria-hidden="true" />
                Detailed list
              </button>
            </div>
            <p className="text-xs text-muted-foreground">
              {view === "path"
                ? "Follow the numbered path top to bottom — that is your step-by-step order."
                : "Every step with full guidance and progress buttons."}
            </p>
          </div>
          {skillOrder.length > 0 && (
            <div>
              <p className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                Learn your skills in this order
              </p>
              <ol className="mt-2 flex items-stretch gap-1.5 overflow-x-auto pb-1">
                {skillOrder.map((entry, index) => (
                  <li key={entry.key} className="flex shrink-0 items-stretch gap-1.5">
                    {index > 0 && (
                      <ArrowRight className="size-4 shrink-0 self-center text-muted-foreground/60" aria-hidden="true" />
                    )}
                    <a
                      href={entry.firstOpenId ? `#roadmap-step-${entry.firstOpenId}` : undefined}
                      onClick={(event) => {
                        if (!entry.firstOpenId) event.preventDefault();
                      }}
                      className={cn(
                        "block min-w-28 rounded-xl border px-3 py-2 text-left transition",
                        entry.allDone
                          ? "border-chart-3/40 bg-chart-3/10"
                          : index === 0
                            ? "border-primary/50 bg-primary/[0.06] ring-1 ring-primary/30"
                            : "border-border/60 bg-background/40 hover:border-primary/40",
                      )}
                      title={entry.firstOpenId ? `Jump to the next open ${entry.label} step` : `${entry.label} is complete`}
                    >
                      <span className="flex items-center gap-1.5 text-xs font-bold">
                        <span className="flex size-5 items-center justify-center rounded-full bg-primary/10 text-[10px] text-primary">
                          {index + 1}
                        </span>
                        <span className="truncate">{entry.label}</span>
                      </span>
                      <span
                        className={cn(
                          "mt-0.5 block text-[11px] font-semibold",
                          entry.allDone
                            ? "text-chart-3"
                            : entry.assessed && entry.worstGap < 0
                              ? "text-amber-700 dark:text-amber-400"
                              : "text-muted-foreground",
                        )}
                      >
                        {skillSub(entry)}
                      </span>
                    </a>
                  </li>
                ))}
              </ol>
            </div>
          )}
        </CardContent>
      </Card>

      {phases.length === 0 ? (
        <EmptyState
          icon={Route}
          title="No roadmap published yet"
          description="Your target career has no roadmap phases right now. Check back soon."
        />
      ) : view === "path" ? (
        <ol className="space-y-8">
          {phases.map((phase, phaseIndex) => {
            const phaseTotal = phase.totalItems ?? 0;
            const phaseDone = phase.completedItems ?? 0;
            const phaseComplete = phaseTotal > 0 && phaseDone >= phaseTotal;
            return (
              <li key={phase.phaseId}>
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <Badge
                    variant="secondary"
                    className={cn("text-xs", phaseComplete && "bg-chart-3/15 text-chart-3")}
                  >
                    Phase {phaseIndex + 1} of {phases.length}
                  </Badge>
                  <p className="text-sm font-bold">{phase.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {phaseComplete ? "complete" : `${phaseDone}/${phaseTotal} steps done`} · about {phase.durationDays} days
                  </p>
                </div>
                <ol className="space-y-0">
                  {(phase.items || []).map((item) => {
                    const busy = pendingId === item.itemId;
                    const isNext = nextStep?.itemId === item.itemId;
                    const stepNumber = stepNumberById[item.itemId] ?? 0;
                    const isFirst = stepNumber <= 1;
                    const prevDone =
                      stepNumber > 1 && statusById[flatItems[stepNumber - 2]?.itemId] === "COMPLETED";
                    const done = item.status === "COMPLETED";
                    return (
                      <li
                        key={item.itemId}
                        id={`roadmap-step-${item.itemId}`}
                        className="relative flex scroll-mt-24 gap-3.5 sm:gap-5"
                      >
                        <div className="flex flex-col items-center" aria-hidden="true">
                          {!isFirst && (
                            <span className={cn("w-1 flex-1 rounded-full", prevDone ? "bg-chart-3" : "bg-border/70")} style={{ minHeight: 12 }} />
                          )}
                          <span
                            className={cn(
                              "relative z-10 flex size-12 shrink-0 items-center justify-center rounded-full text-sm font-bold shadow-md transition",
                              done
                                ? "bg-chart-3 text-white"
                                : item.status === "IN_PROGRESS"
                                  ? "bg-primary text-primary-foreground"
                                  : isNext
                                    ? "bg-primary text-primary-foreground ring-4 ring-primary/25"
                                    : "border-2 border-border bg-card text-muted-foreground",
                            )}
                          >
                            {done ? <CheckCircle2 className="size-5" /> : stepNumber}
                            {isNext && !done && (
                              <span className="absolute -right-0.5 -top-0.5 flex size-3.5">
                                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-60" />
                                <span className="relative inline-flex size-3.5 rounded-full border-2 border-background bg-primary" />
                              </span>
                            )}
                          </span>
                          <span className={cn("w-1 flex-1 rounded-full", done ? "bg-chart-3" : "bg-border/70")} style={{ minHeight: 12 }} />
                        </div>
                        <div
                          className={cn(
                            "mb-1 flex-1 rounded-2xl border bg-card p-4 shadow-card transition sm:p-5",
                            isNext ? "border-primary/50 ring-1 ring-primary/30" : "border-border/60",
                          )}
                        >
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">
                              Step {stepNumber} of {totalItems}
                            </span>
                            {isNext && !allDone && (
                              <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-primary">
                                {item.status === "IN_PROGRESS" ? "Continue here" : "Do this next"}
                              </span>
                            )}
                            <Badge variant="secondary" className={`ml-auto shrink-0 ${STATUS_STYLES[item.status] || ""}`}>
                              {statusLabel(item.status)}
                            </Badge>
                          </div>
                          <p className="mt-1 text-[15px] font-bold leading-snug">{item.title}</p>
                          <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                            {whyFor(item, careerName)}
                          </p>
                          <div className="mt-2.5 flex flex-wrap items-center gap-2 text-xs">
                            {item.skillName && (
                              <Badge variant="outline">{item.skillName}</Badge>
                            )}
                            {item.skillName && (
                              <span className="tnum font-semibold text-muted-foreground">
                                {item.assessed
                                  ? `You ${item.scorePercent}% · need ${item.targetPercent}%`
                                  : `Need ${item.targetPercent}%`}
                              </span>
                            )}
                            <span className="inline-flex items-center gap-1 text-muted-foreground">
                              <Clock3 className="size-3.5" aria-hidden="true" />
                              ~{item.estimatedHours}h
                            </span>
                          </div>
                          <div className="mt-3 flex flex-wrap gap-2">
                            <StepActions item={item} busy={busy} onChange={setStatus} />
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ol>
              </li>
            );
          })}
        </ol>
      ) : (
        <ol className="space-y-6">
          {phases.map((phase, phaseIndex) => {
            const phaseTotal = phase.totalItems ?? 0;
            const phaseDone = phase.completedItems ?? 0;
            const phaseComplete = phaseTotal > 0 && phaseDone >= phaseTotal;
            const phasePercent = phaseTotal === 0 ? 0 : Math.round((phaseDone / phaseTotal) * 100);
            return (
              <li key={phase.phaseId} className="relative flex gap-4">
                <div className="flex flex-col items-center" aria-hidden="true">
                  <span
                    className={cn(
                      "flex size-9 shrink-0 items-center justify-center rounded-full text-sm font-bold shadow-sm",
                      phaseComplete ? "bg-chart-3 text-white" : "bg-primary text-primary-foreground",
                    )}
                  >
                    {phaseComplete ? <CheckCircle2 className="size-4.5" /> : phaseIndex + 1}
                  </span>
                  {phaseIndex < phases.length - 1 && <span className="w-0.5 flex-1 bg-border/70" />}
                </div>
                <Card className="mb-2 flex-1 shadow-card">
                  <CardHeader>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <p className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                          Phase {phaseIndex + 1} of {phases.length} · about {phase.durationDays} days
                        </p>
                        <CardTitle className="mt-0.5 text-base leading-snug">{phase.title}</CardTitle>
                        <CardDescription className="mt-1">{phase.description}</CardDescription>
                      </div>
                      <Badge
                        variant="secondary"
                        className={cn("shrink-0", phaseComplete && "bg-chart-3/15 text-chart-3")}
                      >
                        {phaseComplete ? "Phase complete" : `${phaseDone}/${phaseTotal} steps done`}
                      </Badge>
                    </div>
                    <Progress
                      value={phasePercent}
                      tone={phaseComplete ? "green" : "primary"}
                      className="mt-3 h-2"
                      aria-label={`${phase.title} progress ${phasePercent} percent`}
                    />
                  </CardHeader>
                  <CardContent>
                    <ul className="space-y-3">
                      {(phase.items || []).map((item) => {
                        const busy = pendingId === item.itemId;
                        const isNext = nextStep?.itemId === item.itemId;
                        return (
                          <li
                            key={item.itemId}
                            id={`roadmap-step-${item.itemId}`}
                            className={cn(
                              "scroll-mt-24 rounded-xl border bg-background/40 p-4 transition",
                              isNext
                                ? "border-primary/50 shadow-card ring-1 ring-primary/30"
                                : "border-border/50",
                            )}
                          >
                            <div className="flex flex-wrap items-start justify-between gap-2">
                              <div className="flex min-w-0 flex-1 items-start gap-2.5">
                                {item.status === "COMPLETED" ? (
                                  <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-chart-3" aria-hidden="true" />
                                ) : (
                                  <Circle className="mt-0.5 size-5 shrink-0 text-muted-foreground/50" aria-hidden="true" />
                                )}
                                <div className="min-w-0">
                                  <p className="flex flex-wrap items-center gap-2 text-sm font-semibold leading-snug">
                                    {item.title}
                                    {isNext && !allDone && (
                                      <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-primary">
                                        Up next
                                      </span>
                                    )}
                                  </p>
                                  <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                                    <span className="font-semibold text-foreground">Why this matters for you: </span>
                                    {whyFor(item, careerName)}
                                  </p>
                                  {item.learningGoal && (
                                    <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
                                      <span className="font-semibold text-foreground">You&apos;ll be able to: </span>
                                      {item.learningGoal}
                                    </p>
                                  )}
                                </div>
                              </div>
                              <Badge variant="secondary" className={`shrink-0 ${STATUS_STYLES[item.status] || ""}`}>
                                {statusLabel(item.status)}
                              </Badge>
                            </div>
                            <div className="mt-2.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                              {item.skillName ? (
                                <span className="inline-flex flex-wrap items-center gap-1.5">
                                  <Badge variant="outline">{item.skillName}</Badge>
                                  {item.assessed ? (
                                    <span className="tnum font-semibold">
                                      You: {item.scorePercent}% · Needed: {item.targetPercent}%
                                      {item.gapPercent < 0 ? (
                                        <span className="text-amber-700 dark:text-amber-400"> · { -item.gapPercent} points behind</span>
                                      ) : (
                                        <span className="text-chart-3"> · target met</span>
                                      )}
                                    </span>
                                  ) : (
                                    <span>
                                      Needed: {item.targetPercent}% ·{" "}
                                      <Link href="/student/assessment" className="font-semibold text-primary hover:underline">
                                        take the assessment to measure yourself
                                      </Link>
                                    </span>
                                  )}
                                </span>
                              ) : (
                                <span>Hands-on portfolio step</span>
                              )}
                              <span className="inline-flex items-center gap-1">
                                <Clock3 className="size-3.5" aria-hidden="true" />
                                About {item.estimatedHours} hours
                              </span>
                              {item.skillCategory && (
                                <span>{prettifyEnum(item.skillCategory)}</span>
                              )}
                            </div>
                            <div className="mt-3 flex flex-wrap gap-2">
                              <StepActions item={item} busy={busy} onChange={setStatus} />
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  </CardContent>
                </Card>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
