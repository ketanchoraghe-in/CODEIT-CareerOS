/** @type {import('next').NextConfig} */
const isProd = process.env.NODE_ENV === "production";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
  // HSTS only in production builds: never pin localhost during development.
  ...(isProd
    ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }]
    : []),
];

const nextConfig = {
  // Hide the floating Next.js dev indicator (black "N" button) during development.
  devIndicators: false,
  // Self-contained server output for the production Docker image.
  output: "standalone",
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
  // Same-origin API proxy: browsers always call THIS domain (/api/*), so there
  // is no mixed-content block on HTTPS sites and no CORS involved. The Next
  // server forwards to the backend server-side.
  // API_PROXY_URL is baked at build time: http://backend:8080 in Docker,
  // http://localhost:8080 for local dev. Set NEXT_PUBLIC_API_URL only to
  // bypass the proxy and call a backend directly from the browser.
  async rewrites() {
    const target = (process.env.API_PROXY_URL || "http://localhost:8080").replace(/\/$/, "");
    return [{ source: "/api/:path*", destination: `${target}/api/:path*` }];
  },
};

export default nextConfig;
