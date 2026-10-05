"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Bot,
  CheckCircle2,
  Database,
  Gauge,
  History,
  Timer,
  TriangleAlert,
  Zap,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Breadcrumb } from "@/components/breadcrumb";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { cn } from "cn";

function LimitRow({ icon: Icon, label, value, hint }) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border/60 bg-background/40 p-4">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <Icon className="size-4" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-bold">{label}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>
      </div>
      <span className="tnum shrink-0 text-lg font-bold text-primary">{value}</span>
    </div>
  );
}

export default function AdminAiPage() {
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      setStatus(await api.get("/admin/ai/status"));
    } catch (err) {
      setLoadError(err?.message || "Failed to load AI configuration.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  if (loading) return <LoadingState label="Loading AI configuration…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="AI Configuration"
          description="Provider, safety limits and fallback status for the Career Assistant."
          breadcrumb={<Breadcrumb items={[{ label: "AI Configuration" }]} />}
        />
        <ErrorState title="Couldn't load AI configuration" description={loadError} onRetry={load} />
      </div>
    );
  }

  const live = status?.configured === true;
  const fallback = status?.offlineFallbackEnabled === true;

  return (
    <div className="space-y-6">
      <PageHeader
        title="AI Configuration"
        description="Provider, safety limits and fallback status for the Career Assistant. Keys stay server-side and are never shown here."
        breadcrumb={<Breadcrumb items={[{ label: "AI Configuration" }]} />}
        actions={
          <Badge
            variant="secondary"
            className={cn(
              "gap-1",
              live ? "bg-chart-3/15 text-chart-3" : "bg-amber-500/15 text-amber-700 dark:text-amber-400",
            )}
          >
            {live ? <Zap className="size-3" aria-hidden="true" /> : <Database className="size-3" aria-hidden="true" />}
            {live ? "LLM connected" : "Smart Guidance mode"}
          </Badge>
        }
      />

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Bot className="size-4" aria-hidden="true" />
              </span>
              Provider
            </CardTitle>
            <CardDescription>Which engine answers student questions.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            {[
              ["Provider", status?.provider || "—"],
              ["Model", status?.model || "—"],
              ["Answer mode", status?.mode === "llm" ? "LLM-enhanced (uses CareerOS data)" : "Smart Guidance (built-in, from CareerOS data)"],
            ].map(([label, value]) => (
              <div
                key={label}
                className="flex items-center justify-between gap-2 rounded-xl border border-border/50 bg-background/40 px-3 py-2"
              >
                <span className="text-[11px] font-bold uppercase tracking-wide text-muted-foreground">{label}</span>
                <span className="truncate font-semibold">{value}</span>
              </div>
            ))}
            <p className="rounded-xl bg-muted/60 p-3 text-xs leading-relaxed text-muted-foreground">
              To change the provider or model, set <span className="font-mono font-semibold">AI_PROVIDER</span>,{" "}
              <span className="font-mono font-semibold">AI_MODEL</span> /{" "}
              <span className="font-mono font-semibold">GEMINI_MODEL</span> and API keys in the backend
              environment, then restart the backend. Keys are never displayed in this console.
            </p>
          </CardContent>
        </Card>

        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                {fallback ? (
                  <CheckCircle2 className="size-4 text-chart-3" aria-hidden="true" />
                ) : (
                  <TriangleAlert className="size-4 text-amber-600" aria-hidden="true" />
                )}
              </span>
              Fallback safety net
            </CardTitle>
            <CardDescription>What happens when the provider fails.</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="rounded-2xl border border-border/60 bg-background/40 p-4 text-sm leading-relaxed">
              {fallback ? (
                <>
                  <span className="font-bold text-chart-3">On.</span>{" "}
                  <span className="text-muted-foreground">
                    If the provider is missing or errors, students still get instant answers built
                    from their verified CareerOS data. The chat never goes silent.
                  </span>
                </>
              ) : (
                <>
                  <span className="font-bold text-amber-700 dark:text-amber-400">Off.</span>{" "}
                  <span className="text-muted-foreground">
                    Provider failures return a clear error instead. Only disable the fallback if you
                    want strict LLM-only answers.
                  </span>
                </>
              )}
            </p>
            <p className="mt-2 text-xs text-muted-foreground">
              Controlled by <span className="font-mono font-semibold">AI_OFFLINE_FALLBACK</span> on the backend.
            </p>
          </CardContent>
        </Card>
      </div>

      <Card className="shadow-card">
        <CardHeader>
          <CardTitle className="text-base">Abuse & reliability limits</CardTitle>
          <CardDescription>Guardrails applied to every AI conversation. Tuned via backend env vars.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2">
          <LimitRow icon={Gauge} label="Tool iterations" value={status?.maxToolIterations ?? "—"} hint="Max data-tool calls per answer (hard cap 10)" />
          <LimitRow icon={History} label="History kept" value={status?.historyLimit ?? "—"} hint="Past messages sent with each request" />
          <LimitRow icon={Timer} label="Provider timeout" value={status?.requestTimeoutSeconds != null ? `${status.requestTimeoutSeconds}s` : "—"} hint="Wall-clock budget per provider call" />
          <LimitRow icon={Zap} label="Chat rate limit" value={status?.rateLimitPerMinute != null ? `${status.rateLimitPerMinute}/min` : "—"} hint="Max turns per student per minute" />
        </CardContent>
      </Card>
    </div>
  );
}
