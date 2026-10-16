import { NextResponse } from "next/server";
import { decodeJwt, COOKIE_NAME } from "@/lib/auth";

const STUDENT_HOME = "/student/dashboard";
const ADMIN_HOME = "/admin/dashboard";
const LOGIN = "/login";

function payloadFromRequest(request) {
  const token = request.cookies.get(COOKIE_NAME)?.value;
  if (!token) return null;
  return decodeJwt(token);
}

function isExpiredPayload(payload) {
  if (!payload) return true;
  if (typeof payload.exp !== "number") return false;
  const nowSec = Math.floor(Date.now() / 1000);
  return payload.exp <= nowSec + 10;
}

function roleFromRequest(request) {
  // For guarding /student and /admin we accept the cookie even if the
  // short-lived access token just expired — the client can silently
  // refresh (refresh token lives 7 days) via the api interceptor.
  // An actually missing/invalid token still bounces to /login.
  return payloadFromRequest(request)?.role ?? null;
}

function validRoleFromRequest(request) {
  // For redirecting AWAY from /login and /register we require a
  // NON-expired token. An expired access token must NOT auto-open the
  // dashboard — the user should see the sign-in form again instead of
  // being silently logged in with a stale token.
  const payload = payloadFromRequest(request);
  if (!payload?.role) return null;
  if (isExpiredPayload(payload)) return null;
  return payload.role;
}

export function proxy(request) {
  const { pathname, searchParams } = request.nextUrl;
  const role = roleFromRequest(request);

  const isStudentArea = pathname.startsWith("/student");
  const isAdminArea = pathname.startsWith("/admin");
  const isAuthPage = pathname === "/login" || pathname === "/register";

  if (isStudentArea) {
    if (!role) return NextResponse.redirect(new URL(LOGIN, request.url));
    // Sprint 8: the student area is STUDENT-only; admins go to their console.
    if (role !== "STUDENT") return NextResponse.redirect(new URL(ADMIN_HOME, request.url));
  }

  if (isAdminArea) {
    if (!role) return NextResponse.redirect(new URL(LOGIN, request.url));
    if (role !== "ADMIN") return NextResponse.redirect(new URL(STUDENT_HOME, request.url));
  }

  if (isAuthPage) {
    // ?switch=1 / ?force=1 / ?logout=1 forces the form to render even
    // with a valid session (used by "Use another account"). The page
    // itself clears the stale session client-side.
    const forceForm =
      searchParams.get("switch") === "1" ||
      searchParams.get("force") === "1" ||
      searchParams.get("logout") === "1";
    if (forceForm) return NextResponse.next();

    const validRole = validRoleFromRequest(request);
    if (validRole) {
      const home = validRole === "ADMIN" ? ADMIN_HOME : STUDENT_HOME;
      return NextResponse.redirect(new URL(home, request.url));
    }
    // Expired/invalid token on an auth page: clear the stale cookie so
    // the browser doesn't keep sending it, then show the form.
    if (role) {
      const res = NextResponse.next();
      res.cookies.set(COOKIE_NAME, "", {
        path: "/",
        maxAge: 0,
        sameSite: "lax",
      });
      return res;
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/student/:path*", "/admin/:path*", "/login", "/register"],
};