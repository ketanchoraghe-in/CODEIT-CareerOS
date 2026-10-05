import { useId } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "cn";

function Select({
  id,
  label,
  hint,
  error,
  className,
  wrapperClassName,
  options = [],
  required,
  ...props
}) {
  const autoId = useId();
  const inputId = id || autoId;

  return (
    <div className={cn("space-y-1.5", wrapperClassName)}>
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-foreground">
          {label}
          {required && <span className="ml-0.5 text-destructive" aria-hidden="true">*</span>}
        </label>
      )}
      <div className="relative">
        <select
          id={inputId}
          className={cn(
            "h-10 w-full appearance-none rounded-lg border border-input bg-background px-3 pr-10 text-sm text-foreground transition-colors",
            "focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50",
            "disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50",
            error && "border-destructive ring-3 ring-destructive/20",
            className,
          )}
          aria-invalid={Boolean(error)}
          {...props}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown
          className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
      </div>
      {error ? (
        <p className="text-xs text-destructive" role="alert">{error}</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

export { Select };