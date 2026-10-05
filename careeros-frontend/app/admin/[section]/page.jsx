import {
  Bot,
  Briefcase,
  ClipboardList,
  FileBarChart,
  FolderKanban,
  Lightbulb,
  Route,
  Settings,
  Users,
} from "lucide-react";
import { ComingSoon } from "@/components/coming-soon";

const modules = {
  students: {
    title: "Students",
    icon: Users,
    sprint: "Sprint 2",
    description: "Search, inspect and manage every student account on the platform.",
    bullets: [
      "Search and filter student profiles",
      "View education and career details",
      "Monitor account status",
    ],
  },
  careers: {
    title: "Careers",
    icon: Briefcase,
    sprint: "Sprint 2",
    description: "Configure the career master that powers assessments, gaps and roadmaps.",
    bullets: [
      "Create and edit careers",
      "Configure required skills and weights",
      "Publish careers to students",
    ],
  },
  skills: {
    title: "Skills",
    icon: Lightbulb,
    sprint: "Sprint 2",
    description: "Maintain the skill catalog and competency framework.",
    bullets: [
      "Maintain the skill catalog",
      "Group skills into categories",
      "Map skills to careers with weights",
    ],
  },
  assessments: {
    title: "Assessments",
    icon: ClipboardList,
    sprint: "Sprint 2",
    description: "Build role-specific assessments from the question bank.",
    bullets: [
      "Compose assessments per career",
      "Configure timing and attempts",
      "Publish to target careers",
    ],
  },
  roadmaps: {
    title: "Roadmaps",
    icon: Route,
    sprint: "Sprint 4",
    description: "Design the 30/60/90-day roadmap templates students follow.",
    bullets: [
      "Design phase-wise templates",
      "Map items to skills",
      "Auto-assign on career selection",
    ],
  },
  projects: {
    title: "Projects",
    icon: FolderKanban,
    sprint: "Sprint 4",
    description: "Curate the project catalog recommended against student skill gaps.",
    bullets: [
      "Curate recommended projects",
      "Map technologies and outcomes",
      "Suggest based on skill gaps",
    ],
  },
  reports: {
    title: "Reports",
    icon: FileBarChart,
    sprint: "Sprint 6",
    description: "Access and audit every generated Career Readiness Report.",
    bullets: [
      "Browse generated reports",
      "Download PDF reports",
      "Track report history",
    ],
  },
  ai: {
    title: "AI Configuration",
    icon: Bot,
    sprint: "Sprint 7",
    description: "Configure the AI provider, prompts and tool access for the Career Assistant.",
    bullets: [
      "Configure AI provider and keys",
      "Tune prompts and tool access",
      "Monitor AI usage and limits",
    ],
  },
  settings: {
    title: "Settings",
    icon: Settings,
    sprint: "a later sprint",
    description: "Platform preferences, roles and tenant configuration.",
    bullets: [
      "Platform preferences",
      "Roles and permissions",
      "Tenant configuration",
    ],
  },
};

function prettify(section) {
  return (
    modules[section]?.title ||
    section.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())
  );
}

export async function generateMetadata({ params }) {
  const { section } = await params;
  return { title: `${prettify(section)} — Coming soon` };
}

export default async function AdminComingSoonPage({ params }) {
  const { section } = await params;
  const mod = modules[section];
  return (
    <ComingSoon
      title={prettify(section)}
      icon={mod?.icon}
      sprint={mod?.sprint}
      description={mod?.description}
      bullets={mod?.bullets}
    />
  );
}