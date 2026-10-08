"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Clock, FolderKanban, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { prettifyEnum } from "@/lib/format";
import { cn } from "cn";

export default function AdminProjectsPage() {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [search, setSearch] = useState("");
  const [careerFilter, setCareerFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const data = await api.get("/admin/projects");
      setProjects(Array.isArray(data) ? data : []);
    } catch (err) {
      setLoadError(err?.message || "Failed to load projects.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  const careerOptions = useMemo(() => {
    const names = [...new Set(projects.map((p) => p.careerName).filter(Boolean))].sort();
    return names;
  }, [projects]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return projects.filter((p) => {
      if (careerFilter !== "ALL" && p.careerName !== careerFilter) return false;
      if (statusFilter === "PUBLISHED" && !p.published) return false;
      if (statusFilter === "DRAFT" && p.published) return false;
      if (!term) return true;
      return [p.title, p.description, p.careerName].filter(Boolean).some((v) => String(v).toLowerCase().includes(term));
    });
  }, [projects, search, careerFilter, statusFilter]);

  const publishedCount = projects.filter((p) => p.published).length;

  if (loading) return <LoadingState label="Loading project catalog…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Projects"
          description="The project catalog recommended against student skill gaps. Read-only."
          breadcrumb={<Breadcrumb items={[{ label: "Projects" }]} />}
        />
        <ErrorState title="Couldn't load projects" description={loadError} onRetry={load} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        description={
          projects.length > 0
            ? `${projects.length} projects in the catalog · ${publishedCount} published to students. Read-only.`
            : "The project catalog recommended against student skill gaps."
        }
        breadcrumb={<Breadcrumb items={[{ label: "Projects" }]} />}
      />

      <Card className="shadow-card">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-2 lg:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by title, description, career…"
                aria-label="Search projects"
                className="pl-9"
              />
            </div>
            <Select value={careerFilter} onChange={(e) => setCareerFilter(e.target.value)} aria-label="Filter by career" className="lg:w-52" options={[{ value: "ALL", label: "All careers" }, ...careerOptions.map((name) => ({ value: name, label: name }))]} />
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Filter by status" className="lg:w-44" options={[{ value: "ALL", label: "Published + drafts" }, { value: "PUBLISHED", label: "Published only" }, { value: "DRAFT", label: "Drafts only" }]} />
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              icon={FolderKanban}
              title={projects.length === 0 ? "No projects yet" : "No matches"}
              description={
                projects.length === 0
                  ? "Projects appear here once they are added to the catalog."
                  : "Try a different search term or filter."
              }
            />
          ) : (
            <ul className="grid gap-3 md:grid-cols-2">
              {filtered.map((p) => (
                <li
                  key={p.id}
                  className="rounded-2xl border border-border/60 bg-background/40 p-4 transition hover:border-primary/40 hover:shadow-card-hover"
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="min-w-0 text-sm font-bold leading-snug">{p.title}</p>
                    <Badge
                      variant="secondary"
                      className={cn("shrink-0", p.published ? "bg-chart-3/15 text-chart-3" : "bg-amber-500/15 text-amber-700 dark:text-amber-400")}
                    >
                      {p.published ? "Published" : "Draft"}
                    </Badge>
                  </div>
                  {p.description && (
                    <p className="mt-1.5 line-clamp-2 text-xs leading-relaxed text-muted-foreground">{p.description}</p>
                  )}
                  <div className="mt-2.5 flex flex-wrap items-center gap-1.5 text-[11px]">
                    {p.careerName && (
                      <Badge variant="secondary" className="bg-primary/10 text-primary">
                        {p.careerName}
                      </Badge>
                    )}
                    {p.difficulty && <Badge variant="secondary">{prettifyEnum(p.difficulty)}</Badge>}
                    <span className="inline-flex items-center gap-1 font-semibold text-muted-foreground">
                      <Clock className="size-3" aria-hidden="true" />~{p.estimatedWeeks} wk
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
