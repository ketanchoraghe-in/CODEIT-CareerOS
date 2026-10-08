"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowLeft,
  CheckCircle2,
  HelpCircle,
  Loader2,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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

function newOption() {
  return {
    key: `opt-${Date.now()}-${Math.floor(Math.random() * 100000)}`,
    optionText: "",
    correct: false,
  };
}

const emptyQuestionForm = {
  skillId: "",
  questionText: "",
  difficulty: "",
  explanation: "",
  displayOrder: "",
  active: true,
  options: [newOption(), newOption()],
};

export default function AdminAssessmentDetailPage() {
  const params = useParams();
  const assessmentId = params?.id;

  const [detail, setDetail] = useState(null);
  const [activeSkills, setActiveSkills] = useState([]);
  const [difficultyLevels, setDifficultyLevels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyQuestionForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [assessmentDetail, skillData, catalog] = await Promise.all([
        api.get(`/admin/assessments/${assessmentId}`),
        api.get("/admin/skills"),
        api.get("/meta/catalog"),
      ]);
      setDetail(assessmentDetail);
      setActiveSkills(
        Array.isArray(skillData) ? skillData.filter((skill) => skill.active) : [],
      );
      setDifficultyLevels(catalog?.difficultyLevels || []);
    } catch (err) {
      setLoadError(err?.message || "Failed to load the assessment.");
    } finally {
      setLoading(false);
    }
  }, [assessmentId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    if (assessmentId) load();
  }, [assessmentId, load]);

  function openCreate() {
    setEditing(null);
    setForm({ ...emptyQuestionForm, options: [newOption(), newOption()] });
    setFieldErrors({});
    setFormError("");
    setDialogOpen(true);
  }

  function openEdit(question) {
    setEditing(question);
    setForm({
      skillId: String(question.skillId ?? ""),
      questionText: question.questionText || "",
      difficulty: question.difficulty || "",
      explanation: question.explanation || "",
      displayOrder: question.displayOrder != null ? String(question.displayOrder) : "",
      active: question.active !== false,
      options: (question.options || []).map((option, index) => ({
        key: `opt-${option.id ?? index}`,
        optionText: option.optionText || "",
        correct: Boolean(option.correct),
      })),
    });
    setFieldErrors({});
    setFormError("");
    setDialogOpen(true);
  }

  function handleFormChange(name, value) {
    setForm((prev) => ({ ...prev, [name]: value }));
    if (fieldErrors[name]) setFieldErrors((prev) => ({ ...prev, [name]: undefined }));
    if (formError) setFormError("");
  }

  function updateOption(key, field, value) {
    setForm((prev) => ({
      ...prev,
      options: prev.options.map((option) =>
        option.key === key ? { ...option, [field]: value } : field === "correct" && value ? { ...option, correct: false } : option,
      ),
    }));
    if (formError) setFormError("");
  }

  function markCorrect(key) {
    setForm((prev) => ({
      ...prev,
      options: prev.options.map((option) => ({ ...option, correct: option.key === key })),
    }));
    if (formError) setFormError("");
  }

  function addOption() {
    setForm((prev) => ({ ...prev, options: [...prev.options, newOption()] }));
  }

  function removeOption(key) {
    setForm((prev) => ({
      ...prev,
      options: prev.options.filter((option) => option.key !== key),
    }));
  }

  function validate() {
    const fieldErrorsFound = {};
    if (!form.skillId) fieldErrorsFound.skillId = "Skill is required";
    if (!form.questionText.trim()) fieldErrorsFound.questionText = "Question text is required";
    else if (form.questionText.trim().length > 1000) {
      fieldErrorsFound.questionText = "Question text must be at most 1000 characters";
    }
    if (!form.difficulty) fieldErrorsFound.difficulty = "Difficulty is required";
    if (form.explanation && form.explanation.length > 600) {
      fieldErrorsFound.explanation = "Explanation must be at most 600 characters";
    }
    if (form.displayOrder !== "" && (!Number.isInteger(Number(form.displayOrder)) || Number(form.displayOrder) < 0)) {
      fieldErrorsFound.displayOrder = "Display order must be a non-negative number";
    }

    const texts = form.options.map((option) => option.optionText.trim());
    if (texts.some((text) => text.length === 0)) return { fieldErrorsFound, formError: "Every option needs text." };
    if (texts.some((text) => text.length > 500)) {
      return { fieldErrorsFound, formError: "Option text must be at most 500 characters." };
    }
    const seen = new Set();
    for (const text of texts) {
      const key = text.toLowerCase();
      if (seen.has(key)) return { fieldErrorsFound, formError: "Option text must be unique within a question." };
      seen.add(key);
    }
    if (form.options.length < 2) return { fieldErrorsFound, formError: "A question needs at least 2 options." };
    const correctCount = form.options.filter((option) => option.correct).length;
    if (correctCount !== 1) return { fieldErrorsFound, formError: "Mark exactly one option as correct." };
    return { fieldErrorsFound, formError: "" };
  }

  async function handleSave(event) {
    event.preventDefault();
    const { fieldErrorsFound, formError: problem } = validate();
    setFieldErrors(fieldErrorsFound);
    if (problem) {
      setFormError(problem);
      return;
    }
    if (Object.keys(fieldErrorsFound).length > 0) {
      toast.error("Please fix the highlighted fields.");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        assessmentId: Number(assessmentId),
        skillId: Number(form.skillId),
        questionText: form.questionText.trim(),
        difficulty: form.difficulty,
        explanation: form.explanation.trim() || null,
        questionType: "MCQ",
        active: form.active,
        displayOrder: form.displayOrder === "" ? null : Number(form.displayOrder),
        options: form.options.map((option, index) => ({
          optionText: option.optionText.trim(),
          correct: option.correct,
          displayOrder: index + 1,
        })),
      };
      if (editing) {
        await api.put(`/admin/questions/${editing.id}`, payload);
        toast.success("Question updated.");
      } else {
        await api.post(`/admin/assessments/${assessmentId}/questions`, payload);
        toast.success("Question added.");
      }
      setDialogOpen(false);
      await load();
    } catch (err) {
      const mapped = mapFieldErrors(err);
      if (Object.keys(mapped).length > 0) setFieldErrors(mapped);
      setFormError(err?.message || "Failed to save the question.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.delete(`/admin/questions/${deleteTarget.id}`);
      toast.success("Question deleted.");
      setDeleteTarget(null);
      await load();
    } catch (err) {
      toast.error(err?.message || "Failed to delete the question.");
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <LoadingState label="Loading assessment…" />;

  if (loadError || !detail) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Assessment"
          breadcrumb={
            <Breadcrumb
              items={[{ label: "Assessments", href: "/admin/assessments" }, { label: "Detail" }]}
            />
          }
        />
        <ErrorState title="Couldn't load the assessment" description={loadError} onRetry={load} />
      </div>
    );
  }

  const questions = Array.isArray(detail.questions) ? detail.questions : [];

  return (
    <div className="space-y-6">
      <PageHeader
        title={detail.title}
        description={detail.description || "No description."}
        breadcrumb={
          <Breadcrumb
            items={[{ label: "Assessments", href: "/admin/assessments" }, { label: detail.title }]}
          />
        }
        actions={
          <Button size="sm" onClick={openCreate}>
            <Plus className="size-4" aria-hidden="true" />
            Add question
          </Button>
        }
      />

      <Card className="shadow-card">
        <CardHeader>
          <CardTitle className="text-base">Assessment overview</CardTitle>
          <CardDescription>Configuration students see when they start this assessment.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-2 text-sm">
          <Badge variant="outline">{detail.careerName}</Badge>
          <Badge variant="secondary">{detail.durationMinutes} min</Badge>
          <Badge variant="secondary">{detail.questionCount} questions</Badge>
          {detail.published ? (
            <Badge variant="secondary" className="bg-chart-3/15 text-chart-3">
              Published
            </Badge>
          ) : (
            <Badge variant="secondary">Draft</Badge>
          )}
          <span className="ml-auto">
            <Button variant="outline" size="sm" render={<Link href="/admin/assessments" />}>
              <ArrowLeft className="size-3.5" aria-hidden="true" />
              All assessments
            </Button>
          </span>
        </CardContent>
      </Card>

      {questions.length === 0 ? (
        <EmptyState
          icon={HelpCircle}
          title="No questions yet"
          description="Add the first MCQ question to this assessment."
          action={
            <Button size="sm" onClick={openCreate}>
              <Plus className="size-4" aria-hidden="true" />
              Add question
            </Button>
          }
        />
      ) : (
        <div className="space-y-3">
          {questions.map((question, index) => (
            <Card key={question.id} className="shadow-card">
              <CardContent className="space-y-3 pt-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                      Question {question.displayOrder ?? index + 1}
                    </p>
                    <p className="mt-1 text-sm font-medium leading-relaxed">{question.questionText}</p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <Badge variant="outline">{question.skillName}</Badge>
                      <Badge variant="secondary">{prettifyEnum(question.difficulty)}</Badge>
                      {question.active ? (
                        <Badge variant="secondary" className="bg-chart-3/15 text-chart-3">
                          Active
                        </Badge>
                      ) : (
                        <Badge variant="secondary">Inactive</Badge>
                      )}
                    </div>
                    {question.explanation && (
                      <p className="mt-2 text-xs leading-relaxed text-muted-foreground">
                        Explanation: {question.explanation}
                      </p>
                    )}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button variant="outline" size="sm" onClick={() => openEdit(question)}>
                      <Pencil className="size-3.5" aria-hidden="true" />
                      Edit
                    </Button>
                    <Button variant="destructive" size="sm" onClick={() => setDeleteTarget(question)}>
                      <Trash2 className="size-3.5" aria-hidden="true" />
                      Delete
                    </Button>
                  </div>
                </div>
                <ul className="grid gap-2 sm:grid-cols-2">
                  {(question.options || []).map((option) => (
                    <li
                      key={option.id}
                      className={
                        option.correct
                          ? "flex items-start gap-2 rounded-lg border border-chart-3/40 bg-chart-3/10 px-3 py-2 text-sm"
                          : "flex items-start gap-2 rounded-lg border border-border/60 bg-background/40 px-3 py-2 text-sm"
                      }
                    >
                      {option.correct ? (
                        <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-chart-3" aria-hidden="true" />
                      ) : (
                        <span className="mt-1.5 size-2 shrink-0 rounded-full bg-muted-foreground/40" aria-hidden="true" />
                      )}
                      <span>{option.optionText}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        title={editing ? "Edit question" : "Add question"}
        description="MCQ questions need at least 2 options with exactly one marked correct."
        className="max-w-2xl"
      >
        <form onSubmit={handleSave} noValidate className="mt-4 space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              id="question-skill"
              label="Skill"
              required
              value={form.skillId}
              onChange={(e) => handleFormChange("skillId", e.target.value)}
              options={[
                { value: "", label: "Select skill", disabled: true },
                ...activeSkills.map((skill) => ({
                  value: String(skill.id),
                  label: `${skill.name} (${prettifyEnum(skill.category)})`,
                })),
              ]}
              error={fieldErrors.skillId}
            />
            <Select
              id="question-difficulty"
              label="Difficulty"
              required
              value={form.difficulty}
              onChange={(e) => handleFormChange("difficulty", e.target.value)}
              options={enumOptions(difficultyLevels, "Select difficulty")}
              error={fieldErrors.difficulty}
            />
          </div>
          <Field id="question-text" label="Question" required error={fieldErrors.questionText}>
            {(inputId) => (
              <Input
                id={inputId}
                name="questionText"
                value={form.questionText}
                onChange={(e) => handleFormChange("questionText", e.target.value)}
                placeholder="What does this question ask?"
                className="h-10 px-3"
                aria-invalid={Boolean(fieldErrors.questionText)}
              />
            )}
          </Field>
          <Field
            id="question-explanation"
            label="Explanation"
            error={fieldErrors.explanation}
            hint="Optional, up to 600 characters. Never shown to students mid-attempt."
          >
            {(inputId) => (
              <Input
                id={inputId}
                name="explanation"
                value={form.explanation}
                onChange={(e) => handleFormChange("explanation", e.target.value)}
                placeholder="Why the correct answer is correct…"
                className="h-10 px-3"
                aria-invalid={Boolean(fieldErrors.explanation)}
              />
            )}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field
              id="question-order"
              label="Display order"
              error={fieldErrors.displayOrder}
              hint="Leave empty to append at the end."
            >
              {(inputId) => (
                <Input
                  id={inputId}
                  name="displayOrder"
                  inputMode="numeric"
                  value={form.displayOrder}
                  onChange={(e) => handleFormChange("displayOrder", e.target.value)}
                  placeholder="Auto"
                  className="h-10 px-3"
                  aria-invalid={Boolean(fieldErrors.displayOrder)}
                />
              )}
            </Field>
            <div className="flex items-end pb-2">
              <label className="flex cursor-pointer items-center gap-2.5 text-sm font-medium">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => handleFormChange("active", e.target.checked)}
                  className="size-4 accent-primary"
                />
                Active (included in attempts)
              </label>
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-sm font-medium">
                Options <span className="text-destructive" aria-hidden="true">*</span>
              </p>
              <Button type="button" variant="outline" size="sm" onClick={addOption}>
                <Plus className="size-3.5" aria-hidden="true" />
                Add option
              </Button>
            </div>
            {form.options.map((option, index) => (
              <div
                key={option.key}
                className="grid grid-cols-[auto_1fr_auto] items-center gap-2.5 rounded-xl border border-border/60 bg-background/40 p-2.5"
              >
                <label className="flex cursor-pointer items-center gap-1.5 pl-1 text-xs text-muted-foreground" title="Mark as the correct answer">
                  <input
                    type="radio"
                    name={`correct-${editing?.id ?? "new"}`}
                    checked={option.correct}
                    onChange={() => markCorrect(option.key)}
                    className="size-4 accent-primary"
                    aria-label={`Mark option ${index + 1} as correct`}
                  />
                  Correct
                </label>
                <Input
                  name={`option-${option.key}`}
                  value={option.optionText}
                  onChange={(e) => updateOption(option.key, "optionText", e.target.value)}
                  placeholder={`Option ${index + 1}`}
                  className="h-10 px-3"
                  maxLength={500}
                  aria-label={`Option ${index + 1} text`}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  onClick={() => removeOption(option.key)}
                  disabled={form.options.length <= 2}
                  aria-label={`Remove option ${index + 1}`}
                  title="Remove option"
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                </Button>
              </div>
            ))}
          </div>

          {formError && (
            <p className="text-sm text-destructive" role="alert">
              {formError}
            </p>
          )}

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
                "Add question"
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
        title="Delete this question?"
        description="Questions with saved student answers cannot be deleted."
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
                "Delete question"
              )}
            </Button>
          </>
        }
      />
    </div>
  );
}
