import { AppShell } from "@/components/shell/app-shell";
import { adminNav } from "@/components/shell/nav-items";

export const metadata = { title: "Admin" };

export default function AdminLayout({ children }) {
  return (
    <AppShell items={adminNav} variant="admin" title="Admin Console">
      {children}
    </AppShell>
  );
}