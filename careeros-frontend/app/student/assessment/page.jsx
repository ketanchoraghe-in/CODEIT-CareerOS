"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowRight,
  Award,
  CheckCircle2,
  ClipboardList,
  Clock3,
  History,
  Loader2,
  Play,
  RotateCcw,
  Target,
  Timer,
  Zap,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CircularProgress } from "@/components/ui/circular-progress";
import { Progress } from "@/components/ui/progress";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { cn } from "cn";

function latestChip(latest) {
  if (!latest) return { label: "Not started", className: "" };
  if (latest.status === "IN_PROGRESS") {
    return {
      label: `In progress · ${latest.answeredCount ?? 0}/${latest.totalQuestions ?? 0} answered`,
      className: "bg-primary/10 text-primary",
    };
  }
  if (latest.status === "SUBMITTED") {
    return {
      label: `Submitted · score ${latest.overallScore ?? 0}`,
      className: "bg-chart-3/15 text-chart-3",
    };
  }
  if (latest.status === "EXPIRED") {
    return { label: "Expired · retake available", className: "bg-destructive/10 text-destructive" };
  }
  return { label: latest.status, className: "" };
}

export default function StudentAssessmentPage() {
  const router = useRouter();
  const [profile, setProfile] = useState(null);
  const [assessments, setAssessments] = useState([]);
  const [latestByAssessment, setLatestByAssessment] = useState({});
  const [historyByAssessment, setHistoryByAssessment] = useState({});
  const [historyOpen, setHistoryOpen] = useState({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [startingId, setStartingId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const profileData = await api.get("/students/me");
      setProfile(profileData || null);
      if (profileData?.targetCareerId) {
        const list = await api.get(`/assessments/${profileData.targetCareerId}/available`);
        const items = Array.isArray(list) ? list : [];
        setAssessments(items);
        const entries = await Promise.all(
          items.map(async (assessment) => {
            try {
              const latest = await api.get("/assessments/attempts/my", {
                params: { assessmentId: assessment.assessmentId },
              });
              return [assessment.assessmentId, latest || null];
            } catch {
              return [assessment.assessmentId, null];
            }
          }),
        );
        setLatestByAssessment(Object.fromEntries(entries));
        const historyEntries = await Promise.all(
          items.map(async (assessment) => {
            try {
              const history = await api.get("/students/me/attempts", {
                params: { assessmentId: assessment.assessmentId },
              });
              return [assessment.assessmentId, Array.isArray(history) ? history : []];
            } catch {
              return [assessment.assessmentId, []];
            }
          }),
        );
        setHistoryByAssessment(Object.fromEntries(historyEntries));
      } else {
        setAssessments([]);
        setLatestByAssessment({});
        setHistoryByAssessment({});
      }
    } catch (err) {
      setLoadError(err?.message || "Failed to load assessments.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  async function handleStart(assessmentId, latest) {
    if (latest?.status === "IN_PROGRESS" && latest?.attemptId) {
      router.push(`/student/assessment/attempt/${latest.attemptId}`);
      return;
    }
    setStartingId(assessmentId);
    try {
      const started = await api.post(`/assessments/${assessmentId}/start`);
      router.push(`/student/assessment/attempt/${started.attemptId}`);
    } catch (err) {
      toast.error(err?.message || "Failed to start the assessment.");
    } finally {
      setStartingId(null);
    }
  }

  if (loading) return <LoadingState label="Loading assessments…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Assessment"
          description="Role-specific assessments mapped to your target career."
          breadcrumb={<Breadcrumb items={[{ label: "Assessment" }]} />}
        />
        <ErrorState title="Couldn't load assessments" description={loadError} onRetry={load} />
      </div>
    );
  }

  if (!profile?.targetCareerId) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Assessment"
          description="Role-specific assessments mapped to your target career."
          breadcrumb={<Breadcrumb items={[{ label: "Assessment" }]} />}
        />
        <EmptyState
          icon={Target}
          title="Choose your target career first"
          description="Assessments are mapped to the competency framework of your target career. Select one to unlock them."
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

  const submittedLatests = assessments
    .map((a) => latestByAssessment[a.assessmentId])
    .filter((l) => l?.status === "SUBMITTED" && l?.overallScore != null);
  const completedCount = submittedLatests.length;
  const bestScore = submittedLatests.length > 0
    ? Math.max(...submittedLatests.map((l) => l.overallScore))
    : null;
  const inProgressCount = assessments.filter(
    (a) => latestByAssessment[a.assessmentId]?.status === "IN_PROGRESS",
  ).length;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assessment"
        description={`Assessments for your target career — ${profile.targetCareerName || "your career"}.`}
        breadcrumb={<Breadcrumb items={[{ label: "Assessment" }]} />}
      />

      {/* Hero — what to do + where you stand */}
      <Card className="relative overflow-hidden border-primary/25 shadow-card">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0">
          <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.12] via-transparent to-transparent" />
          <div
            className="absolute inset-0"
            style={{ backgroundImage: "radial-gradient(var(--color-primary) 1px, transparent 1px)", backgroundSize: "22px 22px", WebkitMaskImage: "linear-gradient(115deg, black 0%, transparent 55%)", maskImage: "linear-gradient(115deg, black 0%, transparent 55%)", opacity: 0.12 }}
          />
          <ClipboardList className="absolute -right-6 -bottom-8 size-44 rotate-[-12deg] text-primary/[0.07]" aria-hidden="true" />
        </div>
        <CardContent className="relative py-6">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-center">
            <div className="min-w-0 flex-1">
              <p className="inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-[0.18em] text-primary">
                <Zap className="size-3.5" aria-hidden="true" />
                Skill check · {profile.targetCareerName || "your career"}
              </p>
              <h2 className="mt-1.5 text-xl font-extrabold tracking-tight sm:text-2xl">
                Prove your skills, find your gaps
              </h2>
              <p className="mt-1 max-w-xl text-sm leading-relaxed text-muted-foreground">
                Each test is timed and reshuffled every attempt. Submit it and your scores map
                straight onto your roadmap — biggest gaps first.
              </p>
              <div className="mt-4 grid max-w-xl gap-2.5 sm:grid-cols-3">
                {[
                  { icon: Play, title: "Start when ready", text: "Pick a test below and begin." },
                  { icon: Timer, title: "Beat the timer", text: "Server-enforced time limit." },
                  { icon: Target, title: "Get gaps mapped", text: "Scores feed your roadmap." },
                ].map((s, i) => (
                  <div key={s.title} className="relative flex items-start gap-2.5 rounded-2xl border border-border/60 bg-card/80 p-3">
                    <span className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <s.icon className="size-4" aria-hidden="true" />
                    </span>
                    <div className="min-w-0">
                      <p className="text-xs font-bold"><span className="mr-1 text-primary">{i + 1}.</span>{s.title}</p>
                      <p className="mt-0.5 text-[11px] leading-snug text-muted-foreground">{s.text}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="grid shrink-0 grid-cols-3 gap-2.5 lg:w-64 lg:grid-cols-1">
              {[
                { label: "Available tests", value: assessments.length, icon: ClipboardList },
                { label: "Completed", value: completedCount, icon: CheckCircle2 },
                { label: "Best score", value: bestScore == null ? "—" : `${bestScore}%`, icon: Award },
              ].map((s) => (
                <div key={s.label} className="flex items-center gap-2.5 rounded-2xl border border-border/60 bg-card/80 px-3.5 py-3">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                    <s.icon className="size-4" aria-hidden="true" />
                  </span>
                  <div className="min-w-0">
                    <p className="tnum text-lg leading-none font-extrabold tracking-tight">{s.value}</p>
                    <p className="mt-1 truncate text-[11px] font-medium text-muted-foreground">{s.label}</p>
                  </div>
                </div>
              ))}
              {inProgressCount > 0 && (
                <p className="col-span-3 rounded-xl bg-primary/10 px-3 py-2 text-center text-[11px] font-bold text-primary lg:col-span-1">
                  {inProgressCount} attempt{inProgressCount === 1 ? "" : "s"} waiting — resume below.
                </p>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {assessments.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title="No assessments published yet"
          description="Your target career has no published assessments right now. Check back soon."
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {assessments.map((assessment) => {
            const latest = latestByAssessment[assessment.assessmentId] || null;
            const history = historyByAssessment[assessment.assessmentId] || [];
            const open = Boolean(historyOpen[assessment.assessmentId]);
            const chip = latestChip(latest);
            const isStarting = startingId === assessment.assessmentId;
            const resume = latest?.status === "IN_PROGRESS";
            const review = latest?.status === "SUBMITTED";
            const answered = latest?.answeredCount ?? 0;
            const totalQ = latest?.totalQuestions ?? assessment.questionCount ?? 0;
            const answerPct = totalQ > 0 ? Math.round((answered / totalQ) * 100) : 0;
            const bestOf = history.length > 0
              ? Math.max(...history.map((h) => h.overallScore ?? -1))
              : null;
            return (
              <Card
                key={assessment.assessmentId}
                className={cn(
                  "relative overflow-hidden shadow-card transition hover:-translate-y-0.5 hover:shadow-card-hover",
                  review ? "border-chart-3/30" : resume ? "border-primary/40 ring-1 ring-primary/20" : "border-border/60",
                )}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    "absolute inset-x-0 top-0 h-1",
                    review
                      ? "bg-gradient-to-r from-chart-3 via-chart-3/60 to-transparent"
                      : resume
                        ? "bg-gradient-to-r from-primary via-primary/60 to-transparent"
                        : "bg-gradient-to-r from-border via-border/60 to-transparent",
                  )}
                />
                <CardHeader>
                  <div className="flex items-start gap-3">
                    <span
                      className={cn(
                        "flex size-11 shrink-0 items-center justify-center rounded-2xl shadow-sm",
                        review
                          ? "bg-chart-3/15 text-chart-3"
                          : resume
                            ? "bg-primary text-primary-foreground"
                            : "bg-primary/10 text-primary",
                      )}
                    >
                      {review ? (
                        <Award className="size-5" aria-hidden="true" />
                      ) : resume ? (
                        <Play className="size-5" aria-hidden="true" />
                      ) : (
                        <ClipboardList className="size-5" aria-hidden="true" />
                      )}
                    </span>
                    <div className="min-w-0 flex-1">
                      <CardTitle className="text-base leading-snug">{assessment.title}</CardTitle>
                      <CardDescription className="mt-0.5 line-clamp-2 min-h-10">
                        {assessment.description || "No description."}
                      </CardDescription>
                    </div>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <Badge variant="secondary" className={`shrink-0 ${chip.className}`}>
                      {chip.label}
                    </Badge>
                    {review && bestOf != null && bestOf > 0 && (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 text-[11px] font-bold text-amber-700 dark:text-amber-400">
                        <Award className="size-3" aria-hidden="true" />
                        Best {bestOf}%
                      </span>
                    )}
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {review && latest?.overallScore != null && (
                    <div className="flex items-center gap-4 rounded-2xl border border-chart-3/25 bg-chart-3/[0.06] p-3.5">
                      <CircularProgress
                        value={latest.overallScore}
                        size={76}
                        strokeWidth={8}
                        tone={latest.overallScore >= 80 ? "green" : latest.overallScore >= 55 ? "primary" : "gold"}
                        label={`${assessment.title} latest score ${latest.overallScore} percent`}
                      >
                        <span className="tnum text-xl font-extrabold tracking-tight">{latest.overallScore}<span className="text-xs">%</span></span>
                      </CircularProgress>
                      <div className="min-w-0">
                        <p className="text-sm font-bold">
                          {latest.overallScore >= 80 ? "Excellent — interview ready." : latest.overallScore >= 55 ? "Strong base — close the top gap next." : "Good start — your roadmap targets the gaps."}
                        </p>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {latest?.submittedAt ? formatDateTime(latest.submittedAt) : ""}{latest?.answeredCount != null && latest?.totalQuestions ? ` · ${latest.answeredCount}/${latest.totalQuestions} answered` : ""}
                        </p>
                      </div>
                    </div>
                  )}
                  {resume && totalQ > 0 && (
                    <div>
                      <div className="mb-1 flex items-center justify-between text-xs">
                        <span className="font-semibold text-primary">Attempt in progress</span>
                        <span className="tnum font-semibold text-muted-foreground">{answered}/{totalQ} · {answerPct}%</span>
                      </div>
                      <Progress value={answerPct} tone="primary" className="h-2" aria-label={`Attempt progress ${answerPct} percent`} />
                    </div>
                  )}
                  <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-muted/70 px-2.5 py-1 font-medium">
                      <Clock3 className="size-3.5" aria-hidden="true" />
                      {assessment.durationMinutes} min · timed
                    </span>
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-muted/70 px-2.5 py-1 font-medium">
                      <ClipboardList className="size-3.5" aria-hidden="true" />
                      {assessment.questionCount} questions · reshuffled
                    </span>
                    {latest?.submittedAt && !review && (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-muted/70 px-2.5 py-1 font-medium">
                        <History className="size-3.5" aria-hidden="true" />
                        Last attempt {formatDateTime(latest.submittedAt)}
                      </span>
                    )}
                  </div>
                  {history.length > 1 && (
                    <div className="rounded-xl border border-border/50 bg-background/40">
                      <button
                        type="button"
                        onClick={() =>
                          setHistoryOpen((prev) => ({
                            ...prev,
                            [assessment.assessmentId]: !prev[assessment.assessmentId],
                          }))
                        }
                        aria-expanded={open}
                        className="flex w-full items-center gap-2 px-3 py-2 text-xs font-semibold text-muted-foreground transition hover:text-foreground"
                      >
                        <History className="size-3.5" aria-hidden="true" />
                        Attempt history ({history.length})
                        <span className="ml-auto" aria-hidden="true">{open ? "−" : "+"}</span>
                      </button>
                      {open && (
                        <ul className="space-y-1 px-3 pb-3">
                          {history.map((item) => (
                            <li
                              key={item.attemptId}
                              className="flex items-center gap-2 text-xs text-muted-foreground"
                            >
                              <span className="tnum font-semibold text-foreground">
                                Score {item.overallScore ?? "—"}
                              </span>
                              <span>{formatDateTime(item.submittedAt)}</span>
                              <button
                                type="button"
                                className="ml-auto font-semibold text-primary hover:underline"
                                onClick={() =>
                                  router.push(`/student/assessment/attempt/${item.attemptId}`)
                                }
                              >
                                Review
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                  <div className="flex gap-2">
                    {review && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="flex-1"
                        onClick={() =>
                          router.push(`/student/assessment/attempt/${latest.attemptId}`)
                        }
                      >
                        <CheckCircle2 className="size-3.5" aria-hidden="true" />
                        Review result
                      </Button>
                    )}
                    <Button
                      size="sm"
                      className="flex-1 bg-gradient-to-r from-brand-700 via-brand-600 to-brand-500 shadow-md shadow-brand-600/25"
                      disabled={isStarting}
                      onClick={() => handleStart(assessment.assessmentId, latest)}
                    >
                      {isStarting ? (
                        <>
                          <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                          Starting…
                        </>
                      ) : resume ? (
                        <>
                          <Play className="size-3.5" aria-hidden="true" />
                          Resume attempt
                        </>
                      ) : review || latest?.status === "EXPIRED" ? (
                        <>
                          <RotateCcw className="size-3.5" aria-hidden="true" />
                          Retake
                        </>
                      ) : (
                        <>
                          <Play className="size-3.5" aria-hidden="true" />
                          Start assessment
                        </>
                      )}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
