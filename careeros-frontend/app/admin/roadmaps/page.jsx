"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronDown, Clock, ListChecks, Route } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { cn } from "cn";

export default function AdminRoadmapsPage() {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [selectedId, setSelectedId] = useState(null);
  const [openPhases, setOpenPhases] = useState({});

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const data = await api.get("/admin/roadmaps");
      const list = Array.isArray(data) ? data : [];
      setTemplates(list);
      setSelectedId((current) => current ?? list[0]?.careerId ?? null);
    } catch (err) {
      setLoadError(err?.message || "Failed to load roadmap templates.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  const selected = useMemo(
    () => templates.find((t) => t.careerId === selectedId) || null,
    [templates, selectedId],
  );

  function togglePhase(phaseId) {
    setOpenPhases((prev) => ({ ...prev, [phaseId]: !prev[phaseId] }));
  }

  if (loading) return <LoadingState label="Loading roadmap templates…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Roadmaps"
          description="Browse the 30/60/90-day roadmap templates students follow."
          breadcrumb={<Breadcrumb items={[{ label: "Roadmaps" }]} />}
        />
        <ErrorState title="Couldn't load roadmaps" description={loadError} onRetry={load} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Roadmaps"
        description={
          templates.length > 0
            ? `${templates.length} career template${templates.length === 1 ? "" : "s"} — pick one to inspect its phases and items. Read-only.`
            : "Browse the 30/60/90-day roadmap templates students follow."
        }
        breadcrumb={<Breadcrumb items={[{ label: "Roadmaps" }]} />}
      />

      {templates.length === 0 ? (
        <EmptyState
          icon={Route}
          title="No roadmap templates yet"
          description="Templates appear here once careers have roadmap phases. Manage careers under Content → Careers."
        />
      ) : (
        <div className="grid items-start gap-4 lg:grid-cols-[280px_1fr]">
          <Card className="shadow-card">
            <CardContent className="space-y-1 p-2">
              {templates.map((t) => {
                const active = t.careerId === selectedId;
                return (
                  <button
                    key={t.careerId}
                    type="button"
                    onClick={() => setSelectedId(t.careerId)}
                    className={cn(
                      "w-full rounded-xl border px-3 py-2.5 text-left transition",
                      active
                        ? "border-primary/40 bg-primary/5 shadow-sm"
                        : "border-transparent hover:border-border/60 hover:bg-muted/60",
                    )}
                  >
                    <span className="flex items-center gap-1.5 text-sm font-bold">
                      {active && <span className="size-1.5 shrink-0 rounded-full bg-primary" aria-hidden="true" />}
                      <span className="truncate">{t.careerName}</span>
                    </span>
                    <span className="tnum mt-1 block pl-3 text-[11px] text-muted-foreground">
                      {t.phaseCount} phases · {t.itemCount} items · {t.totalEstimatedHours}h
                    </span>
                  </button>
                );
              })}
            </CardContent>
          </Card>

          <Card className="shadow-card">
            <CardContent className="space-y-3 p-4">
              {!selected ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Select a career template.</p>
              ) : (
                <>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-base font-bold">{selected.careerName}</h2>
                    <Badge variant="secondary" className={selected.careerPublished ? "bg-chart-3/15 text-chart-3" : ""}>
                      {selected.careerPublished ? "Published" : "Draft"}
                    </Badge>
                    <span className="tnum ml-auto text-xs text-muted-foreground">
                      {selected.phaseCount} phases · {selected.itemCount} items · {selected.totalEstimatedHours}h total
                    </span>
                  </div>
                  {selected.phases.length === 0 ? (
                    <p className="rounded-xl border border-dashed border-border py-5 text-center text-sm text-muted-foreground">
                      No phases in this template yet.
                    </p>
                  ) : (
                    <ol className="space-y-2">
                      {selected.phases.map((phase, index) => {
                        const open = openPhases[phase.id] ?? index === 0;
                        return (
                          <li key={phase.id} className="overflow-hidden rounded-2xl border border-border/60">
                            <button
                              type="button"
                              onClick={() => togglePhase(phase.id)}
                              aria-expanded={open}
                              className="flex w-full items-center gap-3 bg-muted/40 px-4 py-3 text-left transition hover:bg-muted/70"
                            >
                              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground">
                                {index + 1}
                              </span>
                              <span className="min-w-0 flex-1">
                                <span className="block truncate text-sm font-bold">{phase.title}</span>
                                <span className="tnum mt-0.5 block text-[11px] text-muted-foreground">
                                  {phase.items.length} items · {phase.durationDays} days
                                </span>
                              </span>
                              <ChevronDown
                                className={cn("size-4 shrink-0 text-muted-foreground transition-transform", open && "rotate-180")}
                                aria-hidden="true"
                              />
                            </button>
                            {open && (
                              <ul className="space-y-1.5 p-3">
                                {phase.items.length === 0 ? (
                                  <li className="text-xs text-muted-foreground">No items in this phase.</li>
                                ) : (
                                  phase.items.map((item) => (
                                    <li
                                      key={item.id}
                                      className="flex items-start gap-2.5 rounded-xl bg-background/60 px-3 py-2 text-sm"
                                    >
                                      <ListChecks className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden="true" />
                                      <span className="min-w-0 flex-1">
                                        <span className="block font-semibold leading-snug">{item.title}</span>
                                        {item.learningGoal && (
                                          <span className="mt-0.5 block text-xs text-muted-foreground">{item.learningGoal}</span>
                                        )}
                                        <span className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px]">
                                          {item.skillName && (
                                            <Badge variant="secondary" className="bg-primary/10 text-primary">
                                              {item.skillName}
                                            </Badge>
                                          )}
                                          <span className="inline-flex items-center gap-1 text-muted-foreground">
                                            <Clock className="size-3" aria-hidden="true" />
                                            {item.estimatedHours}h
                                          </span>
                                        </span>
                                      </span>
                                    </li>
                                  ))
                                )}
                              </ul>
                            )}
                          </li>
                        );
                      })}
                    </ol>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
