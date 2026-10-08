"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Lightbulb, Loader2, Pencil, Plus, Search, Trash2 } from "lucide-react";
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
import { enumOptions, formatDateTime, mapFieldErrors, prettifyEnum } from "@/lib/format";

const emptyForm = { name: "", category: "", description: "", active: true };

export default function AdminSkillsPage() {
  const [skills, setSkills] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("ALL");

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
      const [skillData, catalog] = await Promise.all([
        api.get("/admin/skills"),
        api.get("/meta/catalog"),
      ]);
      setSkills(Array.isArray(skillData) ? skillData : []);
      setCategories(catalog?.skillCategories || []);
    } catch (err) {
      setLoadError(err?.message || "Failed to load the skill catalog.");
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
    return skills.filter((skill) => {
      if (categoryFilter !== "ALL" && skill.category !== categoryFilter) return false;
      if (!term) return true;
      return (
        skill.name?.toLowerCase().includes(term) ||
        skill.description?.toLowerCase().includes(term)
      );
    });
  }, [skills, search, categoryFilter]);

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyForm });
    setFieldErrors({});
    setDialogOpen(true);
  }

  function openEdit(skill) {
    setEditing(skill);
    setForm({
      name: skill.name || "",
      category: skill.category || "",
      description: skill.description || "",
      active: skill.active !== false,
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
    if (!form.name.trim()) errors.name = "Skill name is required";
    else if (form.name.trim().length > 120) errors.name = "Skill name must be at most 120 characters";
    if (!form.category) errors.category = "Category is required";
    if (form.description && form.description.trim().length > 300) {
      errors.description = "Description must be at most 300 characters";
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
        name: form.name.trim(),
        category: form.category,
        description: form.description.trim() || null,
        active: form.active,
      };
      if (editing) {
        await api.put(`/admin/skills/${editing.id}`, payload);
        toast.success(`Skill "${payload.name}" updated.`);
      } else {
        await api.post("/admin/skills", payload);
        toast.success(`Skill "${payload.name}" created.`);
      }
      setDialogOpen(false);
      await load();
    } catch (err) {
      const mapped = mapFieldErrors(err);
      if (Object.keys(mapped).length > 0) {
        setFieldErrors(mapped);
      }
      toast.error(err?.message || "Failed to save the skill.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.delete(`/admin/skills/${deleteTarget.id}`);
      toast.success(`Skill "${deleteTarget.name}" deleted.`);
      setDeleteTarget(null);
      await load();
    } catch (err) {
      toast.error(err?.message || "Failed to delete the skill.");
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <LoadingState label="Loading skill catalog…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Skills"
          description="Maintain the skill catalog and competency framework."
          breadcrumb={<Breadcrumb items={[{ label: "Skills" }]} />}
        />
        <ErrorState title="Couldn't load skills" description={loadError} onRetry={load} />
      </div>
    );
  }

  const categoryFilterOptions = [
    { value: "ALL", label: "All categories" },
    ...categories.map((category) => ({ value: category, label: prettifyEnum(category) })),
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Skills"
        description="Maintain the skill catalog that powers career mappings and assessments."
        breadcrumb={<Breadcrumb items={[{ label: "Skills" }]} />}
        actions={
          <Button size="sm" onClick={openCreate}>
            <Plus className="size-4" aria-hidden="true" />
            Add skill
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
              placeholder="Search skills by name or description…"
              className="h-10 pl-9"
              aria-label="Search skills"
            />
          </div>
          <Select
            id="category-filter"
            aria-label="Filter by category"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            options={categoryFilterOptions}
            className="sm:w-56"
          />
          <Badge variant="secondary" className="shrink-0 self-start sm:self-auto">
            {filtered.length} of {skills.length} shown
          </Badge>
        </CardContent>
      </Card>

      {filtered.length === 0 ? (
        <EmptyState
          icon={Lightbulb}
          title={skills.length === 0 ? "No skills yet" : "No skills match your filters"}
          description={
            skills.length === 0
              ? "Create the first skill to start building the catalog."
              : "Try a different search term or category filter."
          }
          action={
            skills.length === 0 ? (
              <Button size="sm" onClick={openCreate}>
                <Plus className="size-4" aria-hidden="true" />
                Add skill
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
                    <th className="px-5 py-3 font-semibold">Skill</th>
                    <th className="px-5 py-3 font-semibold">Category</th>
                    <th className="px-5 py-3 font-semibold">Status</th>
                    <th className="px-5 py-3 font-semibold">Updated</th>
                    <th className="px-5 py-3 text-right font-semibold">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((skill) => (
                    <tr
                      key={skill.id}
                      className="border-b border-border/40 transition last:border-0 hover:bg-accent/40"
                    >
                      <td className="px-5 py-3">
                        <p className="font-medium">{skill.name}</p>
                        {skill.description && (
                          <p className="mt-0.5 max-w-md truncate text-xs text-muted-foreground">
                            {skill.description}
                          </p>
                        )}
                      </td>
                      <td className="px-5 py-3">
                        <Badge variant="outline">{prettifyEnum(skill.category)}</Badge>
                      </td>
                      <td className="px-5 py-3">
                        {skill.active ? (
                          <Badge variant="secondary" className="bg-chart-3/15 text-chart-3">
                            Active
                          </Badge>
                        ) : (
                          <Badge variant="secondary">Inactive</Badge>
                        )}
                      </td>
                      <td className="px-5 py-3 whitespace-nowrap text-muted-foreground">
                        {formatDateTime(skill.updatedAt)}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          <Button variant="outline" size="sm" onClick={() => openEdit(skill)}>
                            <Pencil className="size-3.5" aria-hidden="true" />
                            Edit
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => setDeleteTarget(skill)}
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
        title={editing ? `Edit skill — ${editing.name}` : "Add skill"}
        description={
          editing
            ? "Update the skill. It stays linked to every career and question that uses it."
            : "Create a new skill in the catalog. Skills can be mapped to careers and questions."
        }
      >
        <form onSubmit={handleSave} noValidate className="mt-4 space-y-4">
          <Field id="skill-name" label="Skill name" required error={fieldErrors.name}>
            {(inputId) => (
              <Input
                id={inputId}
                name="name"
                value={form.name}
                onChange={(e) => handleChange("name", e.target.value)}
                placeholder="e.g. Docker"
                className="h-10 px-3"
                maxLength={120}
                aria-invalid={Boolean(fieldErrors.name)}
              />
            )}
          </Field>
          <Select
            id="skill-category"
            label="Category"
            required
            value={form.category}
            onChange={(e) => handleChange("category", e.target.value)}
            options={enumOptions(categories, "Select category")}
            error={fieldErrors.category}
          />
          <Field
            id="skill-description"
            label="Description"
            error={fieldErrors.description}
            hint="Optional, up to 300 characters."
          >
            {(inputId) => (
              <Input
                id={inputId}
                name="description"
                value={form.description}
                onChange={(e) => handleChange("description", e.target.value)}
                placeholder="What this skill covers…"
                className="h-10 px-3"
                maxLength={300}
                aria-invalid={Boolean(fieldErrors.description)}
              />
            )}
          </Field>
          <label className="flex cursor-pointer items-center gap-2.5 text-sm font-medium">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => handleChange("active", e.target.checked)}
              className="size-4 accent-primary"
            />
            Active (visible to students and usable in mappings)
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
                "Create skill"
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
        title={deleteTarget ? `Delete "${deleteTarget.name}"?` : "Delete skill"}
        description="This permanently removes the skill. Skills mapped to a career or used by questions cannot be deleted."
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
                "Delete skill"
              )}
            </Button>
          </>
        }
      />
    </div>
  );
}
