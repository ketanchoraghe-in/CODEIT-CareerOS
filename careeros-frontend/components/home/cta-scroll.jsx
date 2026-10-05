"use client";

import { useRef } from "react";
import Link from "next/link";
import { motion, useReducedMotion, useScroll, useSpring, useTransform } from "motion/react";
import { ArrowRight } from "lucide-react";

/**
 * CtaSection — the green panel expands and settles into place as it
 * enters the viewport; content and buttons follow on a delayed curve.
 * Same green styling, transform/opacity motion only.
 */
export function CtaSection() {
  const ref = useRef(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "center center"],
  });
  const smooth = useSpring(scrollYProgress, { stiffness: 80, damping: 22, mass: 0.7 });

  const panelScale = useTransform(smooth, [0, 1], [0.92, 1]);
  const panelY = useTransform(smooth, [0, 1], [72, 0]);
  const contentY = useTransform(smooth, [0.3, 1], [36, 0]);
  const contentOpacity = useTransform(smooth, [0.3, 1], [0.35, 1]);
  const buttonsScale = useTransform(smooth, [0.55, 1], [0.94, 1]);

  const scroll = (style) => (reduceMotion ? undefined : style);

  return (
    <section className="mx-auto max-w-7xl px-4 pt-8 pb-20">
      <motion.div
        ref={ref}
        style={scroll({ scale: panelScale, y: panelY })}
        className="bg-cta-brand relative overflow-hidden rounded-3xl px-6 py-12 text-center text-white sm:px-12 sm:py-16"
      >
        <motion.div style={scroll({ y: contentY, opacity: contentOpacity })}>
          <h2 className="text-2xl font-bold tracking-tight sm:text-4xl">
            Know where you stand. Know what to do next.
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-white/85 sm:text-base">
            Create your free student account, complete your profile and take your first career
            assessment.
          </p>
        </motion.div>
        <motion.div
          style={scroll({ scale: buttonsScale, opacity: contentOpacity })}
          className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row"
        >
          <Link
            href="/register"
            className="btn-polish inline-flex h-11 w-full items-center justify-center gap-2 rounded-full bg-white px-6 text-sm font-semibold text-primary shadow-md hover:bg-white/90 sm:w-auto"
          >
            Create your free account
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
          <Link
            href="/login"
            className="btn-polish inline-flex h-11 w-full items-center justify-center rounded-full border border-white/40 px-6 text-sm font-semibold text-white hover:bg-white/10 sm:w-auto"
          >
            Sign in
          </Link>
        </motion.div>
      </motion.div>
    </section>
  );
}
