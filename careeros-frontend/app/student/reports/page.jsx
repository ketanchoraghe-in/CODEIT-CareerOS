"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Download, Eye, FileBarChart, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { formatDate, formatDateTime, prettifyEnum } from "@/lib/format";
import { cn } from "cn";

const JSON_ENDPOINT = {
  readiness: "/reports/readiness",
  assessment: "/reports/assessment",
  "gap-analysis": "/reports/gap-analysis",
  roadmap: "/reports/roadmap",
  overall: "/reports/overall",
};

function summarize(report, type) {
  if (!report) return "No data";
  try {
    if (type === "readiness" || type === "gap-analysis") {
      return `${report.readinessPercent ?? 0}% ready · ${report.metSkills ?? 0}/${report.totalSkills ?? 0} on target`;
    }
    if (type === "assessment") {
      return `${report.attempts?.length ?? 0} submission(s)${report.latestAttempt ? ` · latest ${report.latestAttempt.overallScore ?? "—"}` : ""}`;
    }
    if (type === "roadmap") {
      return `${report.completedItems ?? 0}/${report.totalItems ?? 0} steps · ${report.progressPercent ?? 0}%`;
    }
    if (type === "overall") {
      return report.readiness?.hasTarget ? `${report.readiness.readinessPercent}% ready · ${report.student?.fullName || ""}` : "No target career yet";
    }
  } catch {
    return "Available";
  }
  return "Available";
}

