"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  FolderKanban,
  Hammer,
  Loader2,
  Play,
  RotateCcw,
  Target,
  Trophy,
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
import { prettifyEnum } from "@/lib/format";
import { cn } from "cn";

const STATUS_STYLES = {
  NOT_STARTED: "bg-muted text-muted-foreground",
  IN_PROGRESS: "bg-primary/10 text-primary",
  COMPLETED: "bg-chart-3/15 text-chart-3",
};

function statusLabel(status) {
  if (status === "COMPLETED") return "Completed";
  if (status === "IN_PROGRESS") return "In progress";
  return "To do";
}

export default function StudentProjectsPage() {
  const [projects, setProjects] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [pendingId, setPendingId] = useState(null);
  const [filter, setFilter] = useState("ALL");

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const data = await api.get("/projects/recommended");
      setProjects(data || null);
    } catch (err) {
      setLoadError(err?.message || "Failed to load recommended projects.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  async function setStatus(projectId, status) {
    setPendingId(projectId);
    try {
      await api.put(`/projects/${projectId}/status`, { status });
      await load();
      if (status === "COMPLETED") toast.success("Project completed — nice portfolio win!");
      else if (status === "IN_PROGRESS") toast.success("Project started.");
    } catch (err) {
      toast.error(err?.message || "Failed to update the project.");
    } finally {
      setPendingId(null);
    }
  }

  const allItems = useMemo(() => projects?.projects || [], [projects]);
  const total = projects?.totalProjects ?? allItems.length;
  const inProgress = projects?.inProgressProjects ?? allItems.filter((p) => p.status === "IN_PROGRESS").length;
  const completed = projects?.completedProjects ?? allItems.filter((p) => p.status === "COMPLETED").length;

  const items = useMemo(() => {
    if (filter === "ALL") return allItems;
    return allItems.filter((p) => p.status === filter);
  }, [allItems, filter]);

  if (loading) return <LoadingState label="Loading recommended projects…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Projects"
          description="Build proof of your skills."
          breadcrumb={<Breadcrumb items={[{ label: "Projects" }]} />}
        />
        <ErrorState title="Couldn't load projects" description={loadError} onRetry={load} />
      </div>
    );
  }

  if (!projects?.hasTarget) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Projects"
          description="Build proof of your skills."
          breadcrumb={<Breadcrumb items={[{ label: "Projects" }]} />}
        />
        <EmptyState
          icon={Target}
          title="Choose your target career first"
          description="Project recommendations are mapped to your target career and skill gaps. Select one to unlock them."
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

  const tabs = [
    { value: "ALL", label: `All (${total})` },
    { value: "NOT_STARTED", label: `To do (${total - inProgress - completed})` },
    { value: "IN_PROGRESS", label: `In progress (${inProgress})` },
    { value: "COMPLETED", label: `Completed (${completed})` },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        description={`Recommended builds for ${projects.careerName || "your career"} — matched to your skill gaps.`}
        breadcrumb={<Breadcrumb items={[{ label: "Projects" }]} />}
      />

      {/* Simple overview */}
      <div className="grid gap-4 sm:grid-cols-3">
        {[
          { icon: FolderKanban, label: "Recommended", value: total, tint: "bg-primary/10 text-primary" },
          { icon: Hammer, label: "In progress", value: inProgress, tint: "bg-chart-2/15 text-amber-700 dark:text-amber-400" },
          { icon: Trophy, label: "Completed", value: completed, tint: "bg-chart-3/15 text-chart-3" },
        ].map((stat) => (
          <Card key={stat.label} className="shadow-card">
            <CardContent className="flex items-center gap-3 py-5">
              <span className={cn("flex size-10 items-center justify-center rounded-xl", stat.tint)}>
                <stat.icon className="size-5" aria-hidden="true" />
              </span>
              <div>
                <p className="tnum text-2xl font-bold tracking-tight">{stat.value}</p>
                <p className="text-xs font-medium text-muted-foreground">{stat.label}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Simple filter */}
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter projects by status">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setFilter(tab.value)}
            aria-pressed={filter === tab.value}
            className={cn(
              "rounded-full px-3.5 py-1.5 text-xs font-semibold transition",
              filter === tab.value
                ? "bg-foreground text-background shadow-sm"
                : "bg-muted text-muted-foreground hover:text-foreground",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {allItems.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="No projects published yet"
          description="Your target career has no recommended projects right now. Check back soon."
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={FolderKanban}
          title="Nothing here yet"
          description="No projects with this status. Try another filter."
          action={
            <Button size="sm" variant="outline" onClick={() => setFilter("ALL")}>
              <RotateCcw className="size-3.5" aria-hidden="true" />
              Show all
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {items.map((project) => {
            const busy = pendingId === project.projectId;
            const skills = project.skills || [];
            return (
              <Card key={project.projectId} className="flex flex-col shadow-card transition hover:shadow-card-hover">
                <CardHeader className="pb-0">
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base leading-snug">{project.title}</CardTitle>
                    <Badge variant="secondary" className={cn("shrink-0", STATUS_STYLES[project.status] || "")}>
                      {statusLabel(project.status)}
                    </Badge>
                  </div>
                  <CardDescription className="leading-relaxed">{project.description}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-1 flex-col gap-4 pt-4">
                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <Badge variant="outline">{prettifyEnum(project.difficulty)}</Badge>
                    <span className="inline-flex items-center gap-1.5">
                      <Clock3 className="size-3.5" aria-hidden="true" />
                      ~{project.estimatedWeeks} week{project.estimatedWeeks === 1 ? "" : "s"}
                    </span>
                  </div>

                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      Skills covered
                    </p>
                    {skills.length === 0 ? (
                      <p className="mt-1.5 text-xs text-muted-foreground">General portfolio build.</p>
                    ) : (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {skills.map((skill) => (
                          <Badge
                            key={skill.skillId}
                            variant="outline"
                            className="font-normal"
                            title={
                              skill.assessed
                                ? `You: ${skill.scorePercent}% · Target: ${skill.targetPercent}%`
                                : `Target: ${skill.targetPercent ?? "—"}%`
                            }
                          >
                            {skill.skillName}
                            <span className="tnum text-muted-foreground">
                              {skill.assessed ? ` · ${skill.scorePercent}%` : ""}
                            </span>
                          </Badge>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="mt-auto flex flex-wrap gap-2 border-t border-border/60 pt-3.5">
                    {project.status === "NOT_STARTED" && (
                      <Button size="sm" className="flex-1" disabled={busy} onClick={() => setStatus(project.projectId, "IN_PROGRESS")}>
                        {busy ? (
                          <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                        ) : (
                          <Play className="size-3.5" aria-hidden="true" />
                        )}
                        Start project
                      </Button>
                    )}
                    {project.status === "IN_PROGRESS" && (
                      <>
                        <Button size="sm" className="flex-1" disabled={busy} onClick={() => setStatus(project.projectId, "COMPLETED")}>
                          {busy ? (
                            <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                          ) : (
                            <CheckCircle2 className="size-3.5" aria-hidden="true" />
                          )}
                          Mark complete
                        </Button>
                        <Button variant="outline" size="sm" disabled={busy} onClick={() => setStatus(project.projectId, "NOT_STARTED")}>
                          Reset
                        </Button>
                      </>
                    )}
                    {project.status === "COMPLETED" && (
                      <Button variant="outline" size="sm" className="flex-1" disabled={busy} onClick={() => setStatus(project.projectId, "IN_PROGRESS")}>
                        <RotateCcw className="size-3.5" aria-hidden="true" />
                        Reopen
                      </Button>
                    )}
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
