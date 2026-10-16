"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Eye, EyeOff, Loader2, LogIn, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { AuthSplit } from "@/components/auth/auth-split";
import { Field } from "@/components/field";
import api from "@/lib/api";
import { auth } from "@/lib/auth";
import { GoogleSignInButton } from "@/components/auth/google-signin";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [form, setForm] = useState({ email: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);
  const [existingUser, setExistingUser] = useState(null);
  const [switching, setSwitching] = useState(false);

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
    if (fieldErrors[e.target.name]) {
      setFieldErrors((prev) => ({ ...prev, [e.target.name]: undefined }));
    }
  }

  // If a valid session already exists, never show a blank form that
  // instantly jumps to the dashboard (the reported "auto open my
  // account" bug). Instead show an explicit "already signed in" state.
  // ?switch=1 / ?force=1 / ?logout=1 forces the form for account switching.
  useEffect(() => {
    async function checkExistingSession() {
      const forceForm =
        searchParams.get("switch") === "1" ||
        searchParams.get("force") === "1" ||
        searchParams.get("logout") === "1";
      if (forceForm) {
        auth.clear();
        setExistingUser(null);
        setCheckingSession(false);
        return;
      }
      if (auth.isAuthenticated()) {
        const user = auth.getUser();
        setExistingUser(user);
        setCheckingSession(false);
        return;
      }
      // Access token expired but a refresh token exists: try one silent
      // refresh so a still-valid 7-day session continues, otherwise fall
      // through to the form with a clean (cleared) stale session.
      const refreshToken = auth.getRefreshToken();
      const staleAccess = auth.getAccessToken();
      if (refreshToken && staleAccess) {
        try {
          const data = await api.post("/auth/refresh", { refreshToken });
          auth.save({
            accessToken: data.accessToken,
            refreshToken: data.refreshToken,
            user: data.user,
          });
          setExistingUser(data.user || auth.getUser());
          setCheckingSession(false);
          return;
        } catch {
          auth.clear();
        }
      } else if (staleAccess && !refreshToken) {
        auth.clear();
      }
      setCheckingSession(false);
    }
    checkExistingSession();
  }, [searchParams]);

  function dashboardFor(user) {
    return user?.role === "ADMIN" ? "/admin/dashboard" : "/student/dashboard";
  }

  async function handleSwitchAccount() {
    setSwitching(true);
    try {
      const refreshToken = auth.getRefreshToken();
      if (refreshToken) {
        await api.post("/auth/logout", { refreshToken }).catch(() => null);
      }
    } finally {
      auth.clear();
      setExistingUser(null);
      setForm({ email: "", password: "" });
      setError("");
      setSwitching(false);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setFieldErrors({});
    setLoading(true);
    try {
      const data = await api.post("/auth/login", form);
      auth.save(data);
      router.replace(data.user?.role === "ADMIN" ? "/admin/dashboard" : "/student/dashboard");
    } catch (err) {
      if (err.fieldErrors?.length) {
        const mapped = {};
        err.fieldErrors.forEach((fe) => {
          mapped[fe.field] = fe.message;
        });
        setFieldErrors(mapped);
        setError(err.message || "Please check the form and try again.");
      } else if (err.status === 401) {
        setError(
          "Invalid email or password. Check for typos (especially in your email), or create a student account below.",
        );
      } else {
        setError(err.message || "Login failed. Check your email and password.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthSplit
      title={existingUser ? "You're already signed in" : "Welcome back"}
      description={
        existingUser
          ? `Signed in as ${existingUser.fullName || existingUser.email || "your account"}. Continue to your dashboard or switch accounts.`
          : "Sign in to continue your career journey."
      }
      footer={
        existingUser ? null : (
          <p className="text-sm text-muted-foreground">
            New to CareerOS?{" "}
            <Link href="/register" className="font-semibold text-chart-1 hover:underline">
              Create a student account
            </Link>
          </p>
        )
      }
    >
      {checkingSession ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground" role="status">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          Checking your session…
        </div>
      ) : existingUser ? (
        <div className="space-y-4">
          <div className="flex items-center gap-3 rounded-2xl border border-border/70 bg-muted/40 p-4">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/15 text-primary">
              <UserRound className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{existingUser.fullName || "CareerOS user"}</p>
              <p className="truncate text-xs text-muted-foreground">{existingUser.email}</p>
            </div>
            <span className="shrink-0 rounded-full bg-brand-600/10 px-2.5 py-1 text-[11px] font-bold text-brand-700">
              {existingUser.role || "STUDENT"}
            </span>
          </div>
          <Button size="lg" className="w-full text-base" onClick={() => router.replace(dashboardFor(existingUser))}>
            <LogIn className="size-4" aria-hidden="true" />
            Continue to dashboard
          </Button>
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="w-full"
            disabled={switching}
            onClick={handleSwitchAccount}
          >
            {switching ? (
              <>
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                Signing out…
              </>
            ) : (
              "Use another account"
            )}
          </Button>
          <p className="text-center text-xs text-muted-foreground">
            Want a new account?{" "}
            <Link href="/register?switch=1" className="font-semibold text-chart-1 hover:underline">
              Create one
            </Link>
          </p>
        </div>
      ) : (
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <Field id="email" label="Email" required error={fieldErrors.email}>
          {(inputId) => (
            <Input
              id={inputId}
              name="email"
              type="email"
              autoComplete="email"
              placeholder="you@college.edu"
              className="h-10 px-3"
              value={form.email}
              onChange={handleChange}
              aria-invalid={Boolean(fieldErrors.email)}
            />
          )}
        </Field>

        <Field id="password" label="Password" required error={fieldErrors.password}>
          {(inputId) => (
            <div className="relative">
              <Input
                id={inputId}
                name="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                placeholder="••••••••"
                className="h-10 px-3 pr-10"
                value={form.password}
                onChange={handleChange}
                aria-invalid={Boolean(fieldErrors.password)}
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? "Hide password" : "Show password"}
                aria-pressed={showPassword}
                className="absolute top-1/2 right-2.5 -translate-y-1/2 rounded-md p-1 text-muted-foreground transition hover:text-foreground"
              >
                {showPassword ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
              </button>
            </div>
          )}
        </Field>

        <Button type="submit" size="lg" className="w-full text-base" disabled={loading}>
          {loading ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Signing in…
            </>
          ) : (
            "Sign in"
          )}
        </Button>

        <GoogleSignInButton mode="signin" onError={setError} />

        <p className="text-center text-xs text-muted-foreground sm:hidden">
          New to CareerOS?{" "}
          <Link href="/register" className="font-semibold text-chart-1 hover:underline">
            Create an account
          </Link>
        </p>
      </form>
      )}
    </AuthSplit>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <AuthSplit title="Welcome back" description="Sign in to continue your career journey.">
          <div className="flex items-center justify-center gap-2 py-10 text-sm text-muted-foreground" role="status">
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            Loading…
          </div>
        </AuthSplit>
      }
    >
      <LoginForm />
    </Suspense>
  );
}