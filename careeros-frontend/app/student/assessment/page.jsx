"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowRight,
  CheckCircle2,
  ClipboardList,
  Clock3,
  History,
  Loader2,
  Play,
  RotateCcw,
  Target,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { formatDateTime } from "@/lib/format";

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

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assessment"
        description={`Assessments for your target career — ${profile.targetCareerName || "your career"}.`} 
        breadcrumb={<Breadcrumb items={[{ label: "Assessment" }]} />}
      />

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
            return (
              <Card key={assessment.assessmentId} className="shadow-card">
                <CardHeader>
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base leading-snug">{assessment.title}</CardTitle>
                    <Badge variant="secondary" className={`shrink-0 ${chip.className}`}>
                      {chip.label}
                    </Badge>
                  </div>
                  <CardDescription className="line-clamp-2 min-h-10">
                    {assessment.description || "No description."}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-1.5">
                      <Clock3 className="size-3.5" aria-hidden="true" />
                      {assessment.durationMinutes} min · enforced by the server
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                      <ClipboardList className="size-3.5" aria-hidden="true" />
                      {assessment.questionCount} questions · reshuffled every attempt
                    </span>
                    {latest?.submittedAt && (
                      <span className="inline-flex items-center gap-1.5">
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
                      className="flex-1"
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
