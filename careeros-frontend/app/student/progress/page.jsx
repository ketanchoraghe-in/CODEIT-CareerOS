"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  ArrowRight,
  ClipboardList,
  FileText,
  FolderKanban,
  Route,
  Share2,
  Target,
  TrendingUp,
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
import { formatDateTime, prettifyEnum } from "@/lib/format";

const ACTIVITY_LABEL = {
  ASSESSMENT: "Assessment",
  ROADMAP: "Roadmap",
  PROJECT: "Project",
  CV: "CV",
  LINKEDIN: "LinkedIn",
};

export default function StudentProgressPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const res = await api.get("/progress/me");
      setData(res || null);
    } catch (err) {
      setLoadError(err.message || "Failed to load your progress.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async progress loader; state updates happen in promise callbacks
    load();
  }, [load]);

  if (loading) return <LoadingState title="Loading your progress" description="Gathering readiness, skills, roadmap and more." />;
  if (loadError) return <ErrorState title="Couldn't load progress" description={loadError} onRetry={load} />;
  if (!data) return <EmptyState title="No progress yet" description="Choose a target career and take an assessment to start tracking." action={<Button render={<Link href="/student/careers" />}>Choose career</Button>} />;

  const readiness = data.readiness || {};
  const hasTarget = Boolean(readiness.hasTarget);
  const gaps = Array.isArray(readiness.gaps) ? readiness.gaps : [];
  const latest = data.latestAttempt || null;
  const previous = data.previousAttempt || null;
  const roadmap = data.roadmap || {};
  const projects = data.projects || {};
  const cv = data.cv || {};
  const linkedIn = data.linkedIn || {};
  const activity = Array.isArray(data.recentActivity) ? data.recentActivity : [];

  const delta =
    latest?.overallScore != null && previous?.overallScore != null
      ? latest.overallScore - previous.overallScore
      : null;

  const totalGaps = gaps.filter((g) => !g.metTarget).length;
  const improved = gaps.filter((g) => g.assessed && g.metTarget).length;
  const projectTotal = projects.totalProjects ?? 0;
  const projectPct = projectTotal > 0 ? Math.round(((projects.completedProjects ?? 0) / projectTotal) * 100) : 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="My Progress"
        description={hasTarget ? `Tracking your journey towards ${readiness.targetCareerName}.` : "Choose a target career to start tracking real progress."}
        breadcrumb={<Breadcrumb items={[{ label: "Progress" }]} />}
        actions={hasTarget ? <Badge variant="secondary">{prettifyEnum(readiness.readinessLevel)} · {readiness.readinessPercent}%</Badge> : null}
      />

      {!hasTarget && (
        <Card className="border-primary/25">
          <CardContent className="flex flex-wrap items-center gap-4 py-5">
            <span className="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <Target className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-52 flex-1">
              <p className="text-sm font-bold">No target career yet</p>
              <p className="mt-0.5 text-xs text-muted-foreground">Career selection unlocks assessments, gaps, roadmap and this dashboard.</p>
            </div>
            <Button size="sm" render={<Link href="/student/careers" />}>
              Choose career <ArrowRight className="size-3.5" aria-hidden="true" />
            </Button>
          </CardContent>
        </Card>
      )}

      {/* 1. Overall readiness */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card className="border-primary/25 shadow-card">
          <CardHeader>
            <CardTitle className="text-base">Overall Career Readiness</CardTitle>
            <CardDescription>{hasTarget ? readiness.targetCareerName : "Awaiting career selection"}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col items-center gap-2 text-center">
            <CircularProgress
              value={readiness.readinessPercent ?? 0}
              size={128}
              strokeWidth={11}
              tone={(readiness.readinessPercent ?? 0) >= 80 ? "green" : "primary"}
              label="Overall career readiness"
            >
              <span className="text-3xl font-bold tracking-tight text-primary">{readiness.readinessPercent ?? 0}</span>
              <span className="text-[11px] font-medium text-muted-foreground">ready</span>
            </CircularProgress>
            <p className="text-xs text-muted-foreground">
              {readiness.assessedSkills ?? 0} of {readiness.totalSkills ?? 0} skills assessed · {readiness.metSkills ?? 0} on target
            </p>
          </CardContent>
        </Card>

        {/* 3. Assessment progress */}
        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <ClipboardList className="size-4 text-primary" aria-hidden="true" /> Assessment Progress
            </CardTitle>
            <CardDescription>{data.completedAssessments ?? 0} assessment(s) completed</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            {latest ? (
              <>
                <p className="font-semibold">{latest.assessmentTitle}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(latest.submittedAt)} · Score {latest.overallScore ?? "—"} · {latest.answeredCount}/{latest.totalQuestions} answered
                </p>
                {previous && (
                  <p className="text-xs text-muted-foreground">
                    Previous: {previous.assessmentTitle} · Score {previous.overallScore ?? "—"} · {formatDateTime(previous.submittedAt)}
                  </p>
                )}
                {delta !== null && (
                  <Badge variant="secondary" className={delta >= 0 ? "bg-chart-3/15 text-chart-3" : "bg-amber-500/15 text-amber-700"}>
                    {delta >= 0 ? `+${delta} improvement` : `${delta} decline`}
                  </Badge>
                )}
                <div>
                  <Button variant="outline" size="sm" render={<Link href="/student/assessment" />}>View assessments</Button>
                </div>
              </>
            ) : (
              <p className="text-xs text-muted-foreground">No submitted assessment yet. Take your first role-specific assessment.</p>
            )}
          </CardContent>
        </Card>

        {/* 4. Gap summary */}
        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <TrendingUp className="size-4 text-primary" aria-hidden="true" /> Career Gap Progress
            </CardTitle>
            <CardDescription>Required vs current competency</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div className="flex items-center justify-between"><span>Total gaps</span><span className="font-bold">{totalGaps}</span></div>
            <div className="flex items-center justify-between"><span>On target</span><span className="font-bold text-chart-3">{improved}</span></div>
            <div className="flex items-center justify-between"><span>Remaining</span><span className="font-bold">{totalGaps}</span></div>
            {(readiness.strengths || []).length > 0 && (
              <p className="text-xs text-muted-foreground">Strongest: {(readiness.strengths || []).map((s) => s.skillName).join(", ")}</p>
            )}
            {(readiness.improvements || []).length > 0 && (
              <p className="text-xs text-muted-foreground">Priority: {(readiness.improvements || []).map((s) => s.skillName).join(", ")}</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 2. Skill progress */}
      <Card className="shadow-card">
        <CardHeader>
          <CardTitle className="text-base">Skill Progress</CardTitle>
          <CardDescription>Current score vs required target per skill.</CardDescription>
        </CardHeader>
        <CardContent>
          {gaps.length === 0 ? (
            <p className="text-sm text-muted-foreground">No framework skills found. Select a target career first.</p>
          ) : (
            <ul className="space-y-3.5">
              {[...gaps].sort((a, b) => (a.gapPercent ?? 0) - (b.gapPercent ?? 0)).map((skill) => {
                const pct = skill.assessed && skill.targetPercent ? Math.min(100, Math.round((skill.scorePercent / skill.targetPercent) * 100)) : 0;
                return (
                  <li key={skill.skillId}>
                    <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2 text-sm">
                      <span className="font-medium">{skill.skillName}
                        <span className="ml-2 text-xs text-muted-foreground">{prettifyEnum(skill.requiredLevel)} · weight {skill.weightPercent}%</span>
                      </span>
                      <span className="flex items-center gap-2">
                        <Badge variant="secondary" className={skill.metTarget ? "bg-chart-3/15 text-chart-3" : ""}>
                          {skill.assessed ? (skill.metTarget ? "On target" : `Gap ${skill.gapPercent}%`) : "Not assessed"}
                        </Badge>
                        <span className="tnum text-xs font-semibold text-muted-foreground">
                          {skill.assessed ? `${skill.scorePercent}% / ${skill.targetPercent}%` : `Target ${skill.targetPercent}%`}
                        </span>
                      </span>
                    </div>
                    <Progress value={skill.assessed ? pct : 0} tone={skill.metTarget ? "green" : "primary"} className="h-2"
                      aria-label={`${skill.skillName} progress ${pct} percent`} />
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>

      {/* 5+6. Roadmap + Projects */}
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base"><Route className="size-4 text-primary" aria-hidden="true" /> Learning / Roadmap</CardTitle>
            <CardDescription>{roadmap.completedItems ?? 0}/{roadmap.totalItems ?? 0} steps · {roadmap.progressPercent ?? 0}%</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Progress value={roadmap.progressPercent ?? 0} tone="primary" className="h-2" aria-label="Roadmap completion" />
            <p className="text-xs text-muted-foreground">{roadmap.inProgressItems ?? 0} in progress · {(roadmap.totalItems ?? 0) - (roadmap.completedItems ?? 0) - (roadmap.inProgressItems ?? 0)} pending</p>
            <Button variant="outline" size="sm" render={<Link href="/student/roadmap" />}>View roadmap <ArrowRight className="size-3.5" aria-hidden="true" /></Button>
          </CardContent>
        </Card>
        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base"><FolderKanban className="size-4 text-primary" aria-hidden="true" /> Projects</CardTitle>
            <CardDescription>{projects.completedProjects ?? 0}/{projects.totalProjects ?? 0} completed · {projectPct}%</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <Progress value={projectPct} tone="primary" className="h-2" aria-label="Project completion" />
            <p className="text-xs text-muted-foreground">{projects.inProgressProjects ?? 0} started · {(projectTotal ?? 0) - (projects.completedProjects ?? 0) - (projects.inProgressProjects ?? 0)} not started</p>
            <Button variant="outline" size="sm" render={<Link href="/student/projects" />}>View projects <ArrowRight className="size-3.5" aria-hidden="true" /></Button>
          </CardContent>
        </Card>
      </div>

      {/* 7+8. CV + LinkedIn */}
      <div className="grid gap-4 md:grid-cols-2">
        <Card className="shadow-card">
          <CardContent className="flex flex-wrap items-center gap-4 py-5">
            <span className="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary"><FileText className="size-5" aria-hidden="true" /></span>
            <div className="min-w-52 flex-1">
              <p className="text-sm font-bold">CV Progress</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {cv.hasCv ? `${cv.detectedSkills?.length ?? 0} skills detected · ${cv.matchedSkills?.length ?? 0} matched · ${cv.missingSkills?.length ?? 0} missing` : "No CV uploaded yet."}
              </p>
            </div>
            <Button variant={cv.hasCv ? "outline" : undefined} size="sm" render={<Link href="/student/cv" />}>{cv.hasCv ? "View analysis" : "Upload CV"}</Button>
          </CardContent>
        </Card>
        <Card className="shadow-card">
          <CardContent className="flex flex-wrap items-center gap-4 py-5">
            <span className="flex size-11 items-center justify-center rounded-2xl bg-primary/10 text-primary"><Share2 className="size-5" aria-hidden="true" /></span>
            <div className="min-w-52 flex-1">
              <p className="text-sm font-bold">LinkedIn Progress</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                {linkedIn.hasProfile ? `Completeness ${linkedIn.completenessPercent ?? 0}% · ${linkedIn.matchedSkills?.length ?? 0} matched · ${linkedIn.missingSkills?.length ?? 0} missing` : "No LinkedIn profile connected yet."}
              </p>
            </div>
            <Button variant={linkedIn.hasProfile ? "outline" : undefined} size="sm" render={<Link href="/student/linkedin" />}>{linkedIn.hasProfile ? "View analysis" : "Link profile"}</Button>
          </CardContent>
        </Card>
      </div>

      {/* 9. Activity timeline */}
      <Card className="shadow-card">
        <CardHeader>
          <CardTitle className="text-base">Recent Activity</CardTitle>
          <CardDescription>From your real CareerOS timestamps — nothing invented.</CardDescription>
        </CardHeader>
        <CardContent>
          {activity.length === 0 ? (
            <p className="text-sm text-muted-foreground">No activity recorded yet. Submit an assessment or update your roadmap to see it here.</p>
          ) : (
            <ol className="space-y-3">
              {activity.map((item, index) => (
                <li key={`${item.type}-${item.occurredAt}-${index}`} className="flex items-start gap-3 rounded-xl border border-border/50 bg-background/40 p-3">
                  <Badge variant="secondary" className="shrink-0">{ACTIVITY_LABEL[item.type] || item.type}</Badge>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold">{item.title}</p>
                    {item.detail && <p className="text-xs text-muted-foreground">{item.detail}</p>}
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{formatDateTime(item.occurredAt)}</p>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
