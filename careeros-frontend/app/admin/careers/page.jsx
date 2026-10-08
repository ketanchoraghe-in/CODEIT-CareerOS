"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  Briefcase,
  ClipboardList,
  Loader2,
  Pencil,
  Plus,
  Search,
  Trash2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Field } from "@/components/field";
import { Breadcrumb } from "@/components/breadcrumb";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { enumOptions, mapFieldErrors, prettifyEnum } from "@/lib/format";

const emptyCareerForm = {
  name: "",
  description: "",
  category: "",
  difficultyLevel: "",
  published: false,
};

function newFrameworkRow() {
  return {
    key: `row-${Date.now()}-${Math.floor(Math.random() * 100000)}`,
    skillId: "",
    weightPercent: "",
    requiredLevel: "",
    targetPercent: "80",
  };
}

function toFrameworkRows(skills = []) {
  return skills.map((mapping) => ({
    key: `row-${mapping.skillId}`,
    skillId: String(mapping.skillId),
    weightPercent: String(mapping.weightPercent ?? ""),
    requiredLevel: mapping.requiredLevel || "",
    targetPercent: String(mapping.targetPercent ?? 80),
  }));
}

function frameworkTotal(rows) {
  return rows.reduce((sum, row) => sum + (Number.parseInt(row.weightPercent, 10) || 0), 0);
}

