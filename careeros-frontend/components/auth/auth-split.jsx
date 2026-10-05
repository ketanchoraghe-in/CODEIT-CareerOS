import Link from "next/link";
import { ArrowLeft, BarChart3, GraduationCap, Rocket, ShieldCheck, Sparkles, Target } from "lucide-react";
import { LogoWordmark } from "@/components/logo";

const steps = [
  {
    icon: Target,
    title: "Assess",
    text: "Role-specific skill assessments mapped to your goal.",
    accent: "bg-emerald-400/15 text-emerald-300 ring-emerald-300/25",
  },
  {
    icon: BarChart3,
    title: "Analyze",
    text: "See skills, gaps and readiness in one clear dashboard.",
    accent: "bg-amber-400/15 text-amber-300 ring-amber-300/25",
  },
  {
    icon: Rocket,
    title: "Improve",
    text: "Follow your roadmap, projects and AI guidance to get ready.",
    accent: "bg-sky-400/15 text-sky-300 ring-sky-300/25",
  },
];

const stats = [
  { value: "40+", label: "Target roles" },
  { value: "4", label: "Guided steps" },
  { value: "100%", label: "Free to start" },
];

function AuthSplit({ title, description, eyebrow, children, footer }) {
  return (
    <div className="flex min-h-screen bg-background">
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-pine-950 p-10 lg:flex xl:p-14">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(600px 300px at 20% 0%, oklch(0.6 0.17 148 / 0.38), transparent 60%), radial-gradient(500px 320px at 90% 100%, oklch(0.72 0.16 160 / 0.22), transparent 60%)",
          }}
        />
        <div className="relative flex items-center justify-between">
          <LogoWordmark tone="light" size="text-lg" />
          <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/10 px-3 py-1 text-[11px] font-semibold text-brand-200 backdrop-blur">
            <GraduationCap className="size-3.5" aria-hidden="true" />
            Free for students
          </span>
        </div>

        <div className="relative max-w-md">
          <h2 className="text-3xl leading-tight font-extrabold tracking-tight text-balance text-white xl:text-4xl">
            Know where you stand.
            <br />
            <span className="bg-gradient-to-r from-brand-300 via-brand-200 to-emerald-200 bg-clip-text text-transparent">
              Know what to do next.
            </span>
          </h2>
          <p className="mt-3 max-w-sm text-sm leading-relaxed text-pine-200">
            Assess your skills, close the gaps that matter and prove you&apos;re ready — all in
            one place.
          </p>
          <ul className="mt-8 space-y-3">
            {steps.map((step, i) => (
              <li
                key={step.title}
                className="flex items-center gap-4 rounded-2xl border border-white/10 bg-white/[0.05] p-3.5 backdrop-blur transition-colors hover:bg-white/[0.08]"
              >
                <span
                  className={`flex size-11 shrink-0 items-center justify-center rounded-xl ring-1 ${step.accent}`}
                >
                  <step.icon className="size-5" aria-hidden="true" />
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-white">
                    <span className="mr-1.5 text-[11px] font-extrabold text-pine-300">
                      0{i + 1}
                    </span>
                    {step.title}
                  </p>
                  <p className="mt-0.5 text-[13px] leading-relaxed text-pine-200">
                    {step.text}
                  </p>
                </div>
              </li>
            ))}
          </ul>
          <div className="mt-6 flex items-center gap-6 rounded-2xl border border-white/10 bg-white/[0.04] px-5 py-4 backdrop-blur">
            {stats.map((s, i) => (
              <div key={s.label} className={i > 0 ? "border-l border-white/10 pl-6" : ""}>
                <p className="text-xl font-extrabold tracking-tight text-white">{s.value}</p>
                <p className="mt-0.5 text-[11px] font-medium text-pine-300">{s.label}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="relative flex items-center justify-between text-xs text-pine-300">
          <p className="inline-flex items-center gap-1.5">
            <ShieldCheck className="size-3.5 text-brand-300" aria-hidden="true" />
            Test. Analyze. Improve. Become Industry Ready.
          </p>
          <p className="hidden items-center gap-1 xl:inline-flex">
            <Sparkles className="size-3.5 text-brand-300" aria-hidden="true" />
            No credit card needed
          </p>
        </div>
      </div>

      {/* Right form panel */}
      <div className="relative flex flex-1 flex-col items-center justify-center overflow-hidden px-4 py-10 sm:px-6">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(36rem 18rem at 50% 0%, oklch(0.93 0.055 150 / 0.7), transparent 65%)",
          }}
        />
        <div className="mb-6 lg:hidden">
          <LogoWordmark size="text-lg" />
        </div>

        <div className="relative w-full max-w-md rounded-3xl border border-border/60 bg-card p-6 shadow-popover sm:p-8">
          <span
            aria-hidden="true"
            className="absolute inset-x-8 top-0 h-[3px] rounded-full bg-gradient-to-r from-brand-700 via-brand-500 to-brand-400"
          />
          {eyebrow && (
            <p className="inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/10 px-3 py-1 text-[11px] font-bold tracking-wider text-primary uppercase">
              <Sparkles className="size-3" aria-hidden="true" />
              {eyebrow}
            </p>
          )}
          <h1 className="mt-3 text-2xl font-extrabold tracking-tight text-balance">{title}</h1>
          {description && (
            <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{description}</p>
          )}
          <div className="mt-6">{children}</div>
          {footer && (
            <>
              <div className="my-6 flex items-center gap-3 text-[11px] font-medium tracking-wider text-muted-foreground uppercase">
                <span aria-hidden="true" className="h-px flex-1 bg-border/70" />
                Or
                <span aria-hidden="true" className="h-px flex-1 bg-border/70" />
              </div>
              <div className="text-center">{footer}</div>
            </>
          )}
        </div>

        <p className="relative mt-6 inline-flex items-center gap-1.5 text-xs text-muted-foreground">
          <ArrowLeft className="size-3.5" aria-hidden="true" />
          <Link href="/" className="font-medium hover:text-foreground hover:underline">
            Back to home
          </Link>
        </p>
      </div>
    </div>
  );
}

export { AuthSplit };