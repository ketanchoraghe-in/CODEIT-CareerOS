/**
 * Shared label/date helpers for admin and student Sprint 2 screens.
 * All data comes from the backend - these helpers only format it.
 */

/** PROGRAMMING_LANGUAGES -> "Programming Languages" */
export function prettifyEnum(value) {
  if (!value) return "—";
  return String(value)
    .toLowerCase()
    .split("_")
    .map((part) => (part ? part[0].toUpperCase() + part.slice(1) : part))
    .join(" ");
}

/** Build {value,label} options for the Select component from an enum list. */
export function enumOptions(values = [], placeholder = "Select") {
  const list = [{ value: "", label: placeholder, disabled: true }];
  for (const value of values) {
    list.push({ value, label: prettifyEnum(value) });
  }
  return list;
}

export function formatDate(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function formatDateTime(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Map backend fieldErrors [{field,message}] onto a {field: message} object. */
export function mapFieldErrors(err) {
  const mapped = {};
  if (err?.fieldErrors?.length) {
    for (const fe of err.fieldErrors) {
      if (fe?.field) mapped[fe.field] = fe.message || "Invalid value";
    }
  }
  return mapped;
}
