import { AppShell } from "@/components/shell/app-shell";
import { studentNav } from "@/components/shell/nav-items";

export const metadata = { title: "My Career" };

export default function StudentLayout({ children }) {
  return (
    <AppShell items={studentNav} variant="student" title="Student Portal">
      {children}
    </AppShell>
  );
}