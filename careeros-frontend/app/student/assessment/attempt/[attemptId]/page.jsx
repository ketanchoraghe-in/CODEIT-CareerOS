"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import {
  ArrowLeft,
  CheckCircle2,
  CircleAlert,
  Clock3,
  Flag,
  Loader2,
  RotateCcw,
  TimerOff,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { Breadcrumb } from "@/components/breadcrumb";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { formatDateTime, prettifyEnum } from "@/lib/format";

const LEVEL_STYLES = {
  STRONG: "bg-chart-3/15 text-chart-3",
  GOOD: "bg-primary/10 text-primary",
  DEVELOPING: "bg-amber-500/15 text-amber-700 dark:text-amber-400",
  NEEDS_IMPROVEMENT: "bg-orange-500/15 text-orange-700 dark:text-orange-400",
  PRIORITY_GAP: "bg-destructive/10 text-destructive",
};

function deadlineOf(state) {
  if (!state) return null;
  if (state.expiresAt) return new Date(state.expiresAt).getTime();
  if (state.startedAt && state.durationMinutes) {
    return new Date(state.startedAt).getTime() + state.durationMinutes * 60000;
  }
  return null;
}

function formatRemaining(totalSeconds) {
  const clamped = Math.max(0, totalSeconds);
  const hours = Math.floor(clamped / 3600);
  const minutes = Math.floor((clamped % 3600) / 60);
  const seconds = clamped % 60;
  const mm = hours > 0 ? String(minutes).padStart(2, "0") : String(minutes);
  const ss = String(seconds).padStart(2, "0");
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

export default function AttemptRunnerPage() {
  const params = useParams();
  const router = useRouter();
  const attemptId = params?.attemptId;

  const [state, setState] = useState(null);
  const [selections, setSelections] = useState({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [pendingSaves, setPendingSaves] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [expired, setExpired] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(null);
  const autoSubmitted = useRef(false);
  const stateRef = useRef(null);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const data = await api.get(`/assessments/attempts/${attemptId}`);
      setState(data);
      if (data?.status === "IN_PROGRESS") {
        const initial = {};
        for (const question of data.questions || []) {
          if (question.selectedOptionId) initial[question.questionId] = question.selectedOptionId;
        }
        setSelections(initial);
        setExpired(false);
        const deadline = deadlineOf(data);
        setSecondsLeft(deadline ? Math.max(0, Math.floor((deadline - Date.now()) / 1000)) : null);
      } else if (data?.status === "EXPIRED") {
        setExpired(true);
      }
    } catch (err) {
      setLoadError(err?.message || "Failed to load the attempt.");
    } finally {
      setLoading(false);
    }
  }, [attemptId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    if (attemptId) load();
  }, [attemptId, load]);

  const inProgress = state?.status === "IN_PROGRESS" && !expired;
  const questions = inProgress ? state.questions || [] : [];
  const answeredCount = state?.answeredCount ?? 0;
  const totalQuestions = state?.totalQuestions ?? questions.length;
  const unanswered = Math.max(0, totalQuestions - answeredCount);

  async function doSubmit(auto = false) {
    const current = stateRef.current;
    if (!current || current.status !== "IN_PROGRESS" || submitting) return;
    if (auto) {
      if (autoSubmitted.current) return;
      autoSubmitted.current = true;
      toast.message("Time is up — submitting your attempt.");
    }
    setSubmitting(true);
    setConfirmOpen(false);
    try {
      const result = await api.post(`/assessments/attempts/${attemptId}/submit`);
      setState({ ...result, questions: null });
      if (auto) toast.success("Attempt submitted.");
      else toast.success(`Submitted! Overall score: ${result.overallScore ?? 0}`);
    } catch (err) {
      if (err?.status === 410 || err?.errorCode === "ATTEMPT_EXPIRED") {
        setExpired(true);
        toast.error("The time limit expired before submission.");
        await load();
      } else {
        toast.error(err?.message || "Failed to submit the attempt.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  useEffect(() => {
    if (!inProgress) return undefined;
    const deadline = deadlineOf(state);
    if (!deadline) return undefined;
    const id = setInterval(() => {
      const left = Math.max(0, Math.floor((deadline - Date.now()) / 1000));
      setSecondsLeft(left);
      if (left <= 0) {
        doSubmit(true);
      }
    }, 1000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inProgress, state?.attemptId, state?.expiresAt]);

  async function selectOption(questionId, optionId) {
    if (!inProgress || submitting) return;
    setSelections((prev) => ({ ...prev, [questionId]: optionId }));
    setPendingSaves((count) => count + 1);
    try {
      const progress = await api.put(`/assessments/attempts/${attemptId}/answers`, {
        questionId,
        optionId,
      });
      setState((prev) =>
        prev
          ? {
              ...prev,
              answeredCount: progress.answeredCount ?? prev.answeredCount,
              answeredQuestionIds: progress.answeredQuestionIds ?? prev.answeredQuestionIds,
            }
          : prev,
      );
    } catch (err) {
      if (err?.status === 410 || err?.errorCode === "ATTEMPT_EXPIRED") {
        setExpired(true);
        toast.error("The time limit expired. Your saved answers are kept, but this attempt is closed.");
        await load();
      } else {
        toast.error(err?.message || "Failed to save your answer.");
      }
    } finally {
      setPendingSaves((count) => Math.max(0, count - 1));
    }
  }

  if (loading) return <LoadingState label="Loading attempt…" />;

  if (loadError || !state) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Assessment attempt"
          breadcrumb={
            <Breadcrumb items={[{ label: "Assessment", href: "/student/assessment" }, { label: "Attempt" }]} />
          }
        />
        <ErrorState title="Couldn't load the attempt" description={loadError} onRetry={load} />
      </div>
    );
  }

  if (expired || state.status === "EXPIRED") {
    return (
      <div className="space-y-6">
        <PageHeader
          title={state.assessmentTitle || "Assessment attempt"}
          breadcrumb={
            <Breadcrumb items={[{ label: "Assessment", href: "/student/assessment" }, { label: "Expired" }]} />
          }
        />
        <Card className="border-destructive/40 shadow-card">
          <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
            <span className="flex size-14 items-center justify-center rounded-2xl bg-destructive/10 text-destructive">
              <TimerOff className="size-7" aria-hidden="true" />
            </span>
            <h2 className="text-lg font-semibold">Time expired</h2>
            <p className="max-w-md text-sm leading-relaxed text-muted-foreground">
              The {state.durationMinutes}-minute limit for this attempt elapsed before submission,
              so the server closed it. Start a fresh attempt to try again — your history is kept.
            </p>
            <div className="mt-2 flex gap-2">
              <Button variant="outline" size="sm" render={<Link href="/student/assessment" />}>
                <ArrowLeft className="size-3.5" aria-hidden="true" />
                Back to assessments
              </Button>
              <Button
                size="sm"
                onClick={async () => {
                  try {
                    const started = await api.post(`/assessments/${state.assessmentId}/start`);
                    router.push(`/student/assessment/attempt/${started.attemptId}`);
                  } catch (err) {
                    toast.error(err?.message || "Failed to start a new attempt.");
                  }
                }}
              >
                <RotateCcw className="size-3.5" aria-hidden="true" />
                Start new attempt
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  if (state.status === "SUBMITTED") {
    const skills = state.skills || [];
    return (
      <div className="space-y-6">
        <PageHeader
          title={state.assessmentTitle || "Assessment result"}
          description={`${state.careerName || ""} · submitted ${formatDateTime(state.submittedAt)}`}
          breadcrumb={
            <Breadcrumb items={[{ label: "Assessment", href: "/student/assessment" }, { label: "Result" }]} />
          }
          actions={
            <Button variant="outline" size="sm" render={<Link href="/student/assessment" />}>
              <ArrowLeft className="size-3.5" aria-hidden="true" />
              All assessments
            </Button>
          }
        />

        <Card className="border-primary/25 shadow-card">
          <CardContent className="flex flex-col items-center gap-2 py-8 text-center">
            <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Overall score
            </p>
            <p className="text-5xl font-bold tracking-tight text-primary">{state.overallScore ?? 0}</p>
            <p className="text-sm text-muted-foreground">
              {state.answeredCount} of {state.totalQuestions} questions answered · weighted by your
              career&apos;s competency framework
            </p>
          </CardContent>
        </Card>

        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="text-base">Skill-wise scores</CardTitle>
            <CardDescription>Your score against the target for each measured skill.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {skills.length === 0 ? (
              <p className="text-sm text-muted-foreground">No skill scores recorded.</p>
            ) : (
              skills.map((skill) => (
                <div key={skill.skillId}>
                  <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2 text-sm">
                    <span className="font-medium">{skill.skillName}</span>
                    <span className="flex items-center gap-2">
                      <Badge variant="secondary" className={LEVEL_STYLES[skill.level] || ""}>
                        {prettifyEnum(skill.level)}
                      </Badge>
                      <span className="tnum text-xs font-semibold text-muted-foreground">
                        {skill.correctCount}/{skill.questionCount} · {skill.scorePercent}% (target{" "}
                        {skill.targetPercent}%)
                      </span>
                    </span>
                  </div>
                  <div className="relative">
                    <Progress
                      value={skill.scorePercent}
                      tone="primary"
                      className="h-2.5"
                      aria-label={`${skill.skillName}: ${skill.scorePercent} percent, target ${skill.targetPercent} percent`}
                    />
                    <span
                      className="absolute top-[-3px] h-[14px] w-0.5 rounded bg-chart-3"
                      style={{ left: `${Math.min(100, skill.targetPercent ?? 0)}%` }}
                      title={`Target ${skill.targetPercent}%`}
                      aria-hidden="true"
                    />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <div className="flex flex-wrap justify-between gap-2">
          <Button
            variant="outline"
            size="sm"
            render={<Link href="/student/dashboard" />}
          >
            View career readiness
          </Button>
          <Button
            size="sm"
            onClick={async () => {
              try {
                const started = await api.post(`/assessments/${state.assessmentId}/start`);
                router.push(`/student/assessment/attempt/${started.attemptId}`);
                toast.message("New attempt started. Previous results are kept in history.");
              } catch (err) {
                toast.error(err?.message || "Failed to start a new attempt.");
              }
            }}
          >
            <RotateCcw className="size-3.5" aria-hidden="true" />
            Retake assessment
          </Button>
        </div>
      </div>
    );
  }

  const lowTime = secondsLeft !== null && secondsLeft <= 300;

  return (
    <div className="space-y-6">
      <PageHeader
        title={state.assessmentTitle || "Assessment attempt"}
        description={`${state.careerName || ""} · answers autosave as you select them.`}
        breadcrumb={
          <Breadcrumb items={[{ label: "Assessment", href: "/student/assessment" }, { label: "Attempt" }]} />
        }
      />

      <Card className="shadow-card">
        <CardContent className="flex flex-wrap items-center gap-x-6 gap-y-3 py-4">
          <span className="inline-flex items-center gap-2 text-sm font-semibold">
            <Clock3 className="size-4 text-primary" aria-hidden="true" />
            {secondsLeft === null ? (
              <span className="text-muted-foreground">Timer unavailable</span>
            ) : (
              <span className={lowTime ? "text-destructive" : ""} aria-live="polite">
                {formatRemaining(secondsLeft)} left
              </span>
            )}
          </span>
          <span className="text-sm text-muted-foreground">
            Answered <strong className="tnum text-foreground">{answeredCount}</strong> of{" "}
            <strong className="tnum text-foreground">{totalQuestions}</strong>
          </span>
          {pendingSaves > 0 && (
            <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
              <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
              Saving…
            </span>
          )}
          {pendingSaves === 0 && answeredCount > 0 && (
            <span className="inline-flex items-center gap-1.5 text-xs text-chart-3">
              <CheckCircle2 className="size-3.5" aria-hidden="true" />
              All answers saved
            </span>
          )}
          <span className="ml-auto">
            <Button
              size="sm"
              disabled={submitting || pendingSaves > 0}
              onClick={() => {
                if (unanswered > 0) setConfirmOpen(true);
                else doSubmit(false);
              }}
            >
              {submitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                  Submitting…
                </>
              ) : (
                <>
                  <Flag className="size-3.5" aria-hidden="true" />
                  Submit attempt
                </>
              )}
            </Button>
          </span>
        </CardContent>
      </Card>
      <div>
        <Progress
          value={totalQuestions > 0 ? (answeredCount / totalQuestions) * 100 : 0}
          tone="primary"
          className="h-2"
          aria-label={`Answered ${answeredCount} of ${totalQuestions} questions`}
        />
      </div>

      <div className="space-y-4">
        {questions.map((question, index) => {
          const selected = selections[question.questionId];
          return (
            <Card key={question.questionId} className="shadow-card">
              <CardContent className="space-y-3 pt-6">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <p className="min-w-0 flex-1 text-sm font-medium leading-relaxed">
                    <span className="mr-2 font-bold text-primary">{index + 1}.</span>
                    {question.questionText}
                  </p>
                  <div className="flex shrink-0 gap-2">
                    <Badge variant="outline">{question.skillName}</Badge>
                    <Badge variant="secondary">{prettifyEnum(question.difficulty)}</Badge>
                  </div>
                </div>
                <div className="grid gap-2" role="radiogroup" aria-label={`Question ${index + 1}`}>
                  {(question.options || []).map((option) => {
                    const active = selected === option.optionId;
                    return (
                      <button
                        key={option.optionId}
                        type="button"
                        role="radio"
                        aria-checked={active}
                        disabled={submitting}
                        onClick={() => selectOption(question.questionId, option.optionId)}
                        className={
                          active
                            ? "flex items-center gap-3 rounded-xl border border-primary bg-primary/10 px-4 py-3 text-left text-sm font-medium transition"
                            : "flex items-center gap-3 rounded-xl border border-border/60 bg-background/40 px-4 py-3 text-left text-sm transition hover:border-primary/40 hover:bg-accent/40 disabled:cursor-not-allowed"
                        }
                      >
                        <span
                          className={
                            active
                              ? "flex size-5 shrink-0 items-center justify-center rounded-full border-2 border-primary bg-primary text-primary-foreground"
                              : "size-5 shrink-0 rounded-full border-2 border-muted-foreground/40"
                          }
                          aria-hidden="true"
                        >
                          {active && <CheckCircle2 className="size-3.5" aria-hidden="true" />}
                        </span>
                        {option.optionText}
                      </button>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="sticky bottom-4 z-10 flex items-center justify-between gap-3 rounded-xl border border-border/60 bg-card/90 p-3 shadow-card backdrop-blur">
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
          {unanswered === 0
            ? "All questions answered — ready to submit."
            : `${unanswered} of ${totalQuestions} unanswered.`}
        </p>
        <Button size="sm" disabled={submitting || pendingSaves > 0} onClick={() => {
          if (unanswered > 0) setConfirmOpen(true);
          else doSubmit(false);
        }}>
          {submitting ? (
            <>
              <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
              Submitting…
            </>
          ) : (
            <>
              <Flag className="size-3.5" aria-hidden="true" />
              Submit attempt
            </>
          )}
        </Button>
      </div>

      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Submit with unanswered questions?"
        description={`${unanswered} of ${totalQuestions} questions have no answer. Unanswered questions score zero. You cannot change answers after submitting.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmOpen(false)} disabled={submitting}>
              Keep answering
            </Button>
            <Button onClick={() => doSubmit(false)} disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  Submitting…
                </>
              ) : (
                "Submit anyway"
              )}
            </Button>
          </>
        }
      />
    </div>
  );
}
