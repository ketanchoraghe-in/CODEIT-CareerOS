package com.codeit.careeros.config;

import com.codeit.careeros.assessment.AssessmentQuestion;
import com.codeit.careeros.assessment.AssessmentTest;
import com.codeit.careeros.assessment.QuestionOption;
import com.codeit.careeros.career.Career;
import com.codeit.careeros.career.CareerSkill;
import com.codeit.careeros.common.enums.CareerCategory;
import com.codeit.careeros.common.enums.DifficultyLevel;
import com.codeit.careeros.common.enums.QuestionType;
import com.codeit.careeros.common.enums.SkillCategory;
import com.codeit.careeros.common.enums.SkillLevel;
import com.codeit.careeros.repository.AssessmentQuestionRepository;
import com.codeit.careeros.repository.AssessmentTestRepository;
import com.codeit.careeros.repository.CareerRepository;
import com.codeit.careeros.repository.CareerSkillRepository;
import com.codeit.careeros.repository.ProjectRepository;
import com.codeit.careeros.repository.ProjectSkillRepository;
import com.codeit.careeros.repository.QuestionOptionRepository;
import com.codeit.careeros.repository.RoadmapItemRepository;
import com.codeit.careeros.repository.RoadmapPhaseRepository;
import com.codeit.careeros.repository.SkillRepository;
import com.codeit.careeros.project.Project;
import com.codeit.careeros.project.ProjectSkill;
import com.codeit.careeros.roadmap.RoadmapItem;
import com.codeit.careeros.roadmap.RoadmapPhase;
import com.codeit.careeros.skill.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds the database with the skill master, the career master with its
 * competency framework, and example assessments (docs sections 10-12 and 14),
 * plus Sprint 4 roadmap phases/steps and recommended projects generated from
 * each career's own competency framework.
 * Runs in dev always, and in prod ONLY to fill an empty catalog (first
 * deploy): the first seed runs only when no skills exist, and later runs
 * insert only catalog rows that are still missing (matched by name).
 * Idempotent: existing careers, skills, assessments, roadmaps, projects,
 * users and attempts are never modified or deleted, and restarts insert
 * nothing twice. Never runs in the test profile so integration tests stay
 * hermetic.
 */
@Slf4j
@Component
@Profile({"dev", "prod"})
@RequiredArgsConstructor
public class MasterDataSeeder implements ApplicationRunner {