export default function AdminCareersPage() {
  const [careers, setCareers] = useState([]);
  const [activeSkills, setActiveSkills] = useState([]);
  const [careerCategories, setCareerCategories] = useState([]);
  const [difficultyLevels, setDifficultyLevels] = useState([]);
  const [skillLevels, setSkillLevels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("ALL");
  const [publishedFilter, setPublishedFilter] = useState("ALL");

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyCareerForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const [frameworkOpen, setFrameworkOpen] = useState(false);
  const [frameworkCareer, setFrameworkCareer] = useState(null);
  const [frameworkLoading, setFrameworkLoading] = useState(false);
  const [frameworkRows, setFrameworkRows] = useState([]);
  const [frameworkError, setFrameworkError] = useState("");
  const [frameworkSaving, setFrameworkSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [careerData, skillData, catalog] = await Promise.all([
        api.get("/admin/careers"),
        api.get("/admin/skills"),
        api.get("/meta/catalog"),
      ]);
      setCareers(Array.isArray(careerData) ? careerData : []);
      setActiveSkills(
        Array.isArray(skillData) ? skillData.filter((skill) => skill.active) : [],
      );
      setCareerCategories(catalog?.careerCategories || []);
      setDifficultyLevels(catalog?.difficultyLevels || []);
      setSkillLevels(catalog?.skillLevels || []);
    } catch (err) {
      setLoadError(err?.message || "Failed to load careers.");
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
      if (publishedFilter === "PUBLISHED" && !career.published) return false;
      if (publishedFilter === "DRAFT" && career.published) return false;
      if (!term) return true;
      return (
        career.name?.toLowerCase().includes(term) ||
        career.description?.toLowerCase().includes(term)
      );
    });
  }, [careers, search, categoryFilter, publishedFilter]);

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyCareerForm });
    setFieldErrors({});
    setDialogOpen(true);
  }

  function openEdit(career) {
    setEditing(career);
    setForm({
      name: career.name || "",
      description: career.description || "",
      category: career.category || "",
      difficultyLevel: career.difficultyLevel || "",
      published: Boolean(career.published),
    });
    setFieldErrors({});
    setDialogOpen(true);
  }

  function handleCareerChange(name, value) {
    setForm((prev) => ({ ...prev, [name]: value }));
    if (fieldErrors[name]) setFieldErrors((prev) => ({ ...prev, [name]: undefined }));
  }

  function validateCareer() {
    const errors = {};
    if (!form.name.trim()) errors.name = "Career name is required";
    else if (form.name.trim().length > 140) errors.name = "Career name must be at most 140 characters";
    if (form.description && form.description.length > 1000) {
      errors.description = "Description must be at most 1000 characters";
    }
    if (!form.category) errors.category = "Category is required";
    if (!form.difficultyLevel) errors.difficultyLevel = "Difficulty level is required";
    return errors;
  }

  async function handleSaveCareer(event) {
    event.preventDefault();
    const errors = validateCareer();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      toast.error("Please fix the highlighted fields.");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        category: form.category,
        difficultyLevel: form.difficultyLevel,
        published: form.published,
      };
      let saved;
      if (editing) {
        saved = await api.put(`/admin/careers/${editing.id}`, payload);
        toast.success(`Career "${payload.name}" updated.`);
      } else {
        saved = await api.post("/admin/careers", payload);
        toast.success(`Career "${payload.name}" created. Add its competency framework next.`);
      }
      setDialogOpen(false);
      await load();
      if (!editing && saved?.id) {
        openFramework({ id: saved.id, name: payload.name });
      }
    } catch (err) {
      const mapped = mapFieldErrors(err);
      if (Object.keys(mapped).length > 0) setFieldErrors(mapped);
      toast.error(err?.message || "Failed to save the career.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.delete(`/admin/careers/${deleteTarget.id}`);
      toast.success(`Career "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      await load();
    } catch (err) {
      toast.error(err?.message || "Failed to delete the career.");
    } finally {
      setDeleting(false);
    }
  }

  async function openFramework(career) {
    setFrameworkCareer(career);
    setFrameworkRows([]);
    setFrameworkError("");
    setFrameworkOpen(true);
    setFrameworkLoading(true);
    try {
      const detail = await api.get(`/admin/careers/${career.id}`);
      setFrameworkRows(toFrameworkRows(detail?.skills || []));
    } catch (err) {
      setFrameworkError(err?.message || "Failed to load the competency framework.");
    } finally {
      setFrameworkLoading(false);
    }
  }

  function updateRow(key, field, value) {
    setFrameworkRows((prev) =>
      prev.map((row) => (row.key === key ? { ...row, [field]: value } : row)),
    );
    if (frameworkError) setFrameworkError("");
  }

  function addRow() {
    setFrameworkRows((prev) => [...prev, newFrameworkRow()]);
  }

  function removeRow(key) {
    setFrameworkRows((prev) => prev.filter((row) => row.key !== key));
  }

  function validateFramework() {
    if (frameworkRows.length === 0) return "Add at least one skill mapping.";
    const seen = new Set();
    for (const row of frameworkRows) {
      if (!row.skillId) return "Every row must select a skill.";
      if (seen.has(row.skillId)) return "A skill can only be mapped once per career.";
      seen.add(row.skillId);
      const weight = Number.parseInt(row.weightPercent, 10);
      if (!Number.isInteger(weight) || weight < 1 || weight > 100) {
        return "Each weight must be a whole number between 1 and 100.";
      }
      if (!row.requiredLevel) return "Every row must select a required level.";
      if (row.targetPercent !== "") {
        const target = Number.parseInt(row.targetPercent, 10);
        if (!Number.isInteger(target) || target < 1 || target > 100) {
          return "Each target must be between 1 and 100.";
        }
      }
    }
    const total = frameworkTotal(frameworkRows);
    if (total !== 100) return `Skill weights must total exactly 100% (currently ${total}%).`;
    return "";
  }

  async function handleSaveFramework() {
    const problem = validateFramework();
    if (problem) {
      setFrameworkError(problem);
      return;
    }
    setFrameworkSaving(true);
    try {
      const payload = frameworkRows.map((row) => ({
        skillId: Number(row.skillId),
        weightPercent: Number.parseInt(row.weightPercent, 10),
        requiredLevel: row.requiredLevel,
        targetPercent:
          row.targetPercent === "" ? 80 : Number.parseInt(row.targetPercent, 10),
      }));
      await api.put(`/admin/careers/${frameworkCareer.id}/skills`, payload);
      toast.success(`Competency framework updated for "${frameworkCareer.name}".`);
      setFrameworkOpen(false);
      await load();
    } catch (err) {
      setFrameworkError(err?.message || "Failed to save the competency framework.");
    } finally {
      setFrameworkSaving(false);
    }
  }

  const skillNameById = useMemo(() => {
    const map = new Map();
    for (const skill of activeSkills) map.set(String(skill.id), skill.name);
    return map;
  }, [activeSkills]);

  if (loading) return <LoadingState label="Loading careers…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Careers"
          description="Configure the career master that powers assessments and roadmaps."
          breadcrumb={<Breadcrumb items={[{ label: "Careers" }]} />}
        />
        <ErrorState title="Couldn't load careers" description={loadError} onRetry={load} />
      </div>
    );
  }

  const total = frameworkTotal(frameworkRows);
  const frameworkProblem = frameworkRows.length > 0 ? validateFramework() : "";

  return (
    <div className="space-y-6">
      <PageHeader
        title="Careers"
        description="Configure the career master that powers assessments, gaps and roadmaps."
        breadcrumb={<Breadcrumb items={[{ label: "Careers" }]} />}
        actions={
          <Button size="sm" onClick={openCreate}>
            <Plus className="size-4" aria-hidden="true" />
            Add career
          </Button>
        }
      />

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
              placeholder="Search careers by name or description…"
              className="h-10 pl-9"
              aria-label="Search careers"
            />
          </div>
          <Select
            id="career-category-filter"
            aria-label="Filter by category"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            options={[
              { value: "ALL", label: "All categories" },
              ...careerCategories.map((category) => ({
                value: category,
                label: prettifyEnum(category),
              })),
            ]}
            className="sm:w-56"
          />
          <Select
            id="career-published-filter"
            aria-label="Filter by publish state"
            value={publishedFilter}
            onChange={(e) => setPublishedFilter(e.target.value)}
            options={[
              { value: "ALL", label: "Published + drafts" },
              { value: "PUBLISHED", label: "Published only" },
              { value: "DRAFT", label: "Drafts only" },
            ]}
            className="sm:w-48"
          />
          <Badge variant="secondary" className="shrink-0 self-start sm:self-auto">
            {filtered.length} of {careers.length} shown
          </Badge>
        </CardContent>
      </Card>

      {filtered.length === 0 ? (
        <EmptyState
          icon={Briefcase}
          title={careers.length === 0 ? "No careers yet" : "No careers match your filters"}
          description={
            careers.length === 0
              ? "Create the first career, then attach its competency framework."
              : "Try a different search term or filter."
          }
          action={
            careers.length === 0 ? (
              <Button size="sm" onClick={openCreate}>
                <Plus className="size-4" aria-hidden="true" />
                Add career
              </Button>
            ) : null
          }
        />
      ) : (
        <Card className="shadow-card">
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border/60 text-left text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="px-5 py-3 font-semibold">Career</th>
                    <th className="px-5 py-3 font-semibold">Category</th>
                    <th className="px-5 py-3 font-semibold">Difficulty</th>
                    <th className="px-5 py-3 font-semibold">Skills</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                    <th className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((career) => (
                    <tr
                      key={career.id}
                      className="border-b border-border/40 transition last:border-0 hover:bg-accent/40"
                    >
                      <td className="px-5 py-3">
                        <p className="font-medium">{career.name}</p>
                        {career.description && (
                          <p className="mt-0.5 max-w-md truncate text-xs text-muted-foreground">
                            {career.description}
                          </p>
                        )}
                      </td>
                      <td className="px-5 py-3">
                        <Badge variant="outline">{prettifyEnum(career.category)}</Badge>
                      </td>
                      <td className="px-5 py-3 whitespace-nowrap text-muted-foreground">
                        {prettifyEnum(career.difficultyLevel)}
                      </td>
                      <td className="tnum px-5 py-3">{career.skillCount}</td>
                      <td className="px-5 py-3">
                        {career.published ? (
                          <Badge variant="secondary" className="bg-chart-3/15 text-chart-3">
                            Published
                          </Badge>
                        ) : (
                          <Badge variant="secondary">Draft</Badge>
                        )}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          <Button variant="outline" size="sm" onClick={() => openFramework(career)}>
                            <ClipboardList className="size-3.5" aria-hidden="true" />
                            Framework
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => openEdit(career)}>
                            <Pencil className="size-3.5" aria-hidden="true" />
                            Edit
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => setDeleteTarget(career)}
                          >
                            <Trash2 className="size-3.5" aria-hidden="true" />
                            Delete
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      <Dialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        title={editing ? `Edit career — ${editing.name}` : "Add career"}
        description="Careers become visible to students only when published."
      >
        <form onSubmit={handleSaveCareer} noValidate className="mt-4 space-y-4">
          <Field id="career-name" label="Career name" required error={fieldErrors.name}>
            {(inputId) => (
              <Input
                id={inputId}
                name="name"
                value={form.name}
                onChange={(e) => handleCareerChange("name", e.target.value)}
                placeholder="e.g. Java Developer"
                className="h-10 px-3"
                maxLength={140}
                aria-invalid={Boolean(fieldErrors.name)}
              />
            )}
          </Field>
          <Field
            id="career-description"
            label="Description"
            error={fieldErrors.description}
            hint="Optional, up to 1000 characters."
          >
            {(inputId) => (
              <Input
                id={inputId}
                name="description"
                value={form.description}
                onChange={(e) => handleCareerChange("description", e.target.value)}
                placeholder="What this career is about…"
                className="h-10 px-3"
                aria-invalid={Boolean(fieldErrors.description)}
              />
            )}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              id="career-category"
              label="Category"
              required
              value={form.category}
              onChange={(e) => handleCareerChange("category", e.target.value)}
              options={enumOptions(careerCategories, "Select category")}
              error={fieldErrors.category}
            />
            <Select
              id="career-difficulty"
              label="Difficulty level"
              required
              value={form.difficultyLevel}
              onChange={(e) => handleCareerChange("difficultyLevel", e.target.value)}
              options={enumOptions(difficultyLevels, "Select difficulty")}
              error={fieldErrors.difficultyLevel}
            />
          </div>
          <label className="flex cursor-pointer items-center gap-2.5 text-sm font-medium">
            <input
              type="checkbox"
              checked={form.published}
              onChange={(e) => handleCareerChange("published", e.target.checked)}
              className="size-4 accent-primary"
            />
            Published (visible to students)
          </label>
          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? (
                <>
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  Saving…
                </>
              ) : editing ? (
                "Save changes"
              ) : (
                "Create career"
              )}
            </Button>
          </div>
        </form>
      </Dialog>

      <Dialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        title={deleteTarget ? `Delete "${deleteTarget.name}"?` : "Delete career"}
        description="Careers used by assessments or targeted by students cannot be deleted."
        footer={
          <>
            <Button variant="outline" onClick={() => setDeleteTarget(null)} disabled={deleting}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? (
                <>
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  Deleting…
                </>
              ) : (
                "Delete career"
              )}
            </Button>
          </>
        }
      />

      <Dialog
        open={frameworkOpen}
        onOpenChange={setFrameworkOpen}
        title={frameworkCareer ? `Competency framework — ${frameworkCareer.name}` : "Competency framework"}
        description="Decide how much each skill matters for this career. Weights must total exactly 100%."
        className="max-w-2xl"
      >
        <div className="mt-4 space-y-4">
          {frameworkLoading ? (
            <LoadingState label="Loading framework…" />
          ) : (
            <>
              {frameworkRows.map((row) => (
                <div
                  key={row.key}
                  className="grid gap-3 rounded-xl border border-border/60 bg-background/40 p-3 sm:grid-cols-[1fr_84px_140px_84px_auto] sm:items-end"
                >
                  <Select
                    id={`fw-skill-${row.key}`}
                    label="Skill"
                    value={row.skillId}
                    onChange={(e) => updateRow(row.key, "skillId", e.target.value)}
                    options={[
                      { value: "", label: "Select skill", disabled: true },
                      ...activeSkills.map((skill) => ({
                        value: String(skill.id),
                        label: `${skill.name} (${prettifyEnum(skill.category)})`,
                      })),
                    ]}
                  />
                  <Field id={`fw-weight-${row.key}`} label="Weight %">
                    {(inputId) => (
                      <Input
                        id={inputId}
                        name={`weight-${row.key}`}
                        inputMode="numeric"
                        value={row.weightPercent}
                        onChange={(e) => updateRow(row.key, "weightPercent", e.target.value)}
                        placeholder="0"
                        className="h-10 px-3"
                      />
                    )}
                  </Field>
                  <Select
                    id={`fw-level-${row.key}`}
                    label="Required level"
                    value={row.requiredLevel}
                    onChange={(e) => updateRow(row.key, "requiredLevel", e.target.value)}
                    options={enumOptions(skillLevels, "Select level")}
                  />
                  <Field id={`fw-target-${row.key}`} label="Target %">
                    {(inputId) => (
                      <Input
                        id={inputId}
                        name={`target-${row.key}`}
                        inputMode="numeric"
                        value={row.targetPercent}
                        onChange={(e) => updateRow(row.key, "targetPercent", e.target.value)}
                        placeholder="80"
                        className="h-10 px-3"
                      />
                    )}
                  </Field>
                  <Button
                    type="button"
                    variant="destructive"
                    size="icon-sm"
                    onClick={() => removeRow(row.key)}
                    aria-label={`Remove ${skillNameById.get(row.skillId) || "skill"} mapping`}
                    title="Remove mapping"
                  >
                    <Trash2 className="size-4" aria-hidden="true" />
                  </Button>
                </div>
              ))}

              <div className="flex flex-wrap items-center justify-between gap-2">
                <Button type="button" variant="outline" size="sm" onClick={addRow}>
                  <Plus className="size-4" aria-hidden="true" />
                  Add skill
                </Button>
                <Badge variant={total === 100 ? "secondary" : "destructive"} className={total === 100 ? "bg-chart-3/15 text-chart-3" : ""}>
                  Total: {total}%
                </Badge>
              </div>

              {frameworkError && (
                <p className="text-sm text-destructive" role="alert">
                  {frameworkError}
                </p>
              )}

              <div className="flex justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setFrameworkOpen(false)}
                  disabled={frameworkSaving}
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  onClick={handleSaveFramework}
                  disabled={frameworkSaving || frameworkRows.length === 0 || Boolean(frameworkProblem)}
                >
                  {frameworkSaving ? (
                    <>
                      <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                      Saving…
                    </>
                  ) : (
                    "Save framework"
                  )}
                </Button>
              </div>
              {frameworkProblem && frameworkRows.length > 0 && (
                <p className="text-right text-xs text-muted-foreground">{frameworkProblem}</p>
              )}
              <p className="text-xs text-muted-foreground">
                Already-submitted attempts keep their score snapshots; the new framework
                applies to future submissions.
              </p>
            </>
          )}
        </div>
      </Dialog>
    </div>
  );
}
