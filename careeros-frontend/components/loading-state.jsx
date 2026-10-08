import { Loader2 } from "lucide-react";
import { cn } from "cn";

function LoadingState({ label = "Loading…", className }) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-2 py-12 text-muted-foreground", className)}>
      <Loader2 className="size-6 animate-spin text-primary" aria-hidden="true" />
      <p className="text-sm">{label}</p>
    </div>
  );
}

export { LoadingState };