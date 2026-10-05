import {
  Share2,
  Target,
} from "lucide-react";
import { ComingSoon } from "@/components/coming-soon";

const modules = {
  careers: {
    title: "Career Selection",
    icon: Target,
    sprint: "Sprint 2",
    description: "Browse the admin-curated career catalog and lock in your target career.",
    bullets: [
      "Explore careers with required skills and weights",
      "Set or change your target career anytime",
      "Unlocks assessments, gaps and your roadmap",
    ],
  },
  linkedin: {
    title: "LinkedIn Analysis",
    icon: Share2,
    sprint: "Sprint 6",
    description: "Make your professional profile recruiter-ready with guided checks.",
    bullets: [
      "Profile completeness score",
      "Headline and about-section suggestions",
      "Recruiter-readiness checklist",
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

export default async function StudentComingSoonPage({ params }) {
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