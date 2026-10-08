"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, Menu, Sparkles, X } from "lucide-react";
import { LogoWordmark } from "@/components/logo";
import { cn } from "cn";

const navLinks = [
  { href: "/#features", label: "Features" },
  { href: "/#how-it-works", label: "How it works" },
];

function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [bannerVisible, setBannerVisible] = useState(true);

  useEffect(() => {
    function onScroll() {
      setScrolled(window.scrollY > 8);
    }
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <>
      {bannerVisible && (
        <div className="relative overflow-hidden bg-gradient-to-r from-pine-950 via-brand-800 to-pine-950 text-white">
          <div className="mx-auto flex max-w-7xl items-center justify-center gap-2 px-10 py-2 text-center text-xs font-medium sm:text-[13px]">
            <Sparkles className="size-3.5 shrink-0 text-brand-300" aria-hidden="true" />
            <p className="truncate">
              AI-guided career roadmaps are live —{" "}
              <Link
                href="/register"
                className="font-semibold underline underline-offset-2 transition hover:text-brand-200"
              >
                try it free
              </Link>
            </p>
          </div>
          <button
            type="button"
            onClick={() => setBannerVisible(false)}
            aria-label="Dismiss announcement"
            className="absolute top-1/2 right-3 -translate-y-1/2 rounded-full p-1 text-white/70 transition hover:bg-white/10 hover:text-white"
          >
            <X className="size-3.5" aria-hidden="true" />
          </button>
        </div>
      )}

      <header
        className={cn(
          "anim-nav-drop sticky top-0 z-40 border-b backdrop-blur-xl transition-all duration-300",
          scrolled
            ? "border-border/70 bg-background/85 shadow-[0_12px_32px_-16px_oklch(0.4_0.12_155/0.35)]"
            : "border-transparent bg-background/60",
        )}
      >
        <nav
          aria-label="Primary"
          className={cn(
            "mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 transition-all duration-300",
            scrolled ? "h-14" : "h-16",
          )}
        >
          <Link
            href="/"
            aria-label="CODEIT CareerOS home"
            className="group rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
          >
            <span className="block transition-transform duration-300 group-hover:scale-[1.04]">
              <LogoWordmark />
            </span>
          </Link>

          <div className="hidden items-center gap-8 md:flex">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className="relative py-1 text-sm font-medium text-muted-foreground transition-colors after:absolute after:-bottom-0.5 after:left-0 after:h-0.5 after:w-0 after:rounded-full after:bg-gradient-to-r after:from-brand-500 after:to-brand-700 after:transition-all after:duration-300 hover:text-foreground hover:after:w-full"
              >
                {link.label}
              </Link>
            ))}
          </div>

          <div className="hidden items-center gap-1.5 sm:flex">
            <Link
              href="/login"
              className="rounded-full px-4 py-2 text-sm font-medium text-muted-foreground transition hover:bg-brand-600/10 hover:text-brand-700"
            >
              Sign in
            </Link>
            <Link
              href="/register"
              className="group/cta relative inline-flex items-center gap-1.5 overflow-hidden rounded-full bg-gradient-to-r from-brand-700 via-brand-600 to-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all duration-300 hover:scale-[1.03] hover:shadow-xl hover:shadow-brand-600/40 hover:brightness-110 active:translate-y-px active:scale-[0.97]"
            >
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/30 to-transparent transition-transform duration-700 group-hover/cta:translate-x-full"
              />
              Get Started
              <ArrowRight
                className="size-4 transition-transform duration-300 group-hover/cta:translate-x-0.5"
                aria-hidden="true"
              />
            </Link>
          </div>

          <button
            type="button"
            onClick={() => setMenuOpen((v) => !v)}
            aria-expanded={menuOpen}
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            className="inline-flex size-10 items-center justify-center rounded-full border border-border/70 bg-card/70 text-foreground transition hover:bg-accent sm:hidden"
          >
            {menuOpen ? (
              <X className="size-5" aria-hidden="true" />
            ) : (
              <Menu className="size-5" aria-hidden="true" />
            )}
          </button>
        </nav>

        {menuOpen && (
          <div className="border-t border-border/60 bg-background/95 px-4 pt-2 pb-5 backdrop-blur-xl sm:hidden">
            <div className="flex animate-fade-in flex-col gap-1">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl px-3 py-2.5 text-sm font-medium text-muted-foreground transition hover:bg-brand-600/10 hover:text-brand-700"
                >
                  {link.label}
                </Link>
              ))}
              <div className="mt-2 grid grid-cols-2 gap-2">
                <Link
                  href="/login"
                  onClick={() => setMenuOpen(false)}
                  className="inline-flex h-10 items-center justify-center rounded-full border border-border text-sm font-semibold transition hover:bg-accent"
                >
                  Sign in
                </Link>
                <Link
                  href="/register"
                  onClick={() => setMenuOpen(false)}
                  className="inline-flex h-10 items-center justify-center gap-1.5 rounded-full bg-gradient-to-r from-brand-700 to-brand-500 text-sm font-semibold text-white shadow-md shadow-brand-600/25"
                >
                  Get Started
                </Link>
              </div>
            </div>
          </div>
        )}
      </header>
    </>
  );
}

export { Navbar };
