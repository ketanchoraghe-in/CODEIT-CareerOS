"use client";

import { useRef } from "react";
import Image from "next/image";
import Link from "next/link";
import { motion, useReducedMotion, useScroll, useSpring, useTransform } from "motion/react";
import { ArrowRight, CheckCircle2, Sparkles, Target } from "lucide-react";

const heroChecks = ["Free for students", "Role-specific assessments", "AI-guided roadmap"];

/**
 * HeroSection — same markup/design as before, but scroll-driven:
 * copy drifts up and settles, preview scales down slightly, the two
 * floating cards travel at different speeds, and the green glow drifts.
 * All motion is transform/opacity only and smoothed with a spring.
 */
export function HeroSection() {
  const ref = useRef(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end start"] });
  const smooth = useSpring(scrollYProgress, { stiffness: 90, damping: 26, mass: 0.6 });

  const contentY = useTransform(smooth, [0, 1], [0, -72]);
  const contentOpacity = useTransform(smooth, [0, 0.8], [1, 0.25]);
  const previewY = useTransform(smooth, [0, 1], [0, 64]);
  const previewScale = useTransform(smooth, [0, 1], [1, 0.94]);
  const roadmapY = useTransform(smooth, [0, 1], [0, -110]);
  const aiCardY = useTransform(smooth, [0, 1], [0, -44]);
  const glowX = useTransform(smooth, [0, 1], [0, 72]);
  const glowY = useTransform(smooth, [0, 1], [0, 96]);

  // Scroll transforms live on motion wrappers; CSS entrance/float
  // animations stay on inner elements so they never fight over transforms.
  const scroll = (style) => (reduceMotion ? undefined : style);

  return (
    <section ref={ref} className="bg-hero-brand relative overflow-hidden">
      <motion.div
        aria-hidden="true"
        style={scroll({ x: glowX, y: glowY })}
        className="pointer-events-none absolute -top-28 right-[-12%] h-[26rem] w-[44rem]"
      >
        <div className="anim-hero-glow h-full w-full rounded-full bg-brand-400/20 blur-3xl" />
      </motion.div>

      <div className="mx-auto grid max-w-7xl items-center gap-14 px-4 pt-16 pb-20 sm:pt-20 lg:grid-cols-2 lg:gap-10">
        <motion.div style={scroll({ y: contentY, opacity: contentOpacity })}>
          <div className="text-center lg:text-left">
            <span
              className="anim-hero-rise inline-flex items-center gap-2 rounded-full border border-primary/25 bg-primary/10 px-3.5 py-1.5 text-xs font-semibold text-primary"
              style={{ "--d": "0.05s" }}
            >
              <Sparkles className="size-3.5" aria-hidden="true" />
              Test. Analyze. Improve. Become Industry Ready.
            </span>
            <h1
              className="anim-hero-rise mt-6 text-4xl leading-[1.08] font-extrabold tracking-tight sm:text-5xl xl:text-6xl"
              style={{ "--d": "0.15s" }}
            >
              Know Where You Stand.
              <br />
              <span className="text-primary">Know What To Do Next.</span>
            </h1>
            <p
              className="anim-hero-rise mx-auto mt-5 max-w-xl text-base leading-relaxed text-muted-foreground sm:text-lg lg:mx-0"
              style={{ "--d": "0.26s" }}
            >
              CODEIT CareerOS analyzes your skills, career gaps, CV and professional profile to
              create a personalized career action plan.
            </p>
            <div
              className="anim-hero-rise mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row lg:justify-start"
              style={{ "--d": "0.36s" }}
            >
              <Link
                href="/register"
                className="btn-polish inline-flex h-11 w-full items-center justify-center gap-2 rounded-full bg-primary px-6 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/85 active:translate-y-px sm:w-auto"
              >
                Start Career Assessment
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
              <Link
                href="/login"
                className="btn-polish inline-flex h-11 w-full items-center justify-center rounded-full border border-border bg-card/70 px-6 text-sm font-semibold shadow-sm hover:bg-muted sm:w-auto"
              >
                Explore CareerOS
              </Link>
            </div>
            <ul
              className="anim-hero-rise mt-8 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm text-muted-foreground lg:justify-start"
              style={{ "--d": "0.46s" }}
            >
              {heroChecks.map((check) => (
                <li key={check} className="inline-flex items-center gap-1.5">
                  <CheckCircle2 className="size-4 text-chart-3" aria-hidden="true" />
                  {check}
                </li>
              ))}
            </ul>
          </div>
        </motion.div>

        {/* Product preview */}
        <motion.div
          style={scroll({ y: previewY, scale: previewScale })}
          className="relative mx-auto w-full max-w-md lg:max-w-lg"
        >
          <div className="anim-hero-pop" style={{ "--d": "0.3s" }}>
            <div aria-hidden="true" className="absolute -inset-8 -z-10 rounded-[3rem] bg-primary/15 blur-3xl" />
            <div className="overflow-hidden rounded-3xl border border-border/70 bg-card shadow-popover">
              <Image
                src="/images/ui-preview.png"
                alt="CODEIT CareerOS dashboard preview"
                width={1377}
                height={1142}
                className="h-auto w-full"
                priority
              />
            </div>

            <motion.div
              style={scroll({ y: roadmapY })}
              className="absolute -top-5 -right-3 sm:-right-6"
            >
              <div className="anim-float-soft flex items-center gap-2.5 rounded-2xl border border-border/70 bg-card px-3.5 py-2.5 shadow-popover">
                <span className="flex size-8 items-center justify-center rounded-xl bg-chart-3/15 text-chart-3">
                  <Target className="size-4" aria-hidden="true" />
                </span>
                <div className="leading-tight">
                  <p className="text-xs font-bold">90-Day Roadmap</p>
                  <p className="mt-0.5 text-[10px] text-muted-foreground">Personalized for your goal</p>
                </div>
              </div>
            </motion.div>
            <motion.div
              style={scroll({ y: aiCardY })}
              className="absolute -bottom-5 -left-3 sm:-left-6"
            >
              <div
                className="anim-float-soft flex items-center gap-2.5 rounded-2xl border border-border/70 bg-card px-3.5 py-2.5 shadow-popover"
                style={{ "--d": "3s" }}
              >
                <span className="flex size-8 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <Sparkles className="size-4" aria-hidden="true" />
                </span>
                <div className="leading-tight">
                  <p className="text-xs font-bold">AI Career Guidance</p>
                  <p className="mt-0.5 text-[10px] text-muted-foreground">Know what to do next</p>
                </div>
              </div>
            </motion.div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
