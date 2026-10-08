"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Logo } from "@/components/logo";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { auth } from "@/lib/auth";
import api from "@/lib/api";

export function AppShell({ variant = "student", navItems, children }) {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState({});
  const isAdmin = variant === "admin";
  const initials = (user.fullName || user.email || "U")
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();

  useEffect(() => {
    const id = setTimeout(() => setUser(auth.getUser() || {}), 0);
    return () => clearTimeout(id);
  }, []);

  async function handleLogout() {
    try {
      const refreshToken = auth.getRefreshToken();
      if (refreshToken) {
        await api.post("/auth/logout", { refreshToken });
      }
    } catch {
      // proceed with local cleanup even if the server call fails
    } finally {
      auth.clear();
      router.replace("/login");
    }
  }

  return (
    <div className="flex min-h-screen bg-background">
      <aside
        className={`hidden w-60 shrink-0 flex-col border-r md:flex ${
          isAdmin ? "border-pine-800 bg-pine-950" : "border-border/60 bg-card"
        }`}
      >
        <div className="flex h-16 items-center gap-2 px-5">
          <Logo className="h-8 w-8" />
          <span className={`text-base font-bold tracking-tight ${isAdmin ? "text-white" : ""}`}>
            CODEIT CareerOS
          </span>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navItems.map((item) => {
            const active = pathname === item.href;
            const content = (
              <>
                <span className="inline-block w-5 text-base leading-none">{item.icon}</span>
                <span className="text-sm font-medium">{item.label}</span>
                {item.soon && (
                  <Badge
                    variant="secondary"
                    className={`ml-auto text-[10px] ${isAdmin ? "bg-pine-700 text-pine-200" : ""}`}
                  >
                    soon
                  </Badge>
                )}
              </>
            );
            return item.soon || !item.href ? (
              <div
                key={item.label}
                title={item.soon ? "Coming in a later sprint" : undefined}
                className={`flex cursor-not-allowed items-center gap-3 rounded-lg px-3 py-2.5 opacity-60 ${
                  isAdmin ? "text-pine-200" : "text-muted-foreground"
                }`}
              >
                {content}
              </div>
            ) : (
              <Link
                key={item.label}
                href={item.href}
                className={`flex items-center gap-3 rounded-lg px-3 py-2.5 transition ${
                  active
                    ? isAdmin
                      ? "bg-pine-800 text-white"
                      : "bg-primary/10 font-semibold text-primary"
                    : isAdmin
                      ? "text-pine-200 hover:bg-pine-800 hover:text-white"
                      : "text-muted-foreground hover:bg-accent hover:text-foreground"
                }`}
              >
                {content}
              </Link>
            );
          })}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 shrink-0 items-center justify-between gap-4 border-b border-border/60 bg-card/80 px-6 backdrop-blur">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">
              {isAdmin ? "Admin Dashboard" : "Welcome, " + (user.fullName || "Student")}
            </p>
            {!isAdmin && <p className="truncate text-xs text-muted-foreground">Your career journey at a glance</p>}
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium leading-tight">{user.fullName || user.email}</p>
              <p className="text-xs text-muted-foreground">{user.role || ""}</p>
            </div>
            <Avatar className="h-9 w-9">
              <AvatarFallback className={isAdmin ? "bg-brand-600 text-white" : "bg-primary/15 text-primary"}>
                {initials}
              </AvatarFallback>
            </Avatar>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              Sign out
            </Button>
          </div>
        </header>
        <main className="flex-1 overflow-x-hidden p-6 md:p-8">{children}</main>
      </div>
    </div>
  );
}