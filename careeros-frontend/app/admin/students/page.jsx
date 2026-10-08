"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Briefcase,
  GraduationCap,
  Mail,
  Search,
  UserRound,
  Users,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import api from "@/lib/api";
import { formatDateTime } from "@/lib/format";

const PAGE_SIZE = 12;

function initialsOf(student) {
  const source = student?.fullName || student?.email || "?";
  return source
    .split(/[\s@._]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function DetailRow({ label, value, href }) {
  if (!value) return null;
  return (
    <div className="flex items-start justify-between gap-3 rounded-xl border border-border/50 bg-background/40 px-3 py-2 text-sm">
      <dt className="shrink-0 text-[11px] font-bold uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-right font-medium">
        {href ? (
          <a href={href} target="_blank" rel="noreferrer" className="text-primary hover:underline">
            {value}
          </a>
        ) : (
          <span className="break-words">{value}</span>
        )}
      </dd>
    </div>
  );
}

export default function AdminStudentsPage() {
  const [students, setStudents] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [search, setSearch] = useState("");
  const [careerFilter, setCareerFilter] = useState("ALL");
  const [selected, setSelected] = useState(null);

  const load = useCallback(async (pageToLoad = 0) => {
    setLoading(true);
    setLoadError("");
    try {
      const [listData, statsData] = await Promise.all([
        api.get("/admin/students", { params: { page: pageToLoad, size: PAGE_SIZE } }),
        api.get("/admin/stats").catch(() => null),
      ]);
      setStudents(Array.isArray(listData?.content) ? listData.content : []);
      setPage(listData?.page ?? pageToLoad);
      setTotalPages(listData?.totalPages ?? 0);
      setTotalElements(listData?.totalElements ?? 0);
      setStats(statsData);
    } catch (err) {
      setLoadError(err?.message || "Failed to load students.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load(0);
  }, [load]);

  function gotoPage(next) {
    const clamped = Math.max(0, Math.min(totalPages - 1, next));
    load(clamped);
  }

  const careerOptions = useMemo(() => {
    const names = new Map();
    for (const s of students) {
      if (s.targetCareerName) names.set(s.targetCareerName, (names.get(s.targetCareerName) || 0) + 1);
    }
    return [...names.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [students]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return students.filter((s) => {
      if (careerFilter !== "ALL" && s.targetCareerName !== careerFilter) return false;
      if (!term) return true;
      return [s.fullName, s.email, s.studentId, s.college, s.branch, s.mobile]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(term));
    });
  }, [students, search, careerFilter]);

  if (loading && students.length === 0) return <LoadingState label="Loading students…" />;

  if (loadError && students.length === 0) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Students"
          description="Search, inspect and manage every student account on the platform."
          breadcrumb={<Breadcrumb items={[{ label: "Students" }]} />}
        />
        <ErrorState title="Couldn't load students" description={loadError} onRetry={() => load(page)} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Students"
        description={
          totalElements > 0
            ? `${totalElements} registered student${totalElements === 1 ? "" : "s"} on the platform.`
            : "Search, inspect and manage every student account on the platform."
        }
        breadcrumb={<Breadcrumb items={[{ label: "Students" }]} />}
      />

      {stats && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard icon={Users} label="Students" value={stats.students ?? totalElements} sub="Registered accounts" tone="primary" />
          <StatCard icon={Briefcase} label="Careers" value={stats.careers ?? "—"} sub="Career tracks" tone="gold" />
          <StatCard icon={GraduationCap} label="Assessments done" value={stats.assessmentsCompleted ?? "—"} sub="Submitted attempts" tone="green" />
          <StatCard icon={UserRound} label="Questions" value={stats.questions ?? "—"} sub="In the bank" tone="muted" />
        </div>
      )}

      <Card className="shadow-card">
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-col gap-2 sm:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search this page by name, email, ID, college…"
                aria-label="Search students on this page"
                className="pl-9"
              />
            </div>
            <Select
              value={careerFilter}
              onChange={(e) => setCareerFilter(e.target.value)}
              aria-label="Filter by target career"
              className="sm:w-56"
              options={[
                { value: "ALL", label: "All careers" },
                ...careerOptions.map(([name, count]) => ({ value: name, label: `${name} (${count})` })),
              ]}
            />
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              icon={Users}
              title={students.length === 0 ? "No students yet" : "No matches on this page"}
              description={
                students.length === 0
                  ? "Student accounts appear here once someone registers."
                  : "Try a different search term, career filter, or another page."
              }
            />
          ) : (
            <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {filtered.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() => setSelected(s)}
                    className="flex w-full items-center gap-3 rounded-2xl border border-border/60 bg-background/40 p-3.5 text-left transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-card-hover"
                  >
                    <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-sm font-bold text-primary">
                      {initialsOf(s)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-bold">{s.fullName || "Unnamed student"}</span>
                      <span className="mt-0.5 flex items-center gap-1 truncate text-xs text-muted-foreground">
                        <Mail className="size-3 shrink-0" aria-hidden="true" />
                        {s.email}
                      </span>
                      <span className="mt-1 flex flex-wrap items-center gap-1.5">
                        <Badge variant="secondary" className="tnum">
                          {s.studentId || `#${s.id}`}
                        </Badge>
                        {s.targetCareerName ? (
                          <Badge variant="secondary" className="bg-primary/10 text-primary">
                            {s.targetCareerName}
                          </Badge>
                        ) : (
                          <Badge variant="secondary">No career yet</Badge>
                        )}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-between gap-2 pt-1">
              <p className="tnum text-xs text-muted-foreground">
                Page {page + 1} of {totalPages} · {totalElements} total
              </p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page <= 0 || loading} onClick={() => gotoPage(page - 1)}>
                  <ArrowLeft className="size-3.5" aria-hidden="true" />
                  Prev
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1 || loading}
                  onClick={() => gotoPage(page + 1)}
                >
                  Next
                  <ArrowRight className="size-3.5" aria-hidden="true" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
        title={selected?.fullName || "Student details"}
        description={selected ? `${selected.studentId || ""} · joined ${formatDateTime(selected.createdAt)}` : undefined}
        className="max-w-lg"
        footer={
          <Button variant="outline" size="sm" onClick={() => setSelected(null)}>
            Close
          </Button>
        }
      >
        {selected && (
          <dl className="mt-4 grid gap-2">
            <DetailRow label="Email" value={selected.email} />
            <DetailRow label="Mobile" value={selected.mobile} />
            <DetailRow label="College" value={selected.college} />
            <DetailRow label="Degree" value={selected.degree} />
            <DetailRow label="Branch" value={selected.branch} />
            <DetailRow
              label="Graduation"
              value={
                [selected.graduationYear ? `Class of ${selected.graduationYear}` : null, selected.semester ? `Sem ${selected.semester}` : null]
                  .filter(Boolean)
                  .join(" · ") || null
              }
            />
            <DetailRow label="Location" value={selected.location} />
            <DetailRow label="Target career" value={selected.targetCareerName} />
            <DetailRow label="GitHub" value={selected.githubUrl ? "Open profile" : null} href={selected.githubUrl} />
            <DetailRow label="LinkedIn" value={selected.linkedinUrl ? "Open profile" : null} href={selected.linkedinUrl} />
            <DetailRow label="Portfolio" value={selected.portfolioUrl ? "Open site" : null} href={selected.portfolioUrl} />
          </dl>
        )}
      </Dialog>

      {loadError && students.length > 0 && (
        <p role="alert" className="text-xs font-semibold text-destructive">
          Refresh failed: {loadError}{" "}
          <button type="button" className="underline" onClick={() => load(page)}>
            Retry
          </button>
        </p>
      )}
    </div>
  );
}
