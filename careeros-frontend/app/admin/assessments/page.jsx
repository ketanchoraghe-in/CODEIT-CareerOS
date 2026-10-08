"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  ClipboardList,
  ListOrdered,
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
import { formatDateTime, mapFieldErrors } from "@/lib/format";

const emptyForm = {
  careerId: "",
  title: "",
  description: "",
  durationMinutes: "30",
  published: false,
};

export default function AdminAssessmentsPage() {
  const [assessments, setAssessments] = useState([]);
  const [careers, setCareers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [search, setSearch] = useState("");
  const [careerFilter, setCareerFilter] = useState("ALL");
  const [publishedFilter, setPublishedFilter] = useState("ALL");

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [assessmentData, careerData] = await Promise.all([
        api.get("/admin/assessments"),
        api.get("/admin/careers"),
      ]);
      setAssessments(Array.isArray(assessmentData) ? assessmentData : []);
      setCareers(Array.isArray(careerData) ? careerData : []);
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

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return assessments.filter((assessment) => {
      if (careerFilter !== "ALL" && String(assessment.careerId) !== careerFilter) return false;
      if (publishedFilter === "PUBLISHED" && !assessment.published) return false;
      if (publishedFilter === "DRAFT" && assessment.published) return false;
      if (!term) return true;
      return (
        assessment.title?.toLowerCase().includes(term) ||
        assessment.careerName?.toLowerCase().includes(term)
      );
    });
  }, [assessments, search, careerFilter, publishedFilter]);

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyForm });
    setFieldErrors({});
    setDialogOpen(true);
  }

  function openEdit(assessment) {
    setEditing(assessment);
    setForm({
      careerId: String(assessment.careerId ?? ""),
      title: assessment.title || "",
      description: assessment.description || "",
      durationMinutes: String(assessment.durationMinutes ?? 30),
      published: Boolean(assessment.published),
    });
    setFieldErrors({});
    setDialogOpen(true);
  }

  function handleChange(name, value) {
    setForm((prev) => ({ ...prev, [name]: value }));
    if (fieldErrors[name]) setFieldErrors((prev) => ({ ...prev, [name]: undefined }));
  }

  function validate() {
    const errors = {};
    if (!form.careerId) errors.careerId = "Career is required";
    if (!form.title.trim()) errors.title = "Title is required";
    else if (form.title.trim().length > 150) errors.title = "Title must be at most 150 characters";
    if (form.description && form.description.length > 500) {
      errors.description = "Description must be at most 500 characters";
    }
    const duration = Number.parseInt(form.durationMinutes, 10);
    if (!Number.isInteger(duration) || duration < 5 || duration > 180) {
      errors.durationMinutes = "Duration must be between 5 and 180 minutes";
    }
    return errors;
  }

  async function handleSave(event) {
    event.preventDefault();
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      toast.error("Please fix the highlighted fields.");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        careerId: Number(form.careerId),
        title: form.title.trim(),
        description: form.description.trim() || null,
        durationMinutes: Number.parseInt(form.durationMinutes, 10),
        published: form.published,
      };
      if (editing) {
        await api.put(`/admin/assessments/${editing.id}`, payload);
        toast.success(`Assessment "${payload.title}" updated.`);
      } else {
        await api.post("/admin/assessments", payload);
        toast.success(`Assessment "${payload.title}" created. Add questions next.`);
      }
      setDialogOpen(false);
      await load();
    } catch (err) {
      const mapped = mapFieldErrors(err);
      if (Object.keys(mapped).length > 0) setFieldErrors(mapped);
      toast.error(err?.message || "Failed to save the assessment.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.delete(`/admin/assessments/${deleteTarget.id}`);
      toast.success(`Assessment "${deleteTarget.title}" deleted.`);
      setDeleteTarget(null);
      await load();
    } catch (err) {
      toast.error(err?.message || "Failed to delete the assessment.");
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <LoadingState label="Loading assessments…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Assessments"
          description="Build role-specific assessments from the question bank."
          breadcrumb={<Breadcrumb items={[{ label: "Assessments" }]} />}
        />
        <ErrorState title="Couldn't load assessments" description={loadError} onRetry={load} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assessments"
        description="Build role-specific assessments from the question bank."
        breadcrumb={<Breadcrumb items={[{ label: "Assessments" }]} />}
        actions={
          <Button size="sm" onClick={openCreate}>
            <Plus className="size-4" aria-hidden="true" />
            Add assessment
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
              placeholder="Search assessments by title or career…"
              className="h-10 pl-9"
              aria-label="Search assessments"
            />
          </div>
          <Select
            id="assessment-career-filter"
            aria-label="Filter by career"
            value={careerFilter}
            onChange={(e) => setCareerFilter(e.target.value)}
            options={[
              { value: "ALL", label: "All careers" },
              ...careers.map((career) => ({ value: String(career.id), label: career.name })),
            ]}
            className="sm:w-56"
          />
          <Select
            id="assessment-published-filter"
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
            {filtered.length} of {assessments.length} shown
          </Badge>
        </CardContent>
      </Card>

      {filtered.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title={assessments.length === 0 ? "No assessments yet" : "No assessments match your filters"}
          description={
            assessments.length === 0
              ? "Create the first assessment, then add questions to it."
              : "Try a different search term or filter."
          }
          action={
            assessments.length === 0 ? (
              <Button size="sm" onClick={openCreate}>
                <Plus className="size-4" aria-hidden="true" />
                Add assessment
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
                    <th className="px-5 py-3 font-semibold">Assessment</th>
                    <th className="px-5 py-3 font-semibold">Career</th>
                    <th className="px-5 py-3 font-semibold">Duration</th>
                    <th className="px-5 py-3 font-semibold">Questions</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                    <th className="px-5 py-3 font-semibold">Created</th>
                    <th className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((assessment) => (
                    <tr
                      key={assessment.id}
                      className="border-b border-border/40 transition last:border-0 hover:bg-accent/40"
                    >
                      <td className="px-5 py-3">
                        <p className="font-medium">{assessment.title}</p>
                        {assessment.description && (
                          <p className="mt-0.5 max-w-md truncate text-xs text-muted-foreground">
                            {assessment.description}
                          </p>
                        )}
                      </td>
                      <td className="px-5 py-3 whitespace-nowrap">{assessment.careerName}</td>
                      <td className="tnum px-5 py-3 whitespace-nowrap text-muted-foreground">
                        {assessment.durationMinutes} min
                      </td>
                      <td className="tnum px-5 py-3">{assessment.questionCount}</td>
                      <td className="px-5 py-3">
                        {assessment.published ? (
                          <Badge variant="secondary" className="bg-chart-3/15 text-chart-3">
                            Published
                          </Badge>
                        ) : (
                          <Badge variant="secondary">Draft</Badge>
                        )}
                      </td>
                      <td className="px-5 py-3 whitespace-nowrap text-muted-foreground">
                        {formatDateTime(assessment.createdAt)}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          <Button size="sm" render={<Link href={`/admin/assessments/${assessment.id}`} />}>
                            <ListOrdered className="size-3.5" aria-hidden="true" />
                            Questions
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => openEdit(assessment)}>
                            <Pencil className="size-3.5" aria-hidden="true" />
                            Edit
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => setDeleteTarget(assessment)}
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
        title={editing ? `Edit assessment — ${editing.title}` : "Add assessment"}
        description="Assessments become available to students only when published."
      >
        <form onSubmit={handleSave} noValidate className="mt-4 space-y-4">
          <Select
            id="assessment-career"
            label="Career"
            required
            value={form.careerId}
            onChange={(e) => handleChange("careerId", e.target.value)}
            options={[
              { value: "", label: "Select career", disabled: true },
              ...careers.map((career) => ({ value: String(career.id), label: career.name })),
            ]}
            error={fieldErrors.careerId}
          />
          <Field id="assessment-title" label="Title" required error={fieldErrors.title}>
            {(inputId) => (
              <Input
                id={inputId}
                name="title"
                value={form.title}
                onChange={(e) => handleChange("title", e.target.value)}
                placeholder="e.g. Java & Spring Boot Skills Assessment"
                className="h-10 px-3"
                maxLength={150}
                aria-invalid={Boolean(fieldErrors.title)}
              />
            )}
          </Field>
          <Field
            id="assessment-description"
            label="Description"
            error={fieldErrors.description}
            hint="Optional, up to 500 characters."
          >
            {(inputId) => (
              <Input
                id={inputId}
                name="description"
                value={form.description}
                onChange={(e) => handleChange("description", e.target.value)}
                placeholder="What this assessment covers…"
                className="h-10 px-3"
                aria-invalid={Boolean(fieldErrors.description)}
              />
            )}
          </Field>
          <Field
            id="assessment-duration"
            label="Duration (minutes)"
            required
            error={fieldErrors.durationMinutes}
            hint="5 to 180 minutes. The server enforces this limit on submission."
          >
            {(inputId) => (
              <Input
                id={inputId}
                name="durationMinutes"
                inputMode="numeric"
                value={form.durationMinutes}
                onChange={(e) => handleChange("durationMinutes", e.target.value)}
                placeholder="30"
                className="h-10 px-3"
                aria-invalid={Boolean(fieldErrors.durationMinutes)}
              />
            )}
          </Field>
          <label className="flex cursor-pointer items-center gap-2.5 text-sm font-medium">
            <input
              type="checkbox"
              checked={form.published}
              onChange={(e) => handleChange("published", e.target.checked)}
              className="size-4 accent-primary"
            />
            Published (available to students)
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
                "Create assessment"
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
        title={deleteTarget ? `Delete "${deleteTarget.title}"?` : "Delete assessment"}
        description="Assessments with student attempts cannot be deleted."
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
                "Delete assessment"
              )}
            </Button>
          </>
        }
      />
    </div>
  );
}
