import { useId } from "react";
import { cn } from "cn";

function Field({ id, label, required, error, hint, className, children }) {
  const autoId = useId();
  const inputId = id || autoId;

  return (
    <div className={cn("space-y-1.5", className)}>
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-foreground">
          {label}
          {required && (
            <span className="ml-0.5 text-destructive" aria-hidden="true">*</span>
          )}
        </label>
      )}
      {typeof children === "function" ? children(inputId) : children}
      {error ? (
        <p className="text-xs text-destructive" role="alert">{error}</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

export { Field };