const ACCESS_KEY = "careeros_access";
const REFRESH_KEY = "careeros_refresh";
const USER_KEY = "careeros_user";
const COOKIE_NAME = "careeros_token";

function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(
      decodeURIComponent(
        Array.prototype.map
          .call(atob(normalized), (c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
          .join(""),
      ),
    );
  } catch {
    return null;
  }
}

function isTokenExpired(token) {
  if (!token) return true;
  const payload = decodeJwt(token);
  if (!payload) return true;
  if (typeof payload.exp !== "number") return false;
  // `exp` is seconds since epoch. Add a small clock-skew buffer.
  const nowSec = Math.floor(Date.now() / 1000);
  return payload.exp <= nowSec + 10;
}

function cookieSecureFlag() {
  if (typeof window !== "undefined" && window.location?.protocol === "https:") return "; Secure";
  return "";
}

function setCookie(name, value) {
  document.cookie = `${name}=${value}; path=/; SameSite=Lax; max-age=604800${cookieSecureFlag()}`;
}

function clearCookie(name) {
  document.cookie = `${name}=; path=/; SameSite=Lax; expires=Thu, 01 Jan 1970 00:00:00 GMT${cookieSecureFlag()}`;
}

function notifyAuthChanged() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("careeros:auth-changed"));
  }
}

export const auth = {
  save({ accessToken, refreshToken, user }) {
    if (typeof window === "undefined") return;
    localStorage.setItem(ACCESS_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    setCookie(COOKIE_NAME, accessToken);
    notifyAuthChanged();
  },

  getAccessToken() {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(ACCESS_KEY);
  },

  getRefreshToken() {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(REFRESH_KEY);
  },

  getUser() {
    if (typeof window === "undefined") return null;
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  },

  getRole() {
    const token = this.getAccessToken();
    if (token && !isTokenExpired(token)) {
      // Prefer the live token claim; it reflects the current session.
      // Falls back to the cached user only when the token has no role.
      const tokenRole = decodeJwt(token)?.role;
      if (tokenRole) return tokenRole;
    }
    // If the access token is expired/missing, do NOT trust the cached
    // user — the session may no longer be valid. Callers must refresh
    // or re-authenticate first.
    if (token && !isTokenExpired(token)) {
      const user = this.getUser();
      if (user?.role) return user.role;
    }
    return null;
  },

  isAuthenticated() {
    const token = this.getAccessToken();
    return Boolean(token) && !isTokenExpired(token);
  },

  clear() {
    if (typeof window !== "undefined") {
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(USER_KEY);
      clearCookie(COOKIE_NAME);
      notifyAuthChanged();
      return;
    }
    if (typeof localStorage !== "undefined") {
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(USER_KEY);
    }
  },
};

export { decodeJwt, isTokenExpired, COOKIE_NAME };