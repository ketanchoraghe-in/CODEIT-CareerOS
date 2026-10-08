import { TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "cn";

function ErrorState({ title = "Something went wrong", description = "We couldn't load this page. Try again.", onRetry, className }) {
  return (
    <div className={cn("flex flex-col items-center justify-center rounded-xl border border-dashed border-destructive/30 bg-card/50 px-6 py-10 text-center", className)}>
      <div className="flex size-12 items-center justify-center rounded-full bg-destructive/10 text-destructive">
        <TriangleAlert className="size-6" aria-hidden="true" />
      </div>
      <h3 className="mt-3 text-sm font-semibold text-foreground">{title}</h3>
      <p className="mt-1 max-w-sm text-xs leading-relaxed text-muted-foreground">{description}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export { ErrorState };