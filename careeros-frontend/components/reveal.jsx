"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "cn";

/**
 * Reveal — lightweight scroll-into-view animation for the Home page.
 * GPU-friendly (opacity + translate only), one-shot, stagger via `delay`.
 * Renders visible immediately when prefers-reduced-motion is set.
 */
export function Reveal({ children, className, delay = 0, variant, ...rest }) {
  const ref = useRef(null);
  // Visible immediately when reduced motion is preferred (or no observer
  // support); otherwise revealed by the IntersectionObserver below.
  const [visible, setVisible] = useState(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return true;
    return typeof IntersectionObserver === "undefined";
  });

  useEffect(() => {
    if (visible) return;
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          io.disconnect();
        }
      },
      { threshold: 0.12, rootMargin: "0px 0px -48px 0px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [visible]);

  const { style, ...domRest } = rest;

  return (
    <div
      ref={ref}
      style={{ "--rd": `${delay}ms`, ...style }}
      className={cn(
        "reveal",
        variant === "sm" && "reveal-sm",
        variant === "scale" && "reveal-scale",
        visible && "is-visible",
        className,
      )}
      {...domRest}
    >
      {children}
    </div>
  );
}
