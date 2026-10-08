import Link from "next/link";
import { CheckCircle2, Rocket } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

function ComingSoon({
  title,
  description = "This feature is planned for a later sprint and will appear here once it ships.",
  badge = true,
  icon: Icon,
  sprint,
  bullets = [],
}) {
  return (
    <div className="animate-fade-up space-y-6">
      <div className="flex flex-wrap items-center gap-2.5">
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {badge && <Badge variant="secondary" className="text-[10px]">coming soon</Badge>}
        {sprint && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-2.5 py-1 text-[11px] font-semibold text-primary">
            <Rocket className="size-3" aria-hidden="true" />
            Ships in {sprint}
          </span>
        )}
      </div>
      <p className="max-w-2xl text-sm leading-relaxed text-muted-foreground">{description}</p>

      <div className="rounded-2xl border border-border/60 bg-card p-6 shadow-card sm:p-8">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
          {Icon && (
            <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <Icon className="size-6" aria-hidden="true" />
            </span>
          )}
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-semibold">{title} is on the CareerOS roadmap</h2>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
              This is an intentional placeholder — no fake data here. The real experience ships with
              its corresponding sprint, powered by the CareerOS backend.
            </p>
            {bullets.length > 0 && (
              <ul className="mt-4 grid gap-2.5 sm:grid-cols-2">
                {bullets.map((bullet) => (
                  <li key={bullet} className="flex items-start gap-2 text-sm text-foreground/90">
                    <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-chart-3" aria-hidden="true" />
                    <span>{bullet}</span>
                  </li>
                ))}
              </ul>
            )}
            <div className="mt-6">
              <Button variant="outline" size="sm" render={<Link href="/" />}>
                Back to home
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export { ComingSoon };