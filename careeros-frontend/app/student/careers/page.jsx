"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { CheckCircle2, Loader2, Search, Target } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Progress } from "@/components/ui/progress";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { prettifyEnum } from "@/lib/format";

export default function StudentCareersPage() {
  const [careers, setCareers] = useState([]);
  const [categories, setCategories] = useState([]);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("ALL");

  const [detailCareer, setDetailCareer] = useState(null);
  const [detailSkills, setDetailSkills] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [readiness, setReadiness] = useState(null);

  const [selectingId, setSelectingId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [careerData, profileData, catalog] = await Promise.all([
        api.get("/careers"),
        api.get("/students/me"),
        api.get("/meta/catalog"),
      ]);
      setCareers(Array.isArray(careerData) ? careerData : []);
      setProfile(profileData || null);
      setCategories(catalog?.careerCategories || []);
      if (profileData?.targetCareerId) {
        try {
          const readinessData = await api.get("/students/me/readiness");
          setReadiness(readinessData?.hasTarget ? readinessData : null);
        } catch {
          setReadiness(null);
        }
      } else {
        setReadiness(null);
      }
    } catch (err) {
      setLoadError(err?.message || "Failed to load the career catalog.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return careers.filter((career) => {
      if (categoryFilter !== "ALL" && career.category !== categoryFilter) return false;
      if (!term) return true;
      return (
        career.name?.toLowerCase().includes(term) ||
        career.description?.toLowerCase().includes(term)
      );
    });
  }, [careers, search, categoryFilter]);

  async function openDetail(career) {
    setDetailCareer(career);
    setDetailSkills([]);
    setDetailError("");
    setDetailLoading(true);
    try {
      const skills = await api.get(`/careers/${career.id}/skills`);
      setDetailSkills(Array.isArray(skills) ? skills : []);
    } catch (err) {
      setDetailError(err?.message || "Failed to load required skills.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function selectCareer(careerId) {
    setSelectingId(careerId);
    try {
      const updated = await api.put("/students/me/career", { careerId });
      setProfile(updated || null);
      try {
        const readinessData = await api.get("/students/me/readiness");
        setReadiness(readinessData?.hasTarget ? readinessData : null);
      } catch {
        setReadiness(null);
      }
      toast.success(
        updated?.targetCareerName
          ? `Target career set to "${updated.targetCareerName}".`
          : "Target career updated.",
      );
      setDetailCareer(null);
    } catch (err) {
      toast.error(err?.message || "Failed to set your target career.");
    } finally {
      setSelectingId(null);
    }
  }

  const gapBySkillId = useMemo(() => {
    const map = new Map();
    for (const gap of readiness?.gaps || []) {
      map.set(gap.skillId, gap);
    }
    return map;
  }, [readiness]);

  if (loading) return <LoadingState label="Loading career catalog…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Career Selection"
          description="Browse the catalog and lock in your target career."
          breadcrumb={<Breadcrumb items={[{ label: "Career Selection" }]} />}
        />
        <ErrorState title="Couldn't load careers" description={loadError} onRetry={load} />
      </div>
    );
  }

  const currentTargetId = profile?.targetCareerId ?? null;
  const currentTarget = careers.find((career) => career.id === currentTargetId) || null;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Career Selection"
        description="Browse the admin-curated career catalog and lock in your target career."
        breadcrumb={<Breadcrumb items={[{ label: "Career Selection" }]} />}
      />

      <Card className="border-primary/25 shadow-card">
        <CardHeader>
          <CardTitle className="flex items-center gap-2.5 text-base">
            <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Target className="size-4" aria-hidden="true" />
            </span>
            Your target career
          </CardTitle>
          <CardDescription>
            Career selection unlocks assessments, skill gaps and your roadmap.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {currentTarget ? (
            <div className="flex flex-wrap items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-lg font-bold text-primary">{currentTarget.name}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  {prettifyEnum(currentTarget.category)} · {prettifyEnum(currentTarget.difficultyLevel)} ·{" "}
                  {currentTarget.skillCount} required skills
                </p>
              </div>
              <Button variant="outline" size="sm" onClick={() => openDetail(currentTarget)}>
                View required skills
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              No target career selected yet. Pick one below to unlock your assessments.
            </p>
          )}
        </CardContent>
      </Card>

      <Card className="shadow-card">
        <CardContent className="flex flex-col gap-3 pt-6 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              name="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search careers…"
              className="h-10 pl-9"
              aria-label="Search careers"
            />
          </div>
          <Select
            id="student-career-category-filter"
            aria-label="Filter by category"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            options={[
              { value: "ALL", label: "All categories" },
              ...categories.map((category) => ({
                value: category,
                label: prettifyEnum(category),
              })),
            ]}
            className="sm:w-56"
          />
          <Badge variant="secondary" className="shrink-0 self-start sm:self-auto">
            {filtered.length} of {careers.length} shown
          </Badge>
        </CardContent>
      </Card>

      {filtered.length === 0 ? (
        <EmptyState
          icon={Target}
          title={careers.length === 0 ? "No published careers yet" : "No careers match your filters"}
          description={
            careers.length === 0
              ? "Check back soon — an admin publishes careers here."
              : "Try a different search term or category."
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((career) => {
            const isCurrent = currentTargetId === career.id;
            const isSelecting = selectingId === career.id;
            return (
              <Card key={career.id} className={isCurrent ? "border-primary/40 shadow-card" : "shadow-card"}>
                <CardHeader>
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base leading-snug">{career.name}</CardTitle>
                    {isCurrent && (
                      <Badge variant="secondary" className="shrink-0 bg-chart-3/15 text-chart-3">
                        <CheckCircle2 className="size-3" aria-hidden="true" />
                        Selected
                      </Badge>
                    )}
                  </div>
                  <CardDescription className="line-clamp-2 min-h-10">
                    {career.description || "No description."}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex flex-wrap gap-2">
                    <Badge variant="outline">{prettifyEnum(career.category)}</Badge>
                    <Badge variant="secondary">{prettifyEnum(career.difficultyLevel)}</Badge>
                    <Badge variant="secondary">{career.skillCount} skills</Badge>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" className="flex-1" onClick={() => openDetail(career)}>
                      View skills
                    </Button>
                    <Button
                      size="sm"
                      className="flex-1"
                      disabled={isCurrent || selectingId !== null}
                      onClick={() => selectCareer(career.id)}
                    >
                      {isSelecting ? (
                        <>
                          <Loader2 className="size-3.5 animate-spin" aria-hidden="true" />
                          Saving…
                        </>
                      ) : isCurrent ? (
                        "Current target"
                      ) : (
                        "Set as target"
                      )}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      <Dialog
        open={Boolean(detailCareer)}
        onOpenChange={(open) => {
          if (!open) setDetailCareer(null);
        }}
        title={detailCareer?.name || "Career"}
        description={
          detailCareer
            ? `${prettifyEnum(detailCareer.category)} · ${prettifyEnum(detailCareer.difficultyLevel)}`
            : undefined
        }
        className="max-w-xl"
      >
        <div className="mt-4 space-y-4">
          {detailCareer?.description && (
            <p className="text-sm leading-relaxed text-muted-foreground">{detailCareer.description}</p>
          )}
          <div>
            <p className="text-sm font-semibold">Required skills & weights</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              What the competency framework expects for this career.
            </p>
          </div>
          {detailLoading ? (
            <LoadingState label="Loading required skills…" />
          ) : detailError ? (
            <ErrorState
              title="Couldn't load required skills"
              description={detailError}
              onRetry={() => detailCareer && openDetail(detailCareer)}
            />
          ) : detailSkills.length === 0 ? (
            <EmptyState compact title="No skills mapped" description="This career has no competency framework yet." />
          ) : (
            <ul className="space-y-3">
              {detailSkills.map((skill) => {
                const gap = detailCareer && detailCareer.id === readiness?.targetCareerId
                  ? gapBySkillId.get(skill.skillId)
                  : null;
                return (
                  <li key={skill.skillId}>
                    <div className="mb-1.5 flex items-center justify-between gap-2 text-sm">
                      <span className="min-w-0">
                        <span className="truncate font-medium">{skill.skillName}</span>{" "}
                        <span className="text-xs text-muted-foreground">
                          · {prettifyEnum(skill.requiredLevel)} · target {skill.targetPercent}%
                        </span>
                      </span>
                      <span className="tnum shrink-0 text-xs font-semibold text-muted-foreground">
                        {skill.weightPercent}%
                      </span>
                    </div>
                    <Progress value={skill.weightPercent} tone="primary" className="h-2" aria-label={`${skill.skillName}: ${skill.weightPercent} percent weight`} />
                    {gap && (
                      <p className="mt-1 text-xs text-muted-foreground">
                        {gap.assessed ? (
                          <>
                            Your score: <strong className="tnum text-foreground">{gap.scorePercent}%</strong>{" "}
                            {gap.metTarget ? (
                              <span className="font-semibold text-chart-3">· on target</span>
                            ) : (
                              <span className="font-semibold text-destructive">· gap {gap.gapPercent}%</span>
                            )}
                          </>
                        ) : (
                          "Not assessed yet — submit an assessment to measure this skill."
                        )}
                      </p>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
          {detailCareer && currentTargetId !== detailCareer.id && (
            <div className="flex justify-end">
              <Button
                onClick={() => selectCareer(detailCareer.id)}
                disabled={selectingId !== null}
              >
                {selectingId === detailCareer.id ? (
                  <>
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                    Saving…
                  </>
                ) : (
                  "Set as my target career"
                )}
              </Button>
            </div>
          )}
        </div>
      </Dialog>
    </div>
  );
}