function ProgressBar({ value = 0 }) {
  const pct = Math.max(0, Math.min(100, Number(value) || 0));
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
      <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${pct}%` }} />
    </div>
  );
}

function SectionTitle({ children }) {
  return <p className="mt-4 mb-2 text-[13px] font-semibold text-foreground">{children}</p>;
}

function StatGrid({ items = [] }) {
  return (
    <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg border border-border/50 bg-muted/30 px-2.5 py-2">
          <p className="text-[11px] text-muted-foreground">{item.label}</p>
          <p className="text-sm font-semibold text-foreground">{item.value}</p>
        </div>
      ))}
    </div>
  );
}

function SkillGapTable({ gaps = [] }) {
  if (!gaps.length) return <p className="text-xs text-muted-foreground">No skills to show.</p>;
  return (
    <div className="mt-2 overflow-hidden rounded-lg border border-border/50">
      <div className="hidden grid-cols-[1fr_90px_90px_90px] gap-2 bg-muted/50 px-3 py-1.5 text-[11px] font-semibold text-muted-foreground sm:grid">
        <span>Skill</span>
        <span className="text-right">Score</span>
        <span className="text-right">Target</span>
        <span className="text-right">Status</span>
      </div>
      <ul className="divide-y divide-border/50">
        {gaps.map((gap, idx) => {
          const key = gap.skillId ?? gap.skillName ?? idx;
          const status = !gap.assessed ? "Not assessed" : gap.metTarget ? "On target" : `${gap.gapPercent ?? 0}% gap`;
          return (
            <li key={key} className="grid grid-cols-1 gap-1 px-3 py-2 sm:grid-cols-[1fr_90px_90px_90px] sm:items-center sm:gap-2">
              <div>
                <p className="text-xs font-medium text-foreground">{gap.skillName || "—"}</p>
                {gap.skillCategory && (
                  <p className="text-[11px] text-muted-foreground">{prettifyEnum(gap.skillCategory)}</p>
                )}
              </div>
              <span className="text-xs text-muted-foreground sm:text-right">
                {gap.assessed ? `${gap.scorePercent ?? 0}%` : "—"}
              </span>
              <span className="text-xs text-muted-foreground sm:text-right">{gap.targetPercent ?? 0}%</span>
              <span className="sm:text-right">
                <Badge variant="secondary" className={cn(gap.metTarget && "bg-chart-3/15 text-chart-3")}>
                  {status}
                </Badge>
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function ReadinessView({ data, gapMode = false }) {
  if (!data) return null;
  const gaps = gapMode ? [...(data.gaps || [])].sort((a, b) => (a.gapPercent ?? 0) - (b.gapPercent ?? 0)) : data.gaps || [];
  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold">{data.targetCareerName || "Target career"}</p>
        <Badge variant="secondary">{prettifyEnum(data.readinessLevel)}</Badge>
      </div>
      <div className="mt-2 flex items-center gap-3">
        <p className="text-2xl font-bold">{data.readinessPercent ?? 0}%</p>
        <div className="flex-1">
          <ProgressBar value={data.readinessPercent} />
        </div>
      </div>
      <StatGrid
        items={[
          { label: "On target", value: `${data.metSkills ?? 0}/${data.totalSkills ?? 0}` },
          { label: "Assessed", value: `${data.assessedSkills ?? 0}/${data.totalSkills ?? 0}` },
          { label: "Submissions", value: data.submittedAttempts ?? 0 },
          { label: "Strengths", value: (data.strengths || []).length },
        ]}
      />
      {!gapMode && (
        <>
          <SectionTitle>Strengths ({(data.strengths || []).length})</SectionTitle>
          {(data.strengths || []).length === 0 ? (
            <p className="text-xs text-muted-foreground">No met skills yet — your first wins will show up here.</p>
          ) : (
            <ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
              {(data.strengths || []).map((s) => (
                <li key={s.skillId ?? s.skillName}>
                  {s.skillName} — {s.scorePercent ?? 0}% (target {s.targetPercent ?? 0}%)
                </li>
              ))}
            </ul>
          )}
          <SectionTitle>Areas to improve ({(data.improvements || []).length})</SectionTitle>
          {(data.improvements || []).length === 0 ? (
            <p className="text-xs text-muted-foreground">Everything assessed is on target. Nice work.</p>
          ) : (
            <ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
              {(data.improvements || []).map((s) => (
                <li key={s.skillId ?? s.skillName}>
                  {s.skillName} — {s.assessed ? `${s.scorePercent ?? 0}% vs required ${s.targetPercent ?? 0}%` : `not assessed yet, required ${s.targetPercent ?? 0}%`}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      <SectionTitle>{gapMode ? "Required skills — current vs target" : "All skills"}</SectionTitle>
      <SkillGapTable gaps={gapMode ? gaps : data.gaps || []} />
    </div>
  );
}

function AssessmentView({ data }) {
  if (!data) return null;
  return (
    <div>
      <SectionTitle>Submissions ({(data.attempts || []).length})</SectionTitle>
      <ul className="space-y-2">
        {(data.attempts || []).map((attempt) => (
          <li key={attempt.attemptId} className="rounded-lg border border-border/50 bg-muted/30 px-3 py-2">
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs font-semibold text-foreground">{attempt.assessmentTitle || "Assessment"}</p>
              <Badge variant="secondary">Score {attempt.overallScore ?? "—"}</Badge>
            </div>
            <p className="mt-1 text-[11px] text-muted-foreground">
              {attempt.careerName || ""} · {attempt.answeredCount ?? 0}/{attempt.totalQuestions ?? 0} answered · {formatDateTime(attempt.submittedAt)}
            </p>
          </li>
        ))}
      </ul>
      <SectionTitle>Skill-wise scores (latest attempt)</SectionTitle>
      {(data.latestSkillScores || []).length === 0 ? (
        <p className="text-xs text-muted-foreground">No skill scores yet.</p>
      ) : (
        <SkillGapTable
          gaps={(data.latestSkillScores || []).map((s) => ({
            skillId: s.skillId,
            skillName: s.skillName,
            skillCategory: s.skillCategory,
            scorePercent: s.scorePercent,
            targetPercent: s.targetPercent,
            assessed: true,
            metTarget: (s.scorePercent ?? 0) >= (s.targetPercent ?? 0),
            gapPercent: Math.max(0, (s.targetPercent ?? 0) - (s.scorePercent ?? 0)),
          }))}
        />
      )}
    </div>
  );
}

function RoadmapView({ data }) {
  if (!data) return null;
  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold">{data.careerName || "Roadmap"}</p>
        <Badge variant="secondary">{data.progressPercent ?? 0}% done</Badge>
      </div>
      <div className="mt-2">
        <ProgressBar value={data.progressPercent} />
      </div>
      <StatGrid
        items={[
          { label: "Completed", value: `${data.completedItems ?? 0}/${data.totalItems ?? 0}` },
          { label: "In progress", value: data.inProgressItems ?? 0 },
          { label: "Phases", value: (data.phases || []).length },
        ]}
      />
      {(data.phases || []).map((phase) => (
        <div key={phase.phaseId ?? phase.title} className="mt-3 rounded-lg border border-border/50 p-2.5">
          <p className="text-xs font-semibold text-foreground">
            {phase.title} <span className="font-normal text-muted-foreground">({phase.completedItems ?? 0}/{phase.totalItems ?? 0})</span>
          </p>
          <ul className="mt-1.5 space-y-1">
            {(phase.items || []).map((item) => (
              <li key={item.itemId ?? item.title} className="flex items-start justify-between gap-2 text-xs">
                <span className="text-muted-foreground">• {item.title}</span>
                <Badge variant="secondary" className="shrink-0">{prettifyEnum(item.status)}</Badge>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

function OverallView({ data }) {
  if (!data) return null;
  const { student, readiness, roadmap, projects, cv, linkedIn, recommendedNextSteps } = data;
  return (
    <div>
      <p className="text-sm font-semibold">{student?.fullName || "Progress snapshot"}</p>
      <p className="text-[11px] text-muted-foreground">
        {student?.studentId || ""}{student?.targetCareerName ? ` · ${student.targetCareerName}` : ""}
      </p>
      <StatGrid
        items={[
          { label: "Readiness", value: readiness?.hasTarget ? `${readiness.readinessPercent ?? 0}%` : "—" },
          { label: "Roadmap", value: roadmap?.hasTarget ? `${roadmap.completedItems ?? 0}/${roadmap.totalItems ?? 0}` : "—" },
          { label: "Projects", value: `${projects?.completedProjects ?? 0}/${projects?.totalProjects ?? 0}` },
          { label: "CV score", value: cv?.hasCv ? `${cv.overallScore ?? 0}` : "No CV" },
        ]}
      />
      <SectionTitle>Career readiness</SectionTitle>
      <p className="text-xs text-muted-foreground">
        {readiness?.hasTarget
          ? `${readiness.readinessPercent ?? 0}% (${prettifyEnum(readiness.readinessLevel)}) · ${readiness.metSkills ?? 0} of ${readiness.totalSkills ?? 0} skills on target`
          : "No target career selected yet."}
      </p>
      {(readiness?.improvements || []).length > 0 && (
        <ul className="mt-1.5 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
          {(readiness.improvements || []).slice(0, 3).map((g) => (
            <li key={g.skillId ?? g.skillName}>Improve {g.skillName} ({g.assessed ? `${g.scorePercent}% vs ${g.targetPercent}%` : `required ${g.targetPercent}%`})</li>
          ))}
        </ul>
      )}
      <SectionTitle>LinkedIn</SectionTitle>
      <p className="text-xs text-muted-foreground">
        {linkedIn?.hasProfile
          ? `Completeness ${linkedIn.completenessPercent ?? 0}%, alignment ${linkedIn.alignmentPercent ?? 0}%`
          : "No LinkedIn profile connected yet."}
      </p>
      <SectionTitle>Recommended next steps</SectionTitle>
      {(recommendedNextSteps || []).length === 0 ? (
        <p className="text-xs text-muted-foreground">You are on track.</p>
      ) : (
        <ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
          {(recommendedNextSteps || []).map((step, idx) => (
            <li key={idx}>{step}</li>
          ))}
        </ul>
      )}
      <p className="mt-2 text-[11px] text-muted-foreground">Generated {formatDateTime(data.generatedAt)}</p>
    </div>
  );
}

function ReportDetailView({ type, data }) {
  if (type === "readiness") return <ReadinessView data={data} />;
  if (type === "gap-analysis") return <ReadinessView data={data} gapMode />;
  if (type === "assessment") return <AssessmentView data={data} />;
  if (type === "roadmap") return <RoadmapView data={data} />;
  if (type === "overall") return <OverallView data={data} />;
  return null;
}

export default function StudentReportsPage() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [viewing, setViewing] = useState(null);
  const [viewData, setViewData] = useState({});
  const [downloading, setDownloading] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const res = await api.get("/reports");
      setReports(Array.isArray(res) ? res : []);
    } catch (err) {
      setLoadError(err.message || "Failed to load reports.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async reports loader; state updates happen in promise callbacks
    load();
  }, [load]);

  async function handleView(type) {
    if (viewing === type) {
      setViewing(null);
      return;
    }
    setViewing(type);
    if (viewData[type]) return;
    try {
      const res = await api.get(JSON_ENDPOINT[type]);
      setViewData((prev) => ({ ...prev, [type]: res }));
    } catch (err) {
      toast.error(err.message || "Could not load this report.");
      setViewing(null);
    }
  }

  async function handleDownload(type) {
    setDownloading(type);
    try {
      const response = await api.get(`/reports/${type}/download`, { responseType: "blob" });
      const blob = response instanceof Blob ? response : new Blob([response], { type: "application/pdf" });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `careeros-${type}-report.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success("Report downloaded.");
    } catch (err) {
      toast.error(err.message || "Could not download this report.");
    } finally {
      setDownloading(null);
    }
  }

  if (loading) return <LoadingState title="Loading reports" description="Checking your live CareerOS data." />;
  if (loadError) return <ErrorState title="Couldn't load reports" description={loadError} onRetry={load} />;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reports"
        description="Career reports generated from your live data — view online or download a professional PDF."
        breadcrumb={<Breadcrumb items={[{ label: "Reports" }]} />}
        actions={<Badge variant="secondary">{reports.filter((r) => r.available).length} of {reports.length} available</Badge>}
      />

      {reports.length === 0 && (
        <EmptyState title="No reports yet" description="Reports appear once you have career data." />
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {reports.map((report) => (
          <Card key={report.type} className={cn("shadow-card", !report.available && "opacity-90")}>
            <CardHeader>
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-start gap-3">
                  <span className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                    <FileBarChart className="size-5" aria-hidden="true" />
                  </span>
                  <div>
                    <CardTitle className="text-base">{report.title}</CardTitle>
                    <CardDescription className="mt-0.5">{report.description}</CardDescription>
                  </div>
                </div>
                <Badge variant="secondary" className={report.available ? "bg-chart-3/15 text-chart-3" : ""}>
                  {report.available ? "Ready" : "Not ready"}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-xs text-muted-foreground">
                Generated {formatDateTime(report.generatedAt)} · {report.statusDetail || (report.available ? "Live data" : "Missing data — see detail")}
              </p>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="outline" disabled={!report.available} onClick={() => handleView(report.type)}>
                  <Eye className="size-3.5" aria-hidden="true" />
                  {viewing === report.type ? "Hide report" : "View report"}
                </Button>
                <Button size="sm" disabled={!report.available || downloading === report.type} onClick={() => handleDownload(report.type)}>
                  {downloading === report.type ? (
                    <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                  ) : (
                    <Download className="size-3.5" aria-hidden="true" />
                  )}
                  Download PDF
                </Button>
              </div>
              {viewing === report.type && viewData[report.type] && (
                <div className="rounded-xl border border-border/50 bg-background/40 p-3 text-xs leading-relaxed">
                  <p className="font-semibold text-sm">{summarize(viewData[report.type], report.type)}</p>
                  <p className="mt-0.5 text-[11px] text-muted-foreground">
                    {report.type === "gap-analysis"
                      ? "Largest gaps last — focus the red badges first."
                      : "Live data preview — download the PDF for the full formatted report."}{" "}
                    Generated {formatDate(viewData[report.type]?.generatedAt) !== "—" ? formatDate(viewData[report.type]?.generatedAt) : formatDateTime(report.generatedAt)}
                  </p>
                  <div className="mt-2 max-h-96 overflow-auto pr-1">
                    <ReportDetailView type={report.type} data={viewData[report.type]} />
                  </div>
                </div>
              )}
              {!report.available && (
                <p className="text-xs text-muted-foreground">{report.statusDetail}</p>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