    private final SkillRepository skillRepository;
    private final CareerRepository careerRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final AssessmentTestRepository assessmentTestRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final RoadmapPhaseRepository roadmapPhaseRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final ProjectRepository projectRepository;
    private final ProjectSkillRepository projectSkillRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (skillRepository.count() == 0) {
            Map<String, Skill> skills = seedSkills();
            List<Career> careers = seedCareers(skills);
            MasterDataCatalog.seedAssessments(
                    skills, careers, assessmentTestRepository, questionRepository, optionRepository);
            log.info("Master data seeded: {} skills, {} careers, {} assessments",
                    skillRepository.count(), careerRepository.count(), assessmentTestRepository.count());
        } else {
            // Existing database: insert only catalog rows that are still
            // missing (new skills, new careers, new assessment questions).
            // Existing careers, skills, assessments, users and attempts are
            // never modified (except assessment durations, which are synced to
            // the fixed 20-minute policy) or deleted, and restarts insert
            // nothing twice.
            Map<String, Skill> skills = backfillSkillsAndCareers();
            Map<String, Career> careers = careerRepository.findAll().stream()
                    .collect(Collectors.toMap(Career::getName, Function.identity(), (a, b) -> a));
            int added = MasterDataCatalog.backfillAssessments(
                    skills, careers, assessmentTestRepository, questionRepository, optionRepository);
            log.info("Master data already present; backfilled catalog skills/careers and {} new questions", added);
        }
        ensureRoadmapsAndProjects();
    }

    /**
     * Inserts catalog skills and careers missing from an existing database
     * (e.g. a catalog expansion shipped after the first seed) without
     * touching any existing row. Idempotent: skills and careers are matched
     * by name, so restarts never duplicate data. New careers are published
     * immediately so students can select them.
     *
     * @return all skills keyed by name (existing plus newly inserted)
     */
    Map<String, Skill> backfillSkillsAndCareers() {
        int newSkills = 0;
        for (SkillSeed seed : SKILLS) {
            if (!skillRepository.existsByNameIgnoreCase(seed.name())) {
                skillRepository.save(Skill.builder()
                        .name(seed.name())
                        .category(seed.category())
                        .description(seed.description())
                        .active(true)
                        .build());
                newSkills++;
            }
        }
        Map<String, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getName, Function.identity(), (a, b) -> a));
        int newCareers = 0;
        for (CareerSeed seed : CAREERS) {
            if (careerRepository.findByNameIgnoreCase(seed.name()).isPresent()) {
                continue;
            }
            Career career = careerRepository.save(Career.builder()
                    .name(seed.name())
                    .description(seed.description())
                    .category(seed.category())
                    .difficultyLevel(seed.difficulty())
                    .published(true)
                    .build());
            for (CareerSkillSeed row : seed.skills()) {
                Skill skill = skills.get(row.skill());
                if (skill == null) {
                    continue;
                }
                careerSkillRepository.save(CareerSkill.builder()
                        .career(career)
                        .skill(skill)
                        .weightPercent(row.weight())
                        .requiredLevel(row.level())
                        .targetPercent(row.target())
                        .build());
            }
            newCareers++;
        }
        log.info("Career catalog backfill: {} new skills, {} new careers", newSkills, newCareers);
        return skills;
    }

    /**
     * Sprint 4 content: roadmap phases/steps and recommended projects for
     * every career, generated from each career's own competency framework
     * (skills ordered by framework weight). Guarded per career so databases
     * seeded before a catalog expansion are backfilled too.
     * Idempotent: careers that already have roadmap phases or projects are
     * skipped entirely.
     */
    void ensureRoadmapsAndProjects() {
        int phases = 0;
        int items = 0;
        int projects = 0;
        for (Career career : careerRepository.findAll()) {
            if (roadmapPhaseRepository.existsByCareerId(career.getId())
                    || projectRepository.existsByCareerId(career.getId())) {
                continue;
            }
            List<CareerSkill> framework =
                    careerSkillRepository.findByCareerIdOrderByWeightPercentDesc(career.getId());
            if (framework.isEmpty()) {
                continue;
            }
            int third = Math.max(1, framework.size() / 3);
            List<List<CareerSkill>> groups = List.of(
                    framework.subList(0, Math.min(third, framework.size())),
                    framework.subList(Math.min(third, framework.size()),
                            Math.min(third * 2, framework.size())),
                    framework.subList(Math.min(third * 2, framework.size()), framework.size()));

            String[] phaseTitles = {"Foundation", "Builder", "Mastery"};
            String[] phaseIntros = {
                    "Core skills every %s needs on day one.".formatted(career.getName()),
                    "Apply the fundamentals to realistic %s work.".formatted(career.getName()),
                    "Reach job-ready depth and prove it end to end."};
            String[] itemVerbs = {"%s essentials", "Applied %s", "%s mastery"};
            for (int p = 0; p < 3; p++) {
                RoadmapPhase phase = roadmapPhaseRepository.save(RoadmapPhase.builder()
                        .career(career)
                        .title(phaseTitles[p] + " — Days " + (p * 30 + 1) + "-" + ((p + 1) * 30))
                        .description(phaseIntros[p])
                        .displayOrder(p + 1)
                        .durationDays(30)
                        .build());
                phases++;
                int order = 1;
                for (CareerSkill row : groups.get(p)) {
                    roadmapItemRepository.save(RoadmapItem.builder()
                            .phase(phase)
                            .skill(row.getSkill())
                            .title(itemVerbs[p].formatted(row.getSkill().getName()))
                            .description(row.getSkill().getDescription())
                            .learningGoal("Reach %d%% competency in %s (%s level, %d%% career weight)."
                                    .formatted(row.getTargetPercent(), row.getSkill().getName(),
                                            row.getRequiredLevel().name(), row.getWeightPercent()))
                            .displayOrder(order++)
                            .estimatedHours(6 + row.getWeightPercent() / 5)
                            .build());
                    items++;
                }
                if (p == 2) {
                    roadmapItemRepository.save(RoadmapItem.builder()
                            .phase(phase)
                            .title(career.getName() + " capstone review")
                            .description("Combine every phase skill in one end-to-end build for your "
                                    + career.getName() + " portfolio.")
                            .learningGoal("Demonstrate all required skills working together.")
                            .displayOrder(order)
                            .estimatedHours(12)
                            .build());
                    items++;
                }
            }

            List<CareerSkill> topSkills = framework.subList(0, Math.min(4, framework.size()));
            Project starter = projectRepository.save(Project.builder()
                    .career(career)
                    .title(career.getName() + " Starter Project")
                    .description("A guided first build exercising "
                            + topSkills.subList(0, Math.min(3, topSkills.size())).stream()
                                    .map(row -> row.getSkill().getName())
                                    .collect(java.util.stream.Collectors.joining(", "))
                            + ". Ideal once the Foundation phase is underway.")
                    .difficulty(career.getDifficultyLevel())
                    .estimatedWeeks(2)
                    .displayOrder(1)
                    .published(true)
                    .build());
            Project capstone = projectRepository.save(Project.builder()
                    .career(career)
                    .title(career.getName() + " Capstone Project")
                    .description("An end-to-end portfolio piece combining "
                            + topSkills.stream()
                                    .map(row -> row.getSkill().getName())
                                    .collect(java.util.stream.Collectors.joining(", "))
                            + ". Tackle it during the Mastery phase.")
                    .difficulty(career.getDifficultyLevel())
                    .estimatedWeeks(4)
                    .displayOrder(2)
                    .published(true)
                    .build());
            projects += 2;
            for (CareerSkill row : topSkills.subList(0, Math.min(3, topSkills.size()))) {
                projectSkillRepository.save(ProjectSkill.builder()
                        .project(starter).skill(row.getSkill()).build());
            }
            for (CareerSkill row : topSkills) {
                projectSkillRepository.save(ProjectSkill.builder()
                        .project(capstone).skill(row.getSkill()).build());
            }
        }
        log.info("Roadmap/project seed: {} phases, {} items, {} projects",
                phases, items, projects);
    }

    Map<String, Skill> seedSkills() {
        Map<String, Skill> byName = new HashMap<>();
        for (SkillSeed seed : SKILLS) {
            byName.put(seed.name(), skillRepository.save(Skill.builder()
                    .name(seed.name())
                    .category(seed.category())
                    .description(seed.description())
                    .active(true)
                    .build()));
        }
        return byName;
    }

    List<Career> seedCareers(Map<String, Skill> skills) {
        List<Career> careers = new java.util.ArrayList<>();
        for (CareerSeed seed : CAREERS) {
            Career career = careerRepository.save(Career.builder()
                    .name(seed.name())
                    .description(seed.description())
                    .category(seed.category())
                    .difficultyLevel(seed.difficulty())
                    .published(true)
                    .build());
            for (CareerSkillSeed row : seed.skills()) {
                careerSkillRepository.save(CareerSkill.builder()
                        .career(career)
                        .skill(skills.get(row.skill()))
                        .weightPercent(row.weight())
                        .requiredLevel(row.level())
                        .targetPercent(row.target())
                        .build());
            }
            careers.add(career);
        }
        return careers;
    }

    record SkillSeed(String name, SkillCategory category, String description) {
    }

    record CareerSkillSeed(String skill, int weight, SkillLevel level, int target) {
    }

    record CareerSeed(String name, String description, CareerCategory category,
                      DifficultyLevel difficulty, List<CareerSkillSeed> skills) {
    }

    static java.util.function.Function<String, CareerSkillSeed> cs(int weight, SkillLevel level, int target) {
        return skill -> new CareerSkillSeed(skill, weight, level, target);
    }

    private static final List<SkillSeed> SKILLS = List.of(
            new SkillSeed("Java", SkillCategory.PROGRAMMING_LANGUAGES, "Object-oriented language for enterprise applications"),
            new SkillSeed("Python", SkillCategory.PROGRAMMING_LANGUAGES, "High-level language used for scripting, data and automation"),
            new SkillSeed("JavaScript", SkillCategory.PROGRAMMING_LANGUAGES, "Core language of the web, runs in every browser"),
            new SkillSeed("TypeScript", SkillCategory.PROGRAMMING_LANGUAGES, "Typed superset of JavaScript for scalable applications"),
            new SkillSeed("SQL", SkillCategory.PROGRAMMING_LANGUAGES, "Query language for relational databases"),
            new SkillSeed("HTML/CSS", SkillCategory.PROGRAMMING_LANGUAGES, "Structure and styling of web pages"),
            new SkillSeed("Spring Boot", SkillCategory.FRAMEWORKS, "Java framework for production-ready REST services"),
            new SkillSeed("React", SkillCategory.FRAMEWORKS, "JavaScript library for building user interfaces"),
            new SkillSeed("Node.js", SkillCategory.FRAMEWORKS, "JavaScript runtime for server-side applications"),
            new SkillSeed("Django", SkillCategory.FRAMEWORKS, "Python web framework with batteries included"),
            new SkillSeed("MySQL", SkillCategory.DATABASES, "Popular open-source relational database"),
            new SkillSeed("PostgreSQL", SkillCategory.DATABASES, "Advanced open-source relational database"),
            new SkillSeed("MongoDB", SkillCategory.DATABASES, "Document-oriented NoSQL database"),
            new SkillSeed("AWS", SkillCategory.CLOUD, "Amazon Web Services cloud platform"),
            new SkillSeed("Azure", SkillCategory.CLOUD, "Microsoft cloud platform"),
            new SkillSeed("Docker", SkillCategory.CLOUD, "Containerization of applications"),
            new SkillSeed("Kubernetes", SkillCategory.CLOUD, "Orchestration of container workloads"),
            new SkillSeed("CI/CD (Jenkins)", SkillCategory.DEVOPS, "Automated build, test and deployment pipelines"),
            new SkillSeed("Git & GitHub", SkillCategory.DEVOPS, "Version control and collaborative code hosting"),
            new SkillSeed("Linux Administration", SkillCategory.DEVOPS, "Managing servers and infrastructure on Linux"),
            new SkillSeed("Terraform", SkillCategory.DEVOPS, "Infrastructure as code for cloud provisioning"),
            new SkillSeed("Monitoring (Prometheus & Grafana)", SkillCategory.DEVOPS, "Observability, metrics collection and dashboards"),
            new SkillSeed("Data Analysis", SkillCategory.DATA, "Exploring and interpreting structured data"),
            new SkillSeed("Data Visualization", SkillCategory.DATA, "Presenting data with charts and dashboards"),
            new SkillSeed("ETL & Data Pipelines", SkillCategory.DATA, "Extracting, transforming and loading enterprise data"),
            new SkillSeed("Statistics", SkillCategory.DATA, "Mathematical foundations for analysing data"),
            new SkillSeed("Power BI", SkillCategory.DATA, "Microsoft business intelligence and reporting"),
            new SkillSeed("Machine Learning", SkillCategory.AI_ML, "Training models from data for prediction"),
            new SkillSeed("Deep Learning", SkillCategory.AI_ML, "Neural networks for complex pattern recognition"),
            new SkillSeed("Network Security", SkillCategory.CYBERSECURITY, "Protecting networks from unauthorized access"),
            new SkillSeed("Application Security", SkillCategory.CYBERSECURITY, "Securing software from the development stage"),
            new SkillSeed("Security Operations (SOC)", SkillCategory.CYBERSECURITY, "Monitoring, detecting and responding to incidents"),
            new SkillSeed("Penetration Testing", SkillCategory.CYBERSECURITY, "Ethically exploiting systems to find vulnerabilities"),
            new SkillSeed("Cybersecurity Fundamentals", SkillCategory.CYBERSECURITY, "Core concepts of protecting information systems"),
            new SkillSeed("Risk & Compliance", SkillCategory.CYBERSECURITY, "Managing security risk and regulatory requirements"),
            new SkillSeed("Manual Testing", SkillCategory.TESTING, "Executing test cases by hand to find defects"),
            new SkillSeed("Automation Testing", SkillCategory.TESTING, "Automating test execution to speed up regression"),
            new SkillSeed("API Testing", SkillCategory.TESTING, "Verifying REST and web service contracts"),
            new SkillSeed("Communication", SkillCategory.SOFT_SKILLS, "Clearly exchanging information and ideas"),
            new SkillSeed("Problem Solving", SkillCategory.SOFT_SKILLS, "Analysing issues and finding effective solutions"),
            new SkillSeed("Team Collaboration", SkillCategory.SOFT_SKILLS, "Working effectively with others toward shared goals"),
            new SkillSeed("Agile & Scrum", SkillCategory.SOFT_SKILLS, "Iterative delivery and team rituals"),
            new SkillSeed("Leadership", SkillCategory.SOFT_SKILLS, "Guiding and motivating teams to deliver results"),
            new SkillSeed("Kotlin", SkillCategory.PROGRAMMING_LANGUAGES, "Modern language for Android development"),
            new SkillSeed("Swift", SkillCategory.PROGRAMMING_LANGUAGES, "Apple language for iOS and macOS apps"),
            new SkillSeed("Dart", SkillCategory.PROGRAMMING_LANGUAGES, "Language behind the Flutter UI toolkit"),
            new SkillSeed("Solidity", SkillCategory.PROGRAMMING_LANGUAGES, "Contract language for Ethereum-compatible blockchains"),
            new SkillSeed("Flutter", SkillCategory.FRAMEWORKS, "Cross-platform UI toolkit from a single codebase"),
            new SkillSeed("React Native", SkillCategory.FRAMEWORKS, "Native mobile apps built with React"),
            new SkillSeed("Generative AI", SkillCategory.AI_ML, "Large language models and generative systems"),
            new SkillSeed("Prompt Engineering", SkillCategory.AI_ML, "Designing effective prompts for AI models"),
            new SkillSeed("Natural Language Processing", SkillCategory.AI_ML, "Understanding and generating human language"),
            new SkillSeed("Selenium WebDriver", SkillCategory.TESTING, "Browser automation for UI regression tests"),
            new SkillSeed("Database Administration", SkillCategory.DATABASES, "Operating, securing and tuning databases"),
            new SkillSeed("Cloud Security", SkillCategory.CYBERSECURITY, "Securing workloads and identities in the cloud"),
            new SkillSeed("System Design", SkillCategory.OTHER, "Architecting scalable and reliable systems"),
            new SkillSeed("Microservices", SkillCategory.OTHER, "Decomposing systems into independently deployable services"),
            new SkillSeed("REST APIs", SkillCategory.OTHER, "Designing and consuming HTTP-based service contracts"),
            new SkillSeed("UI/UX Design", SkillCategory.OTHER, "Designing usable and accessible product experiences"),
            new SkillSeed("Figma", SkillCategory.OTHER, "Collaborative interface design and prototyping"),
            new SkillSeed("Blockchain Fundamentals", SkillCategory.OTHER, "Ledgers, consensus and decentralized applications"),
            new SkillSeed("Networking Fundamentals", SkillCategory.OTHER, "TCP/IP, DNS, routing and network troubleshooting"),
            new SkillSeed("System Administration", SkillCategory.OTHER, "Provisioning and maintaining servers and services"),
            new SkillSeed("Technical Troubleshooting", SkillCategory.OTHER, "Diagnosing and resolving technical issues systematically"));

    private static final List<CareerSeed> CAREERS = List.of(
            new CareerSeed("Java Developer", "Builds robust enterprise applications with Java and Spring Boot",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 80).apply("Java"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Spring Boot"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("PostgreSQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Git & GitHub"),
                            cs(10, SkillLevel.BASIC, 70).apply("Docker"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Python Developer", "Develops automation, back-end services and scripts in Python",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 80).apply("Python"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Django"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("SQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("PostgreSQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.BASIC, 65).apply("Docker"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(5, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Full Stack Developer", "Builds end-to-end web applications from database to UI",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(15, SkillLevel.ADVANCED, 80).apply("JavaScript"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("TypeScript"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("React"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Node.js"),
                            cs(5, SkillLevel.INTERMEDIATE, 70).apply("HTML/CSS"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("MongoDB"),
                            cs(5, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(5, SkillLevel.BASIC, 65).apply("Docker"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Backend Developer", "Designs APIs and data layers for web and mobile products",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 80).apply("Java"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("Spring Boot"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("PostgreSQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Git & GitHub"),
                            cs(10, SkillLevel.BASIC, 70).apply("Docker"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("DevOps Engineer", "Automates build, deployment and infrastructure operations",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(15, SkillLevel.ADVANCED, 80).apply("Linux Administration"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Docker"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Kubernetes"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("CI/CD (Jenkins)"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("AWS"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Terraform"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Monitoring (Prometheus & Grafana)"),
                            cs(5, SkillLevel.BASIC, 65).apply("Git & GitHub"))),
            new CareerSeed("Cloud Architect", "Designs scalable, secure cloud infrastructure and migration paths",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(25, SkillLevel.EXPERT, 85).apply("AWS"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Azure"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Kubernetes"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Docker"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Terraform"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(5, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Data Analyst", "Turns raw data into insights and reports for business decisions",
                    CareerCategory.DATA_AI, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(25, SkillLevel.INTERMEDIATE, 80).apply("Data Analysis"),
                            cs(20, SkillLevel.INTERMEDIATE, 80).apply("SQL"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Data Visualization"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Power BI"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Statistics"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Communication"))),
            new CareerSeed("Data Engineer", "Builds pipelines that move and transform enterprise data",
                    CareerCategory.DATA_AI, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 80).apply("SQL"),
                            cs(20, SkillLevel.INTERMEDIATE, 80).apply("Python"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("ETL & Data Pipelines"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("PostgreSQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Data Visualization"),
                            cs(10, SkillLevel.BASIC, 70).apply("Docker"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Statistics"))),
            new CareerSeed("Data Scientist", "Builds predictive models and communicates findings to stakeholders",
                    CareerCategory.DATA_AI, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 85).apply("Machine Learning"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Statistics"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Python"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Data Analysis"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Data Visualization"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Communication"))),
            new CareerSeed("Machine Learning Engineer", "Operationalizes ML models into production systems",
                    CareerCategory.DATA_AI, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 85).apply("Machine Learning"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Deep Learning"),
                            cs(25, SkillLevel.ADVANCED, 85).apply("Python"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Docker"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Kubernetes"),
                            cs(5, SkillLevel.BASIC, 65).apply("AWS"),
                            cs(5, SkillLevel.BASIC, 65).apply("SQL"))),
            new CareerSeed("Cybersecurity Analyst", "Monitors, defends and reports on organizational security",
                    CareerCategory.CYBERSECURITY, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 80).apply("Security Operations (SOC)"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Network Security"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Cybersecurity Fundamentals"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Application Security"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Risk & Compliance"),
                            cs(10, SkillLevel.BASIC, 65).apply("Linux Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Communication"))),
            new CareerSeed("Penetration Tester", "Finds and documents exploitable vulnerabilities in systems",
                    CareerCategory.CYBERSECURITY, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Penetration Testing"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Application Security"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Network Security"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Cybersecurity Fundamentals"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("QA Engineer", "Plans, executes and reports on software quality checks",
                    CareerCategory.QUALITY_ASSURANCE, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(30, SkillLevel.INTERMEDIATE, 80).apply("Manual Testing"),
                            cs(25, SkillLevel.INTERMEDIATE, 75).apply("Automation Testing"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("API Testing"),
                            cs(10, SkillLevel.BASIC, 65).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Communication"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("SDET / Automation Engineer", "Builds automated test suites that run in CI pipelines",
                    CareerCategory.QUALITY_ASSURANCE, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 80).apply("Automation Testing"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("API Testing"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Manual Testing"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Java"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("SQL"),
                            cs(5, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("IT Project Manager", "Plans, schedules and delivers technology projects with teams",
                    CareerCategory.BUSINESS, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 80).apply("Agile & Scrum"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Leadership"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Communication"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Team Collaboration"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Risk & Compliance"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Java Full Stack Developer", "Builds enterprise apps with Java, Spring Boot and React",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 80).apply("Java"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Spring Boot"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("React"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Git & GitHub"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Spring Boot Developer", "Creates production-grade REST services with Spring Boot",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 85).apply("Spring Boot"),
                            cs(25, SkillLevel.ADVANCED, 85).apply("Java"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(5, SkillLevel.BASIC, 65).apply("PostgreSQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Git & GitHub"),
                            cs(5, SkillLevel.BASIC, 65).apply("Docker"),
                            cs(5, SkillLevel.INTERMEDIATE, 70).apply("Problem Solving"))),
            new CareerSeed("Frontend Developer", "Crafts responsive interfaces with modern web tooling",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(20, SkillLevel.INTERMEDIATE, 80).apply("HTML/CSS"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("JavaScript"),
                            cs(20, SkillLevel.INTERMEDIATE, 75).apply("React"),
                            cs(10, SkillLevel.BASIC, 65).apply("TypeScript"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(5, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("React Developer", "Builds component-driven interfaces with React",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("React"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("JavaScript"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("TypeScript"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("HTML/CSS"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(5, SkillLevel.INTERMEDIATE, 70).apply("Problem Solving"))),
            new CareerSeed("JavaScript Developer", "Develops interactive web experiences with JavaScript",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 80).apply("JavaScript"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("HTML/CSS"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Node.js"),
                            cs(10, SkillLevel.BASIC, 65).apply("React"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Software Engineer", "Designs and ships reliable software across the stack",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(15, SkillLevel.INTERMEDIATE, 75).apply("Java"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Python"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("System Design"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Git & GitHub"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Problem Solving"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Communication"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Agile & Scrum"))),
            new CareerSeed("Software Developer", "Builds and maintains applications through the full lifecycle",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(20, SkillLevel.INTERMEDIATE, 80).apply("Python"),
                            cs(15, SkillLevel.BASIC, 65).apply("Java"),
                            cs(10, SkillLevel.BASIC, 65).apply("SQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(20, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Communication"),
                            cs(10, SkillLevel.BASIC, 65).apply("Agile & Scrum"))),
            new CareerSeed("Web Developer", "Builds websites and web apps from markup to deployment",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(25, SkillLevel.INTERMEDIATE, 80).apply("HTML/CSS"),
                            cs(25, SkillLevel.INTERMEDIATE, 80).apply("JavaScript"),
                            cs(10, SkillLevel.BASIC, 65).apply("Node.js"),
                            cs(10, SkillLevel.BASIC, 65).apply("SQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Application Developer", "Delivers business applications and integrations",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.INTERMEDIATE, 80).apply("Java"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Git & GitHub"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Communication"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Agile & Scrum"))),
            new CareerSeed("Django Developer", "Builds data-driven web apps with Python and Django",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Django"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("Python"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("PostgreSQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(5, SkillLevel.INTERMEDIATE, 70).apply("Problem Solving"))),
            new CareerSeed("Android Developer", "Builds native Android apps with Kotlin and Java",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Kotlin"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Java"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("SQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("iOS Developer", "Builds native iOS apps with Swift",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Swift"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("SQL"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("UI/UX Design"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Flutter Developer", "Ships cross-platform apps with Flutter and Dart",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 85).apply("Flutter"),
                            cs(25, SkillLevel.ADVANCED, 85).apply("Dart"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("UI/UX Design"),
                            cs(5, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("React Native Developer", "Builds native mobile apps with React Native",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 85).apply("React Native"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("JavaScript"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("React"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Blockchain Developer", "Builds decentralized apps and smart contracts",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 85).apply("Solidity"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Blockchain Fundamentals"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("JavaScript"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("System Design"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Application Security"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Software Architect", "Defines system architecture and technical direction",
                    CareerCategory.SOFTWARE_DEVELOPMENT, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(25, SkillLevel.EXPERT, 85).apply("System Design"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Microservices"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("AWS"),
                            cs(10, SkillLevel.ADVANCED, 80).apply("Java"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("SQL"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Leadership"),
                            cs(10, SkillLevel.ADVANCED, 80).apply("Communication"))),
            new CareerSeed("AI Engineer", "Designs and deploys applied AI systems",
                    CareerCategory.DATA_AI, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 85).apply("Machine Learning"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Deep Learning"),
                            cs(20, SkillLevel.ADVANCED, 85).apply("Python"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Natural Language Processing"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("Docker"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Generative AI Engineer", "Builds LLM-powered products and assistants",
                    CareerCategory.DATA_AI, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 85).apply("Generative AI"),
                            cs(20, SkillLevel.ADVANCED, 85).apply("Prompt Engineering"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Natural Language Processing"),
                            cs(20, SkillLevel.ADVANCED, 85).apply("Python"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Machine Learning"),
                            cs(5, SkillLevel.BASIC, 65).apply("REST APIs"),
                            cs(5, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Cloud Engineer", "Operates reliable infrastructure across clouds",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 80).apply("AWS"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Azure"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Terraform"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Docker"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Networking Fundamentals"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"))),
            new CareerSeed("AWS Cloud Engineer", "Designs and runs workloads on Amazon Web Services",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("AWS"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Terraform"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Docker"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Networking Fundamentals"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.BASIC, 65).apply("Monitoring (Prometheus & Grafana)"))),
            new CareerSeed("Azure Cloud Engineer", "Designs and runs workloads on Microsoft Azure",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Azure"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Terraform"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Docker"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Networking Fundamentals"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.BASIC, 65).apply("Monitoring (Prometheus & Grafana)"))),
            new CareerSeed("Cloud Developer", "Builds cloud-native services and integrations",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(20, SkillLevel.INTERMEDIATE, 80).apply("AWS"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Node.js"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Python"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Microservices"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Docker"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"))),
            new CareerSeed("Site Reliability Engineer", "Keeps production systems fast and dependable",
                    CareerCategory.CLOUD_DEVOPS, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 85).apply("Linux Administration"),
                            cs(20, SkillLevel.ADVANCED, 85).apply("Kubernetes"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Docker"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Monitoring (Prometheus & Grafana)"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("CI/CD (Jenkins)"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Cloud Security"),
                            cs(5, SkillLevel.INTERMEDIATE, 70).apply("Problem Solving"))),
            new CareerSeed("Software Testing Engineer", "Verifies software quality across releases",
                    CareerCategory.QUALITY_ASSURANCE, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(30, SkillLevel.INTERMEDIATE, 80).apply("Manual Testing"),
                            cs(20, SkillLevel.INTERMEDIATE, 75).apply("API Testing"),
                            cs(10, SkillLevel.BASIC, 65).apply("SQL"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Communication"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Agile & Scrum"))),
            new CareerSeed("Automation Test Engineer", "Automates regression suites for fast feedback",
                    CareerCategory.QUALITY_ASSURANCE, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Automation Testing"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Selenium WebDriver"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Java"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("API Testing"),
                            cs(10, SkillLevel.BASIC, 65).apply("CI/CD (Jenkins)"),
                            cs(5, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Selenium Automation Engineer", "Automates browser testing with Selenium",
                    CareerCategory.QUALITY_ASSURANCE, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(35, SkillLevel.ADVANCED, 85).apply("Selenium WebDriver"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("Automation Testing"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Java"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("Manual Testing"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Cybersecurity Engineer", "Designs defenses across networks, apps and cloud",
                    CareerCategory.CYBERSECURITY, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(20, SkillLevel.ADVANCED, 85).apply("Network Security"),
                            cs(20, SkillLevel.ADVANCED, 85).apply("Application Security"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Cloud Security"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Penetration Testing"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("Cybersecurity Fundamentals"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Security Analyst", "Triages alerts and investigates security events",
                    CareerCategory.CYBERSECURITY, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(25, SkillLevel.ADVANCED, 80).apply("Security Operations (SOC)"),
                            cs(20, SkillLevel.INTERMEDIATE, 75).apply("Cybersecurity Fundamentals"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Network Security"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Risk & Compliance"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Communication"),
                            cs(10, SkillLevel.BASIC, 65).apply("Linux Administration"))),
            new CareerSeed("SQL Developer", "Writes efficient queries and database logic",
                    CareerCategory.OTHER, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(35, SkillLevel.ADVANCED, 85).apply("SQL"),
                            cs(20, SkillLevel.INTERMEDIATE, 75).apply("Database Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("MySQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("PostgreSQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Database Developer", "Models data and builds database-backed features",
                    CareerCategory.OTHER, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("SQL"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Database Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("PostgreSQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("MySQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("ETL & Data Pipelines"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(5, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Database Administrator", "Keeps databases secure, backed up and performant",
                    CareerCategory.OTHER, DifficultyLevel.ADVANCED,
                    java.util.List.of(cs(30, SkillLevel.EXPERT, 85).apply("Database Administration"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("SQL"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(15, SkillLevel.ADVANCED, 80).apply("PostgreSQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("MySQL"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("MySQL Developer", "Builds applications on MySQL-backed data layers",
                    CareerCategory.OTHER, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("MySQL"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("SQL"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Database Administration"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.BASIC, 65).apply("Git & GitHub"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("Network Engineer", "Designs and maintains enterprise networks",
                    CareerCategory.OTHER, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("Networking Fundamentals"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Network Security"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Linux Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("System Administration"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Technical Troubleshooting"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"))),
            new CareerSeed("System Administrator", "Runs servers, identities and everyday IT operations",
                    CareerCategory.OTHER, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 85).apply("System Administration"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("Linux Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Networking Fundamentals"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Technical Troubleshooting"),
                            cs(10, SkillLevel.BASIC, 65).apply("Communication"),
                            cs(5, SkillLevel.INTERMEDIATE, 70).apply("Problem Solving"))),
            new CareerSeed("UI/UX Designer", "Designs intuitive interfaces and user journeys",
                    CareerCategory.OTHER, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(35, SkillLevel.ADVANCED, 85).apply("UI/UX Design"),
                            cs(25, SkillLevel.ADVANCED, 80).apply("Figma"),
                            cs(10, SkillLevel.BASIC, 65).apply("HTML/CSS"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Communication"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Team Collaboration"),
                            cs(5, SkillLevel.BASIC, 65).apply("Problem Solving"))),
            new CareerSeed("Technical Support Engineer", "Resolves customer technical issues end to end",
                    CareerCategory.OTHER, DifficultyLevel.BEGINNER,
                    java.util.List.of(cs(30, SkillLevel.ADVANCED, 80).apply("Technical Troubleshooting"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Communication"),
                            cs(15, SkillLevel.BASIC, 65).apply("Networking Fundamentals"),
                            cs(10, SkillLevel.BASIC, 65).apply("System Administration"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Team Collaboration"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"))),
            new CareerSeed("Solutions Engineer", "Bridges customer needs with technical solutions",
                    CareerCategory.OTHER, DifficultyLevel.INTERMEDIATE,
                    java.util.List.of(cs(15, SkillLevel.INTERMEDIATE, 75).apply("Technical Troubleshooting"),
                            cs(20, SkillLevel.ADVANCED, 80).apply("Communication"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("AWS"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("REST APIs"),
                            cs(10, SkillLevel.INTERMEDIATE, 70).apply("System Design"),
                            cs(15, SkillLevel.INTERMEDIATE, 75).apply("Problem Solving"),
                            cs(10, SkillLevel.INTERMEDIATE, 75).apply("Team Collaboration"))));
}