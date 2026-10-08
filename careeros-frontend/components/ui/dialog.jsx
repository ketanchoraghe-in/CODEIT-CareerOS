"use client";

import { useEffect, useRef } from "react";
import { X } from "lucide-react";
import { cn } from "cn";

function Dialog({ open, onOpenChange, title, description, children, footer, className }) {
  const panelRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const previousFocus = document.activeElement;
    if (panelRef.current) {
      panelRef.current.focus({ preventScroll: true });
    }
    function onKeyDown(event) {
      if (event.key === "Escape") onOpenChange(false);
    }
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKeyDown);
      if (previousFocus && previousFocus.focus) previousFocus.focus();
    };
  }, [open, onOpenChange]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-4 sm:items-center" role="presentation">
      <div
        className="absolute inset-0 animate-fade-in bg-pine-950/60 backdrop-blur-sm"
        onClick={() => onOpenChange(false)}
        aria-hidden="true"
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        className={cn(
          "relative z-10 w-full max-w-md animate-zoom-in rounded-2xl border border-border bg-card shadow-popover outline-none",
          className,
        )}
        tabIndex={-1}
      >
        <button
          type="button"
          onClick={() => onOpenChange(false)}
          aria-label="Close dialog"
          className="absolute top-3 right-3 flex size-7 items-center justify-center rounded-md text-muted-foreground transition hover:bg-muted hover:text-foreground"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
        <div className="p-6 pt-7">
          {title && (
            <h2 id="dialog-title" className="text-lg font-semibold tracking-tight">
              {title}
            </h2>
          )}
          {description && <p className="mt-1.5 text-sm text-muted-foreground">{description}</p>}
          {children}
        </div>
        {footer && <div className="flex justify-end gap-2 border-t border-border/60 px-6 py-4">{footer}</div>}
      </div>
    </div>
  );
}

export { Dialog };