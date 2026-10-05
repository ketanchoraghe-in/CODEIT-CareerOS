"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { KeyRound, Loader2, LogOut, Settings, ShieldCheck, UserRound } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/field";
import { Breadcrumb } from "@/components/breadcrumb";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { PageHeader } from "@/components/page-header";
import api from "@/lib/api";
import { auth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { mapFieldErrors } from "@/lib/format";

export default function AdminSettingsPage() {
  const router = useRouter();
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [form, setForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [revoking, setRevoking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      setMe(await api.get("/admin/me"));
    } catch (err) {
      setLoadError(err?.message || "Failed to load admin profile.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async data loader; state updates happen in promise callbacks
    load();
  }, [load]);

  async function handlePasswordChange(e) {
    e?.preventDefault();
    setFieldErrors({});
    if (!form.currentPassword || !form.newPassword) {
      setFieldErrors({ currentPassword: !form.currentPassword ? "Current password is required." : undefined, newPassword: !form.newPassword ? "New password is required." : undefined });
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: "New passwords do not match." });
      return;
    }
    setSaving(true);
    try {
      await api.put("/users/me/password", { currentPassword: form.currentPassword, newPassword: form.newPassword });
      toast.success("Admin password changed. Other sessions were revoked — please sign in again.");
      auth.clear();
      router.replace("/login");
    } catch (err) {
      if (err.fieldErrors?.length) setFieldErrors(mapFieldErrors(err));
      toast.error(err?.message || "Could not change password.");
    } finally {
      setSaving(false);
    }
  }

  async function handleRevokeSessions() {
    setRevoking(true);
    try {
      await api.post("/users/me/sessions/revoke-all", {});
      toast.success("All other sessions revoked. Please sign in again.");
      auth.clear();
      router.replace("/login");
    } catch (err) {
      toast.error(err?.message || "Could not revoke sessions.");
    } finally {
      setRevoking(false);
    }
  }

  if (loading) return <LoadingState label="Loading settings…" />;

  if (loadError) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Settings"
          description="Your admin account and session security."
          breadcrumb={<Breadcrumb items={[{ label: "Settings" }]} />}
        />
        <ErrorState title="Couldn't load settings" description={loadError} onRetry={load} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Settings"
        description="Your admin account and session security."
        breadcrumb={<Breadcrumb items={[{ label: "Settings" }]} />}
      />

      <div className="grid items-start gap-4 lg:grid-cols-2">
        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <UserRound className="size-4" aria-hidden="true" />
              </span>
              Admin account
            </CardTitle>
            <CardDescription>Signed in as the platform administrator.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            {[
              ["Name", me?.fullName || "—"],
              ["Email", me?.email || "—"],
            ].map(([label, value]) => (
              <div
                key={label}
                className="flex items-center justify-between gap-2 rounded-xl border border-border/50 bg-background/40 px-3 py-2"
              >
                <span className="text-[11px] font-bold uppercase tracking-wide text-muted-foreground">{label}</span>
                <span className="truncate font-semibold">{value}</span>
              </div>
            ))}
            <div className="flex items-center justify-between gap-2 rounded-xl border border-border/50 bg-background/40 px-3 py-2">
              <span className="text-[11px] font-bold uppercase tracking-wide text-muted-foreground">Role</span>
              <Badge variant="secondary" className="gap-1 bg-primary/10 text-primary">
                <ShieldCheck className="size-3" aria-hidden="true" />
                {me?.role || "ADMIN"}
              </Badge>
            </div>
            <p className="rounded-xl bg-muted/60 p-3 text-xs leading-relaxed text-muted-foreground">
              Platform content (careers, skills, assessments) is managed under Content. AI provider
              keys live in the backend environment — never in this console.
            </p>
          </CardContent>
        </Card>

        <Card className="shadow-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <span className="flex size-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <KeyRound className="size-4" aria-hidden="true" />
              </span>
              Change password
            </CardTitle>
            <CardDescription>Changing it revokes all sessions, including this one.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handlePasswordChange} className="grid gap-3">
              <Field label="Current password" required error={fieldErrors.currentPassword}>
                <Input
                  type="password"
                  autoComplete="current-password"
                  value={form.currentPassword}
                  onChange={(e) => setForm((p) => ({ ...p, currentPassword: e.target.value }))}
                  placeholder="••••••••"
                />
              </Field>
              <Field label="New password" required error={fieldErrors.newPassword} hint="Same strength rules as student registration">
                <Input
                  type="password"
                  autoComplete="new-password"
                  value={form.newPassword}
                  onChange={(e) => setForm((p) => ({ ...p, newPassword: e.target.value }))}
                  placeholder="••••••••"
                />
              </Field>
              <Field label="Confirm new password" required error={fieldErrors.confirmPassword}>
                <Input
                  type="password"
                  autoComplete="new-password"
                  value={form.confirmPassword}
                  onChange={(e) => setForm((p) => ({ ...p, confirmPassword: e.target.value }))}
                  placeholder="••••••••"
                />
              </Field>
              <div className="flex flex-wrap gap-2">
                <Button type="submit" size="sm" disabled={saving} className="btn-polish">
                  {saving ? <Loader2 className="size-3.5 animate-spin" aria-hidden="true" /> : <KeyRound className="size-3.5" aria-hidden="true" />}
                  {saving ? "Saving…" : "Change password"}
                </Button>
                <Button type="button" size="sm" variant="outline" disabled={revoking} onClick={handleRevokeSessions} className="btn-polish">
                  {revoking ? <Loader2 className="size-3.5 animate-spin" aria-hidden="true" /> : <LogOut className="size-3.5" aria-hidden="true" />}
                  {revoking ? "Revoking…" : "Sign out other sessions"}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>

      <p className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
        <Settings className="size-3.5" aria-hidden="true" />
        Platform-wide configuration (database, storage, AI provider) is managed through backend
        environment variables — see docs/PRODUCTION.md.
      </p>
    </div>
  );
}
