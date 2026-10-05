package com.codeit.careeros.ai;

import com.codeit.careeros.dto.insight.AttemptHistoryItem;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.insight.SkillGapResponse;
import com.codeit.careeros.service.CareerInsightService;
import com.codeit.careeros.service.CvAnalysisService;
import com.codeit.careeros.service.LinkedInService;
import com.codeit.careeros.service.ProjectService;
import com.codeit.careeros.service.RoadmapService;
import com.codeit.careeros.service.StudentProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in CareerOS guidance engine. Works with ZERO API key, ZERO network and
 * ZERO cost: it reads the student's verified CareerOS data through the existing
 * Sprint 1-6 services and composes a direct, practical markdown answer.
 *
 * <p>This is what makes the AI Guidance module "always respond" like a real
 * chatbot even when no LLM provider is configured. When an LLM IS configured,
 * {@link CareerAssistantService} prefers the model and only uses this engine
 * as a fallback.
 */
@Component
@RequiredArgsConstructor
public class OfflineGuidanceEngine {

    private final StudentProfileService studentProfileService;
    private final CareerInsightService careerInsightService;
    private final RoadmapService roadmapService;
    private final ProjectService projectService;
    private final CvAnalysisService cvAnalysisService;
    private final LinkedInService linkedInService;

    public record GuidanceResult(String reply, List<String> toolsUsed) {
    }

    public GuidanceResult reply(String message) {
        return reply(message, List.of());
    }

    /**
     * History-aware entry point. {@code history} holds recent raw message texts
     * (oldest first, both user + assistant turns) so follow-ups like
     * "Why is it useful?" or "How good am I at it?" resolve to the topic of the
     * previous turn instead of falling back to a generic digest.
     */
    public GuidanceResult reply(String message, List<String> history) {
        String raw = message == null ? "" : message.strip();
        String lower = raw.toLowerCase(Locale.ROOT);
        List<String> toolsUsed = new ArrayList<>();

        // Simple greetings get a warm, data-aware hello (no heavy tool calls).
        if (isGreeting(lower)) {
            String name = safeFirstName();
            toolsUsed.add("getStudentProfile");
            return new GuidanceResult(greetingReply(name), List.copyOf(toolsUsed));
        }
        if (isHelp(lower)) {
            toolsUsed.add("getStudentProfile");
            return new GuidanceResult(helpReply(safeFirstName()), List.copyOf(toolsUsed));
        }

        // Resolve follow-up pronouns ("it", "that", "why is it useful?" ...)
        // to the topic of the previous turn before any intent routing.
        String effectiveRaw = raw;
        String effectiveLower = lower;
        if (isFollowUp(lower)) {
            String resolved = resolveFollowUpTopic(history, raw);
            if (resolved != null && !resolved.isBlank()) {
                effectiveRaw = raw + " [context: " + resolved + "]";
                effectiveLower = effectiveRaw.toLowerCase(Locale.ROOT);
            }
        }

        // ROLE-SKILLS questions ("Which skills are needed to become a Java
        // Developer?"): answer in TWO parts — general skills for that role,
        // then a personal comparison against real CareerOS data. Explicitly
        // personal asks ("What are my skill gaps?") skip this and stay personal.
        if (isCareerSkillsQuestion(effectiveLower) && !hasExplicitPersonalAsk(effectiveLower)) {
            Snapshot snap = snapshot(toolsUsed);
            return new GuidanceResult(careerSkillsReply(snap, effectiveRaw, effectiveLower), List.copyOf(toolsUsed));
        }

        // MIXED questions: general explanation + personal CareerOS standing.
        // e.g. "Explain Spring Boot and tell me how good I am at it."
        if (isMixedQuestion(effectiveLower)) {
            Snapshot snap = snapshot(toolsUsed);
            if (!snap.hasTarget) {
                return new GuidanceResult(mixedNoTargetReply(snap, effectiveRaw), List.copyOf(toolsUsed));
            }
            return new GuidanceResult(mixedReply(snap, effectiveRaw, effectiveLower), List.copyOf(toolsUsed));
        }

        // GENERAL student questions: answer directly from general knowledge,
        // NEVER forced into career/profile/gap analysis, NEVER fabricated data.
        if (isGeneralQuestion(effectiveLower)) {
            Snapshot snap = null;
            // "Give me Java interview questions based on my weak skills" is
            // general content tailored by real gaps — needs data, still no fabrication.
            if (effectiveLower.contains("my weak") || effectiveLower.contains("my gap")
                    || effectiveLower.contains("based on my") || effectiveLower.contains("for me")) {
                snap = snapshot(toolsUsed);
            }
            return new GuidanceResult(generalReply(effectiveRaw, effectiveLower, snap), List.copyOf(toolsUsed));
        }

        // Gather data once (each guarded so one broken module never kills chat).
        Snapshot snap = snapshot(toolsUsed);
        if (!snap.hasTarget) {
            return new GuidanceResult(noTargetReply(snap, lower), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "roadmap", "learn next", "what should i learn", "next step",
                "focus", "30-day", "30 day", "plan", "week", "step should")) {
            return new GuidanceResult(roadmapReply(snap, raw), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "project", "portfolio", "build", "practice")) {
            return new GuidanceResult(projectsReply(snap), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "cv", "resume", "curriculum")) {
            return new GuidanceResult(cvReply(snap), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "linkedin")) {
            return new GuidanceResult(linkedInReply(snap), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "assessment", "test", "exam", "score", "result", "attempt", "quiz", "marks")) {
            return new GuidanceResult(assessmentReply(snap), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "gap", "missing", "weak", "improve", "skill", "readiness", "ready", "how am i doing")) {
            return new GuidanceResult(gapsReply(snap), List.copyOf(toolsUsed));
        }
        if (containsAny(lower, "career", "target", "profile", "who am i", "about me")) {
            return new GuidanceResult(profileReply(snap), List.copyOf(toolsUsed));
        }
        return new GuidanceResult(overviewReply(snap, raw), List.copyOf(toolsUsed));
    }

    // ------------------------------------------------- intent routing ----

    /**
     * Follow-ups are short messages that depend on the previous turn:
     * pronoun-only ("it", "that one"), "why is it useful?", "how good am I
     * at it?", "what should I improve?", "tell me more", "and then?".
     */
    private boolean isFollowUp(String lower) {
        String t = lower.trim();
        if (t.isEmpty()) {
            return false;
        }
        // A message that names its own topic ("Explain Spring Boot ... and tell
        // me how good I am at it") is standalone — its "it" resolves in-sentence
        // and must NOT be treated as a pronoun-only follow-up.
        if (t.matches("(?s).*(explain|describe|define|what is|what are|tell me about|give me|list|show)\\s+"
                + "(spring boot|springboot|java(?!script)|python|sql|oops|docker|react|javascript|typescript|angular|node|mongodb|kubernetes|hibernate|dsa|algorithm|jvm|microservice|rest api|maven|git)\\b.*")) {
            return false;
        }
        if (t.matches("^(it|that|this|that one|this one|more|tell me more|explain more|why\\?*|how\\?*)[?.!\\s]*$")) {
            return true;
        }
        boolean hasPronoun = t.contains(" it") || t.endsWith(" it") || t.equals("it")
                || t.contains("that ") || t.contains("this ") || t.contains("they ")
                || t.contains(" same ") || t.contains(" above ");
        boolean shortFollowUp = t.length() < 70 && containsAny(t,
                "why is", "why should", "why use", "how good am i", "how good i am",
                "how am i at", "am i good",
                "what should i improve", "what should i focus", "tell me more",
                "explain more", "give me more", "and what", "what about", "is it",
                "is that", "useful", "advantage", "benefit", "drawback");
        return (hasPronoun && t.length() < 120) || shortFollowUp;
    }

    /**
     * Finds the last substantial user topic in the conversation history so a
     * follow-up inherits it. History entries are raw texts, oldest first.
     */
    private String resolveFollowUpTopic(List<String> history, String currentRaw) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            String entry = history.get(i);
            if (entry == null) {
                continue;
            }
            String e = entry.strip();
            if (e.isEmpty() || e.equalsIgnoreCase(currentRaw.strip())) {
                continue;
            }
            // Skip previous follow-ups themselves; keep walking back.
            if (isFollowUp(e.toLowerCase(Locale.ROOT)) || isGreeting(e.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String topic = extractTopic(e);
            if (topic != null && !topic.isBlank()) {
                return e.length() > 220 ? e.substring(0, 220) : e;
            }
        }
        return null;
    }

    /**
     * Mixed = a general/technical topic PLUS a personal CareerOS ask in one
     * message ("Explain X and tell me how good I am at it",
     * "Java interview questions based on my weak skills").
     */
    private boolean isMixedQuestion(String lower) {
        boolean hasGeneralTopic = containsAny(lower,
                "explain ", "what is ", "what are ", "what does ", "define",
                "spring boot", "springboot", "trending", "interview question",
                "write a", "write me", "program", "code example", "tutorial",
                "difference between", " vs ", "how does ", "how do i use");
        boolean hasPersonalAsk = containsAny(lower,
                "how good am i", "how good i am", "how am i at", "am i good",
                "my level", "rate my", "rate me",
                "my skill", "my gap", "my weak", "based on my", "for me",
                "my readiness", "my progress", "should i learn", "should i improve",
                "which project should i", "what should i learn",
                "assess me", "my standing", "my score", "how am i doing");
        // Explicit two-part joiners almost always mean mixed.
        boolean twoPart = (lower.contains(" and ") || lower.contains(" + ")
                || lower.contains(" also ") || lower.contains(" plus "))
                && hasPersonalAsk;
        if (!(hasGeneralTopic && hasPersonalAsk) && !twoPart) {
            return false;
        }
        // Purely personal asks ("What are my skill gaps?") match bare question
        // words ("what are ") — require a CONCRETE topic signal (a technology,
        // trending, interview, program...) or it stays personalized, not mixed.
        if (hasExplicitPersonalAsk(lower) && !containsAny(lower,
                "spring", "trending", "interview question", "write a", "write me",
                "program", "code ", "tutorial", "difference between", " vs ",
                "how does ", "how do i use", "java", "python", "javascript",
                "typescript", "react", "angular", "node", "sql", "mysql",
                "mongodb", "docker", "kubernetes", "hibernate", "rest api",
                "microservice", "oops", "dsa", "algorithm", "maven", "git")) {
            return false;
        }
        return true;
    }

    /**
     * Pure general questions: no personal pronouns/data ask, just knowledge.
     * Must NOT be forced into career analysis.
     */
    private boolean isGeneralQuestion(String lower) {
        if (containsAny(lower,
                "my ", " mine", "i am ", "i'm ", "am i ", "my skill", "my gap",
                "my readiness", "my career", "my target", "my roadmap",
                "my project", "my cv", "my linkedin", "my assessment",
                "my score", "my progress", "for my target", "based on my")) {
            return false;
        }
        if (containsAny(lower,
                "roadmap", "readiness", "skill gap", "what skills am i missing",
                "what should i learn", "which roadmap", "my assessment")) {
            return false;
        }
        return containsAny(lower,
                "what is ", "what are ", "what does ", "define ", "explain ",
                "trending", "in demand", "worth learning", "interview question",
                "write a", "write me", "program", "code ", "example code",
                "tutorial", "difference between", " vs ", "how does ",
                "how do i use", "why is ", "why use ", "why should i learn",
                "benefits of ", "advantages of ", "disadvantages of",
                "hello world", "fibonacci", "palindrome", "sorting",
                "oops", "inheritance", "polymorphism", "encapsulation",
                "abstraction", "collection", "multithreading", "jvm", "jdk", "jre",
                "spring", "hibernate", "rest api", "microservice", "maven",
                "docker", "kubernetes", "python", "javascript", "typescript",
                "react", "angular", "node", "sql ", "mysql", "mongodb",
                "data structure", "algorithm", "recursion", "linked list",
                "binary tree", "java8", "java 8", "stream api", "lambda");
    }

    /** Best-effort topic extraction for general/mixed answers + skill matching. */
    private String extractTopic(String raw) {
        String t = raw.replaceAll("(?i)\\[context:.*?\\]", " ").strip();
        // Prefer explicit "X interview questions" / "explain X" / "what is X".
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:explain|what is|what are|what does|define|tell me about)\\s+([a-zA-Z0-9#+.\\- ]{2,40})")
                .matcher(t);
        if (m.find()) {
            return cleanTopic(m.group(1));
        }
        m = java.util.regex.Pattern
                .compile("(?i)([a-zA-Z0-9#+.\\- ]{2,30})\\s+(?:interview questions?|trending|tutorial|program|code|vs\\b)")
                .matcher(t);
        if (m.find()) {
            return cleanTopic(m.group(1));
        }
        for (String known : new String[]{"spring boot", "springboot", "spring", "java", "python",
                "javascript", "typescript", "react", "angular", "node", "sql", "mysql",
                "mongodb", "docker", "kubernetes", "hibernate", "rest api", "microservices",
                "oops", "dsa", "data structures", "algorithms", "maven", "git"}) {
            if (t.toLowerCase(Locale.ROOT).contains(known)) {
                return known.equals("springboot") ? "Spring Boot" : capitalizeTopic(known);
            }
        }
        String stripped = t.replaceAll("(?i)^(hi|hello|hey|please|can you|could you|give me|write|explain|tell me|what|why|how|is|are|do|does)\\s+", "")
                .replaceAll("[?.!]+$", "").strip();
        if (stripped.length() >= 2 && stripped.length() <= 50) {
            return stripped;
        }
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    private String cleanTopic(String raw) {
        String t = raw.replaceAll("(?i)\\b(please|java program|a java|the|a|an|in java|for beginners?|to me)\\b", " ")
                .replaceAll("(?i)^(is|are|was|were|do|does|did|can|could|will|would|should|has|have|what|which|give me|list|show me|show|tell me|write)\\s+", "")
                // Drop trailing clauses from two-part questions:
                // "Spring Boot and tell me how good I am at" -> "Spring Boot".
                .replaceAll("(?i)\\s+(and|also|plus|tell me|based on|with|for me|for my)\\b.*$", "")
                .replaceAll("\\s+", " ").strip();
        if (t.isBlank()) {
            return "Java";
        }
        return capitalizeTopic(t);
    }

    private String capitalizeTopic(String topic) {
        String t = topic.strip();
        if (t.equalsIgnoreCase("java")) {
            return "Java";
        }
        if (t.equalsIgnoreCase("spring boot") || t.equalsIgnoreCase("springboot")) {
            return "Spring Boot";
        }
        if (t.equalsIgnoreCase("sql")) {
            return "SQL";
        }
        if (t.equalsIgnoreCase("oops")) {
            return "OOPs";
        }
        if (t.length() <= 4) {
            return t.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    // ------------------------------------------------- general knowledge --

    /**
     * Direct general answer — no CareerOS data, no fabrication.
     * When {@code snap} is present the question asked for personal tailoring
     * ("based on my weak skills") and weak skills are woven in from REAL gaps.
     */
    private String generalReply(String raw, String lower, Snapshot snap) {
        String topic = extractTopic(raw);
        if (lower.contains("interview question")) {
            return interviewQuestionsReply(topic, lower, snap);
        }
        if (containsAny(lower, "write a", "write me", "program", "code example",
                "hello world", "fibonacci", "palindrome", "sorting", "example code")) {
            return codeReply(topic, lower);
        }
        if (lower.contains("trending") || lower.contains("in demand")
                || lower.contains("worth learning")) {
            return trendingReply(topic);
        }
        return explainReply(topic, lower);
    }

    private String explainReply(String topic, String lower) {
        String key = topic.toLowerCase(Locale.ROOT);
        if (key.contains("spring boot") || key.contains("springboot") || key.equals("spring")) {
            return "### 🌱 Spring Boot — explained\n\n"
                    + "**Spring Boot** is an opinionated Java framework that makes it easy to build "
                    + "production-ready **REST APIs and microservices** with minimal setup.\n\n"
                    + "- **Why it exists:** plain Spring needs lots of XML/config; Boot adds "
                    + "auto-configuration, an embedded server (Tomcat/Jetty) and starter dependencies.\n"
                    + "- **Core ideas:** `@SpringBootApplication`, auto-configuration, "
                    + "`application.properties/yml`, Spring Data JPA, Spring Security, Actuator.\n"
                    + "- **Typical flow:** Controller → Service → Repository → MySQL, "
                    + "exactly like this CareerOS backend.\n"
                    + "- **Why learn it:** most Java backend job postings in India ask for "
                    + "Spring Boot + REST + JPA. It pairs directly with your Java skill.\n\n"
                    + "### ✅ Learn it in order\n"
                    + "1. Java OOPs + Collections → 2. Maven + REST basics → "
                    + "3. Spring Core (DI) → 4. Spring Boot CRUD + JPA → 5. Security + JWT → "
                    + "6. One portfolio project (e.g. a task tracker API).\n\n"
                    + "Ask a follow-up like **\"Why is it useful?\"** or "
                    + "**\"Explain Spring Boot and tell me how good I am at it\"** for your personal standing.";
        }
        if (key.contains("java")) {
            return "### ☕ Java — explained\n\n"
                    + "**Java** is a general-purpose, object-oriented, platform-independent language "
                    + "(\"write once, run anywhere\" via the JVM). It powers enterprise backends, "
                    + "Android apps, and big-data tooling.\n\n"
                    + "- **Key traits:** statically typed, garbage-collected, rich standard library, "
                    + "huge ecosystem (Spring, Hibernate, Maven/Gradle).\n"
                    + "- **Core syllabus:** OOPs (inheritance, polymorphism, encapsulation, abstraction), "
                    + "Collections, Exceptions, Streams/Lambdas (Java 8+), Multithreading, JVM basics.\n"
                    + "- **Where it's used:** backend APIs (Spring Boot), banking/fintech systems, Android.\n"
                    + "- **Is it trending?** Yes — Java + Spring Boot remains one of the most "
                    + "in-demand backend stacks for entry-level jobs in 2026.\n\n"
                    + "### ✅ How to get good\n"
                    + "1. OOPs + Collections → 2. Exception handling + Streams → "
                    + "3. One console project → 4. Spring Boot CRUD API → 5. Assessment + revise weak spots.\n\n"
                    + "Ask **\"Give me Java interview questions\"** or "
                    + "**\"Explain Java and tell me how good I am at Java\"** to link this to your CareerOS data.";
        }
        String display = topic.isBlank() ? "this topic" : topic;
        return "### 📘 " + display + " — explained\n\n"
                + "Here's the short version a student needs:\n\n"
                + "- **What it is:** " + display + " is a core concept in modern software development — "
                + "start from its definition, then one tiny working example, then where it's used in real projects.\n"
                + "- **Why it matters:** interviewers test fundamentals first; a crisp 3-line definition "
                + "plus one example beats a long memorised paragraph.\n"
                + "- **How to learn it fast:**\n"
                + "  1. Read the definition once\n"
                + "  2. Write/run one minimal example\n"
                + "  3. Explain it aloud in 30 seconds (Feynman check)\n"
                + "  4. Link it to one CareerOS roadmap item or project\n\n"
                + "Ask a sharper follow-up like **\"Give me " + display + " interview questions\"**, "
                + "**\"Write a " + display + " program\"**, or "
                + "**\"Explain " + display + " and tell me how good I am at it\"** and I'll go deeper.";
    }

    private String trendingReply(String topic) {
        String key = topic.toLowerCase(Locale.ROOT);
        boolean javaLike = key.contains("java") || key.contains("spring");
        String display = topic.isBlank() ? "It" : topic;
        StringBuilder sb = new StringBuilder();
        sb.append("### 📈 Is ").append(display).append(" trending?\n\n");
        if (javaLike) {
            sb.append("**Short answer: yes.** Java + Spring Boot is still one of the most "
                    + "in-demand backend stacks for fresher/entry-level roles in 2026.\n\n");
            sb.append("- **Demand:** thousands of backend postings ask for *Java, Spring Boot, REST, SQL*.\n");
            sb.append("- **Why it stays relevant:** banking, SaaS and enterprise systems run on the JVM; "
                    + "the ecosystem (Spring, Hibernate, Kafka) keeps evolving.\n");
            sb.append("- **Competition note:** it IS popular, so you stand out with a "
                    + "**finished CRUD project + clean CV bullets**, not just a certificate.\n\n");
            sb.append("### ✅ What to do\n");
            sb.append("1. Finish Java OOPs + Collections\n");
            sb.append("2. Build one Spring Boot REST API with MySQL\n");
            sb.append("3. Take the related assessment and close the top gap first.\n");
        } else {
            sb.append("**Short answer: fundamentals beat hype.** ").append(display)
                    .append(" is worth learning if it appears in your target-career postings; "
                            + "otherwise treat it as secondary.\n\n");
            sb.append("- Check 10 job postings for your target role and count how many mention **")
                    .append(display).append("**.\n");
            sb.append("- If 5+ mention it → put it in your next 2-week sprint; else park it.\n");
            sb.append("- Either way, Java/OOPs + SQL + one framework still carry most fresher interviews.\n");
        }
        sb.append("\nAsk **\"Explain ").append(display)
                .append(" and tell me how good I am at it\"** to map the trend to YOUR scores.");
        return sb.toString();
    }

    private String interviewQuestionsReply(String topic, String lower, Snapshot snap) {
        String key = topic.toLowerCase(Locale.ROOT);
        boolean javaLike = key.contains("java") && !key.contains("javascript");
        boolean springLike = key.contains("spring");
        StringBuilder sb = new StringBuilder();
        sb.append("### 🎤 ").append(topic.isBlank() ? "Interview" : topic)
                .append(" interview questions\n\n");
        if (snap != null && snap.gaps != null && !snap.gaps.isEmpty()
                && (lower.contains("my weak") || lower.contains("based on my") || lower.contains("weak skill"))) {
            sb.append("Tailored to YOUR real weak skills for **").append(snap.targetCareer).append("**:\n\n");
            int i = 1;
            for (SkillGapResponse g : snap.gaps.stream().limit(5).toList()) {
                sb.append(i++).append(". **").append(g.skillName()).append("** (your gap ")
                        .append(or0(g.gapPercent())).append("%) — *\"Explain ")
                        .append(g.skillName()).append(" with one example.\"*\n");
            }
            sb.append("\nThen the general ").append(topic).append(" set below.\n\n");
        }
        if (springLike) {
            sb.append("1. What is Spring Boot and how is it different from Spring?\n");
            sb.append("2. What does `@SpringBootApplication` do? What is auto-configuration?\n");
            sb.append("3. How do you build a REST controller (`@RestController`, `@GetMapping`)?\n");
            sb.append("4. How does dependency injection work (`@Service`, `@Autowired`/constructor injection)?\n");
            sb.append("5. How do you connect Spring Boot to MySQL with Spring Data JPA?\n");
            sb.append("6. How do you validate request bodies and handle exceptions (`@Valid`, `@ControllerAdvice`)?\n");
            sb.append("7. How do you secure APIs with Spring Security + JWT?\n");
            sb.append("8. What are profiles and `application.properties` vs `application.yml`?\n");
        } else if (javaLike || topic.equalsIgnoreCase("Java")) {
            sb.append("1. What are the four OOPs pillars? Give one real example of each.\n");
            sb.append("2. Difference between `==` and `.equals()`? Between `ArrayList` and `LinkedList`?\n");
            sb.append("3. What is the difference between JDK, JRE and JVM?\n");
            sb.append("4. How do `HashMap` internals work? What happens on collision?\n");
            sb.append("5. What are Streams and lambdas (Java 8)? Write `filter` + `map` on a list.\n");
            sb.append("6. Checked vs unchecked exceptions? How does `try-with-resources` work?\n");
            sb.append("7. What is multithreading? `synchronized` vs `volatile` vs `AtomicInteger`?\n");
            sb.append("8. Write a program to reverse a string / check a palindrome without library shortcuts.\n");
        } else {
            sb.append("1. What is ").append(topic).append(" in 2–3 lines?\n");
            sb.append("2. Where is ").append(topic).append(" used in a real project?\n");
            sb.append("3. What are its main advantages and one limitation?\n");
            sb.append("4. Show one minimal working example of ").append(topic).append(".\n");
            sb.append("5. What is a common beginner mistake with ").append(topic).append("?\n");
            sb.append("6. How would you debug a failing ").append(topic).append(" example?\n");
        }
        sb.append("\n### ✅ How to use this list\n");
        sb.append("- Answer each aloud in under 60 seconds, then write the code version.\n");
        if (snap != null && snap.targetCareer != null) {
            sb.append("- For **").append(snap.targetCareer)
                    .append("**, start with the questions matching your biggest gap");
            if (!snap.gaps.isEmpty()) {
                sb.append(" (**").append(snap.gaps.get(0).skillName()).append("**)");
            }
            sb.append(".\n");
        }
        return sb.toString();
    }

    private String codeReply(String topic, String lower) {
        String key = topic.toLowerCase(Locale.ROOT);
        if (key.contains("fibonacci") || lower.contains("fibonacci")) {
            return "### 💻 Fibonacci in Java\n\n"
                    + "```java\npublic class Fibonacci {\n"
                    + "    public static void main(String[] args) {\n"
                    + "        int n = 10, a = 0, b = 1;\n"
                    + "        for (int i = 0; i < n; i++) {\n"
                    + "            System.out.print(a + \" \");\n"
                    + "            int next = a + b;\n"
                    + "            a = b;\n"
                    + "            b = next;\n"
                    + "        }\n    }\n}\n```\n\n"
                    + "- **How it works:** keep two running numbers, print `a`, shift forward.\n"
                    + "- **Complexity:** O(n) time, O(1) space.\n"
                    + "- **Follow-up to try:** *\"Explain this code line by line\"* or "
                    + "*\"Write a palindrome program in Java\"*.";
        }
        if (key.contains("palindrome") || lower.contains("palindrome")) {
            return "### 💻 Palindrome check in Java\n\n"
                    + "```java\npublic class Palindrome {\n"
                    + "    static boolean isPalindrome(String s) {\n"
                    + "        int left = 0, right = s.length() - 1;\n"
                    + "        while (left < right) {\n"
                    + "            if (s.charAt(left++) != s.charAt(right--)) return false;\n"
                    + "        }\n"
                    + "        return true;\n    }\n"
                    + "    public static void main(String[] args) {\n"
                    + "        System.out.println(isPalindrome(\"madam\")); // true\n"
                    + "    }\n}\n```\n\n"
                    + "- **How it works:** two pointers walk inward; any mismatch → not a palindrome.\n"
                    + "- **Complexity:** O(n) time, O(1) space.";
        }
        return "### 💻 Hello World in Java\n\n"
                + "```java\npublic class HelloWorld {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello, CareerOS!\");\n"
                + "    }\n}\n```\n\n"
                + "- **Compile & run:** `javac HelloWorld.java && java HelloWorld`\n"
                + "- **`main` signature:** entry point; `String[] args` receives CLI arguments.\n"
                + "- **Next steps:** ask *\"Write a Fibonacci program in Java\"*, "
                + "*\"Explain OOPs with examples\"*, or *\"Give me Java interview questions\"*.";
    }

    // ------------------------------------------------- role-skills questions --

    /**
     * True when the message is a purely general-knowledge question with no
     * personal CareerOS angle (no "my …", no mixed two-part ask, no role
     * skills that invite a personal comparison). Used by
     * {@link CareerAssistantService} so that, when the configured LLM fails,
     * general questions get an honest "provider unavailable" message instead
     * of a hardcoded answer pretending to be the LLM. Greetings, help asks
     * and pronoun follow-ups return false so they keep their offline answers.
     */
    public boolean isGeneralKnowledgeOnly(String message) {
        String lower = message == null ? "" : message.strip().toLowerCase(Locale.ROOT);
        if (lower.isBlank() || isGreeting(lower) || isFollowUp(lower)) {
            return false;
        }
        // A bare "help"-style ask keeps the offline help answer; a "help me
        // write/explain …" task that is otherwise a general question does not.
        if (isHelp(lower) && !isGeneralQuestion(lower)) {
            return false;
        }
        if (isMixedQuestion(lower) || isCareerSkillsQuestion(lower) || hasExplicitPersonalAsk(lower)) {
            return false;
        }
        return isGeneralQuestion(lower);
    }

    /**
     * Explicitly personal asks stay purely personalized
     * ("What are my skill gaps?", "How ready am I?", "Which skills am I weak in?").
     */
    private boolean hasExplicitPersonalAsk(String lower) {
        return containsAny(lower,
                "my ", " mine", "am i ", "i'm ", "i am ", "for me",
                "based on my", "mine ", "my skill", "my gap", "my weak",
                "my readiness", "my career", "my target", "rate me", "assess me");
    }

    /**
     * "Which skills are needed to become a Java Developer?" — a general
     * role-skills question, NOT an immediate personal gap question.
     */
    private boolean isCareerSkillsQuestion(String lower) {
        if (!lower.contains("skill")) {
            return false;
        }
        boolean needWord = containsAny(lower,
                "need", "required", "requirement", "important", "necessary",
                "essential", "must know", "must-have", "should know", "learn to",
                "to learn", "takes to");
        boolean roleWord = containsAny(lower,
                "become", "be a ", "be an ", "for a ", "for an ", "role",
                "career", "job", "developer", "engineer", "analyst",
                "scientist", "devops", "tester", "designer", "admin");
        return needWord && roleWord;
    }

    /** Best-effort role name extraction ("... become a Java Developer"). */
    private String extractRole(String raw) {
        String t = raw.replaceAll("(?i)\\[context:.*?\\]", " ").toLowerCase(Locale.ROOT);
        String[] knownRoles = {"java developer", "backend developer", "frontend developer",
                "full stack developer", "full-stack developer", "data analyst",
                "data scientist", "data engineer", "devops engineer", "cloud architect",
                "cybersecurity analyst", "android developer", "software engineer",
                "web developer", "qa engineer", "test engineer"};
        for (String role : knownRoles) {
            if (t.contains(role)) {
                return capitalizeRole(role);
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)become an?\\s+([a-z][a-z .&+-]{1,40})")
                .matcher(raw);
        if (m.find()) {
            return capitalizeRole(m.group(1).replaceAll("[?.!]+$", "").strip());
        }
        m = java.util.regex.Pattern
                .compile("(?i)for (?:a |an |the )?([a-z][a-z .&+-]{1,40})\\s+(?:role|career|job|position)")
                .matcher(raw);
        if (m.find()) {
            return capitalizeRole(m.group(1).strip());
        }
        return null;
    }

    private String capitalizeRole(String role) {
        String[] words = role.strip().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.equalsIgnoreCase("qa") || w.equalsIgnoreCase("it")) {
                sb.append(w.toUpperCase(Locale.ROOT));
            } else if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            sb.append(' ');
        }
        return sb.toString().strip();
    }

    /** Curated general skill set per role (industry-standard, no fabrication of student data). */
    private List<String> roleSkills(String role) {
        if (role == null) {
            return List.of();
        }
        String key = role.toLowerCase(Locale.ROOT);
        if (key.contains("java developer")) {
            return List.of("Java + OOP (inheritance, polymorphism, encapsulation, abstraction)",
                    "Collections Framework (List, Set, Map)",
                    "Exception Handling + Java 8 Streams/Lambdas",
                    "SQL + JDBC/JPA basics",
                    "Spring Boot + REST APIs",
                    "Git & GitHub",
                    "Unit Testing (JUnit)",
                    "Problem Solving / DSA basics");
        }
        if (key.contains("backend")) {
            return List.of("One backend language (Java or Python) + OOP",
                    "REST API design",
                    "SQL + one database (MySQL/PostgreSQL)",
                    "A backend framework (Spring Boot / Django)",
                    "Git & GitHub",
                    "Docker basics",
                    "Unit Testing",
                    "Problem Solving / DSA basics");
        }
        if (key.contains("frontend") || key.contains("web developer")) {
            return List.of("HTML + CSS (responsive design)",
                    "JavaScript (ES6+) + DOM",
                    "TypeScript basics",
                    "React + component thinking",
                    "Consuming REST APIs",
                    "Git & GitHub",
                    "Basic testing",
                    "Problem Solving");
        }
        if (key.contains("data analyst")) {
            return List.of("SQL (joins, aggregations, subqueries)",
                    "Excel / Google Sheets",
                    "Python + pandas basics",
                    "Data visualization (Power BI / Tableau)",
                    "Statistics basics",
                    "Problem Solving",
                    "Communication of insights");
        }
        if (key.contains("data scientist") || key.contains("data engineer")) {
            return List.of("Python + OOP",
                    "SQL",
                    "Statistics + Pandas/NumPy",
                    "Machine-learning basics (scientist) / Pipelines + ETL (engineer)",
                    "Git & GitHub",
                    "Problem Solving");
        }
        if (key.contains("full stack")) {
            return List.of("HTML + CSS + JavaScript",
                    "React (frontend)",
                    "Java + Spring Boot (backend)",
                    "REST APIs",
                    "SQL + MySQL/PostgreSQL",
                    "Git & GitHub",
                    "Docker basics",
                    "Problem Solving");
        }
        return List.of("Programming fundamentals in one language",
                "Problem Solving / DSA basics",
                "SQL",
                "Git & GitHub",
                "One framework relevant to the role",
                "Unit Testing basics",
                "Communication + teamwork");
    }

    /**
     * Two-part answer: (1) general skills for the role, (2) personal comparison
     * against the student's REAL gaps/readiness. Never invents scores.
     */
    private String careerSkillsReply(Snapshot s, String raw, String lower) {
        String role = extractRole(raw);
        String displayRole = role == null ? "that role" : role;
        List<String> skills = roleSkills(role);
        StringBuilder sb = new StringBuilder();
        sb.append("### 🛠️ Skills needed to become ").append(displayRole).append("\n\n");
        sb.append("Generally, the important skills are:\n\n");
        int i = 1;
        for (String skill : skills) {
            sb.append(i++).append(". **").append(skill).append("**\n");
        }
        sb.append("\n---\n\n");
        sb.append("### 🙋 How this maps to YOU\n\n");
        if (!s.hasTarget) {
            sb.append("I checked your CareerOS data and you **haven't set a target career yet**, "
                    + "so I can't compare these against measured scores without guessing.\n\n"
                    + "1. Go to **Careers** → pick your goal → **Set as target**\n"
                    + "2. Take the related **Assessment**\n"
                    + "3. Ask again — I'll map every skill above to your real gaps instantly.\n");
            return sb.toString();
        }
        if (role != null && s.targetCareer != null
                && !s.targetCareer.equalsIgnoreCase(role)
                && !role.equalsIgnoreCase("that role")) {
            sb.append("_Note: your current CareerOS target is **").append(s.targetCareer)
                    .append("**, so the comparison below is against that framework._\n\n");
        }
        sb.append("Based on your CareerOS assessment: **readiness ").append(s.readinessPercent)
                .append("% (").append(s.readinessLevel).append(")** · ")
                .append(s.metSkills).append("/").append(s.totalSkills).append(" skills at target");
        if (s.assessedSkills == 0) {
            sb.append(" · _no skills assessed yet_\n\n");
        } else {
            sb.append(" · ").append(s.assessedSkills).append(" assessed\n\n");
        }
        List<String> unmatched = new ArrayList<>();
        boolean anyMatched = false;
        for (String skill : skills) {
            SkillGapResponse match = findSkillMatch(s, skill);
            if (match == null) {
                unmatched.add(skill);
                continue;
            }
            anyMatched = true;
            sb.append("- **").append(match.skillName()).append("** — target ")
                    .append(or0(match.targetPercent())).append("%, you: ")
                    .append(scoreOrDash(match.scorePercent()));
            if (Boolean.FALSE.equals(match.assessed()) || match.scorePercent() == null) {
                sb.append(" · _not yet assessed_");
            } else {
                sb.append(", gap **").append(or0(match.gapPercent())).append("%**");
            }
            sb.append("\n");
        }
        if (!anyMatched) {
            sb.append("- None of these role skills appear by name in your **").append(s.targetCareer)
                    .append("** framework, so I won't guess scores. ");
            if (!s.gaps.isEmpty()) {
                sb.append("Your biggest measured gap there is **")
                        .append(s.gaps.get(0).skillName()).append("**.\n");
            } else {
                sb.append("\n");
            }
        }
        if (!unmatched.isEmpty() && anyMatched) {
            sb.append("\n_Skills like ").append(unmatched.size() > 2
                            ? unmatched.get(0) + ", " + unmatched.get(1) + " and others"
                            : String.join(" and ", unmatched))
                    .append(" aren't named in your current target framework, "
                            + "but still help in interviews._\n");
        }
        if (s.assessedSkills == 0) {
            sb.append("\n### ✅ Next step\nTake the **Assessment** for **").append(s.targetCareer)
                    .append("** — then every skill above gets a real measured score here.\n");
        } else if (!s.gaps.isEmpty()) {
            sb.append("\n### ✅ Next step\nAttack your largest gap first — **")
                    .append(s.gaps.get(0).skillName()).append("** — then re-take the assessment.\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------- mixed questions ----

    private String mixedReply(Snapshot s, String raw, String lower) {
        String topic = extractTopic(raw);
        StringBuilder sb = new StringBuilder();
        // Part 1: general explanation (compact). If the question asks for
        // personal tailoring ("based on my weak skills"), weave REAL gaps in.
        boolean tailor = lower.contains("my weak") || lower.contains("my gap")
                || lower.contains("based on my") || lower.contains("for me");
        sb.append(generalReply(raw, lower, tailor ? s : null)).append("\n\n---\n\n");
        // Part 2: personal standing from REAL CareerOS data only.
        sb.append("### 🙋 How good YOU are at **").append(topic.isBlank() ? s.targetCareer : topic)
                .append("**\n\n");
        SkillGapResponse match = findSkillMatch(s, topic);
        sb.append("- **Target career:** ").append(s.targetCareer).append("\n");
        sb.append("- **Overall readiness:** ").append(s.readinessPercent).append("% (")
                .append(s.readinessLevel).append(") · ").append(s.metSkills).append("/")
                .append(s.totalSkills).append(" skills at target\n");
        if (match != null) {
            sb.append("- **").append(match.skillName()).append(":** target ")
                    .append(or0(match.targetPercent())).append("%, you: ")
                    .append(scoreOrDash(match.scorePercent())).append(", gap **")
                    .append(or0(match.gapPercent())).append("%**");
            if (Boolean.FALSE.equals(match.assessed())) {
                sb.append(" · _not yet assessed — take the relevant test to get a real score_");
            }
            sb.append("\n");
            if (Boolean.FALSE.equals(match.assessed()) || match.scorePercent() == null) {
                sb.append("- Verdict: ⚪ **not yet assessed** — take the relevant assessment "
                        + "to get a real score instead of guessing.\n");
            } else if (or0(match.gapPercent()) <= 0) {
                sb.append("- Verdict: ✅ **at/above target** — showcase it in your CV + project bullets.\n");
            } else if (or0(match.gapPercent()) <= 20) {
                sb.append("- Verdict: 🟡 **close** — one focused week (roadmap item + mini-project) should close it.\n");
            } else {
                sb.append("- Verdict: 🔴 **needs work** — make this your #1 focus this week.\n");
            }
        } else {
            sb.append("- No exact skill named **").append(topic)
                    .append("** in your target framework, so I will NOT guess a score.\n");
            if (!s.gaps.isEmpty()) {
                sb.append("- Your biggest measured gap right now is **")
                        .append(s.gaps.get(0).skillName()).append("** (")
                        .append(or0(s.gaps.get(0).gapPercent())).append("% behind).\n");
            }
        }
        if (!s.nextSteps.isEmpty()) {
            sb.append("- **Next step:** _").append(s.nextSteps.get(0)).append("_\n");
        }
        sb.append("\nAsk **\"What should I learn next?\"** for the ordered plan.");
        return sb.toString();
    }

    private String mixedNoTargetReply(Snapshot s, String raw) {
        String topic = extractTopic(raw);
        return generalReply(raw, raw.toLowerCase(Locale.ROOT), null)
                + "\n\n---\n\n"
                + "### 🙋 Your personal standing\n"
                + "I checked your CareerOS data and you **haven't set a target career yet**, "
                + "so I can't score you on **" + topic + "** without fabricating data.\n\n"
                + "1. Go to **Careers** → pick your goal → **Set as target**\n"
                + "2. Take the related **Assessment**\n"
                + "3. Come back and ask again — I'll map your real scores instantly.";
    }

    /** Matches a general topic to a REAL assessed skill (no guessing). */
    private SkillGapResponse findSkillMatch(Snapshot s, String topic) {
        if (s == null || topic == null || topic.isBlank()) {
            return null;
        }
        String key = topic.toLowerCase(Locale.ROOT).strip();
        // Check gaps first, then strengths.
        if (s.gaps != null) {
            for (SkillGapResponse g : s.gaps) {
                if (g.skillName() != null && skillMatches(g.skillName(), key)) {
                    return g;
                }
            }
        }
        if (s.strengths != null) {
            for (SkillGapResponse g : s.strengths) {
                if (g.skillName() != null && skillMatches(g.skillName(), key)) {
                    return g;
                }
            }
        }
        // "Java" should also match "Core Java" style names.
        return null;
    }

    private boolean skillMatches(String skillName, String topicKey) {
        String skill = skillName.toLowerCase(Locale.ROOT);
        if (skill.contains(topicKey) || topicKey.contains(skill)) {
            return true;
        }
        // Token overlap: "spring boot" vs "Spring Framework".
        for (String token : topicKey.split("\\s+")) {
            if (token.length() >= 3 && skill.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ data

    private record Snapshot(
            String studentName, String targetCareer, boolean hasTarget,
            int readinessPercent, String readinessLevel,
            int totalSkills, int assessedSkills, int metSkills, int attempts,
            List<SkillGapResponse> gaps, List<SkillGapResponse> strengths,
            AttemptHistoryItem latestAttempt, List<AttemptHistoryItem> history,
            int roadmapDone, int roadmapTotal, int roadmapPercent, List<String> nextSteps,
            int projectsDone, int projectsTotal, List<String> projectLines,
            boolean hasCv, int cvSkills, String cvCompleteness,
            boolean hasLinkedIn, int linkedInPercent, int linkedInAlignment) {
    }

    @SuppressWarnings("unchecked")
    private Snapshot snapshot(List<String> toolsUsed) {
        String name = "there";
        String career = null;
        boolean hasTarget = false;
        int readiness = 0;
        String level = "—";
        int total = 0, assessed = 0, met = 0, attempts = 0;
        List<SkillGapResponse> gaps = List.of();
        List<SkillGapResponse> strengths = List.of();
        AttemptHistoryItem latest = null;
        List<AttemptHistoryItem> history = List.of();
        int roadmapDone = 0, roadmapTotal = 0, roadmapPercent = 0;
        List<String> nextSteps = new ArrayList<>();
        int projectsDone = 0, projectsTotal = 0;
        List<String> projectLines = new ArrayList<>();
        boolean hasCv = false;
        int cvSkills = 0;
        String cvCompleteness = "—";
        boolean hasLinkedIn = false;
        int linkedInPercent = 0, linkedInAlignment = 0;

        try {
            var profile = studentProfileService.getCurrentProfile();
            toolsUsed.add("getStudentProfile");
            if (profile.fullName() != null && !profile.fullName().isBlank()) {
                name = profile.fullName().strip().split("\\s+")[0];
            }
            if (profile.targetCareerName() != null && !profile.targetCareerName().isBlank()) {
                career = profile.targetCareerName();
            }
        } catch (Exception ignored) {
        }
        try {
            ReadinessResponse r = careerInsightService.myReadiness();
            toolsUsed.add("getReadiness");
            toolsUsed.add("getSkillGaps");
            hasTarget = Boolean.TRUE.equals(r.hasTarget());
            if (r.targetCareerName() != null && !r.targetCareerName().isBlank()) {
                career = r.targetCareerName();
            }
            readiness = r.readinessPercent() == null ? 0 : r.readinessPercent();
            level = r.readinessLevel() == null ? "—" : r.readinessLevel();
            total = or0(r.totalSkills());
            assessed = or0(r.assessedSkills());
            met = or0(r.metSkills());
            attempts = or0(r.submittedAttempts());
            latest = r.latestAttempt();
            gaps = unmetGaps(r.gaps());
            strengths = r.strengths() == null ? List.of() : r.strengths();
        } catch (Exception ignored) {
        }
        try {
            history = careerInsightService.myHistory(null);
            toolsUsed.add("getAssessmentHistory");
            if (history == null) {
                history = List.of();
            }
        } catch (Exception ignored) {
        }
        try {
            var roadmap = roadmapService.myRoadmap();
            toolsUsed.add("getRoadmap");
            roadmapDone = or0(roadmap.completedItems());
            roadmapTotal = or0(roadmap.totalItems());
            roadmapPercent = or0(roadmap.progressPercent());
            if (roadmap.phases() != null) {
                for (var phase : roadmap.phases()) {
                    if (phase.items() != null) {
                        for (var item : phase.items()) {
                            if (!"COMPLETED".equalsIgnoreCase(String.valueOf(item.status()))) {
                                if (nextSteps.size() < 5) {
                                    nextSteps.add(item.title() + " (" + phase.title() + ")");
                                }
                            }
                        }
                    }
                    if (nextSteps.size() >= 5) {
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            var projects = projectService.recommended();
            toolsUsed.add("getProjects");
            projectsDone = or0(projects.completedProjects());
            projectsTotal = or0(projects.totalProjects());
            if (projects.projects() != null) {
                for (var p : projects.projects()) {
                    projectLines.add("**" + p.title() + "** — " + p.status()
                            + (p.difficulty() != null ? " · " + p.difficulty() : ""));
                }
            }
        } catch (Exception ignored) {
        }
        try {
            var cv = cvAnalysisService.analyze();
            toolsUsed.add("getCvAnalysis");
            hasCv = Boolean.TRUE.equals(cv.hasCv());
            cvSkills = cv.detectedSkills() == null ? 0 : cv.detectedSkills().size();
            cvCompleteness = cv.completeness() == null || cv.completeness().scorePercent() == null
                    ? "—" : cv.completeness().scorePercent() + "%";
        } catch (Exception ignored) {
        }
        try {
            var li = linkedInService.analyze();
            toolsUsed.add("getLinkedInAnalysis");
            hasLinkedIn = Boolean.TRUE.equals(li.hasProfile());
            linkedInPercent = or0(li.completenessPercent());
            linkedInAlignment = or0(li.alignmentPercent());
        } catch (Exception ignored) {
        }
        return new Snapshot(name, career, hasTarget, readiness, level, total, assessed, met,
                attempts, gaps, strengths, latest, history, roadmapDone, roadmapTotal,
                roadmapPercent, nextSteps, projectsDone, projectsTotal, projectLines,
                hasCv, cvSkills, cvCompleteness, hasLinkedIn, linkedInPercent, linkedInAlignment);
    }

    private List<SkillGapResponse> unmetGaps(List<SkillGapResponse> all) {
        if (all == null) {
            return List.of();
        }
        return all.stream()
                .filter(g -> !Boolean.TRUE.equals(g.metTarget()))
                .sorted(Comparator.comparingInt(g -> -(g.gapPercent() == null ? 0 : g.gapPercent())))
                .limit(8)
                .toList();
    }

    // ---------------------------------------------------------------- replies

    private String greetingReply(String name) {
        return "Hi " + name + "! 👋 I'm your **CareerOS Guide** — I can see your verified "
                + "profile, readiness, skill gaps, roadmap, projects, CV and LinkedIn.\n\n"
                + "Try one of these:\n"
                + "- **What skills am I missing for my target career?**\n"
                + "- **What should I learn next?**\n"
                + "- **Explain my assessment result**\n"
                + "- **Which roadmap step should I focus on?**\n"
                + "- **How can I improve my CV?**";
    }

    private String helpReply(String name) {
        return "Hi " + name + "! Here's what I can do — all grounded in **your actual CareerOS data**:\n\n"
                + "### 📊 Career readiness\n"
                + "Ask **\"How ready am I?\"** — I use your assessment scores vs your target career.\n\n"
                + "### 🎯 Skill gaps\n"
                + "Ask **\"What skills am I missing?\"** — biggest gaps first, with what to do next.\n\n"
                + "### 🗺️ Roadmap & projects\n"
                + "Ask **\"What should I learn next?\"** or **\"What projects should I complete?\"**\n\n"
                + "### 📄 CV & LinkedIn\n"
                + "Ask **\"How can I improve my CV?\"** or **\"Review my LinkedIn\"**\n\n"
                + "### 📝 Assessments\n"
                + "Ask **\"Explain my assessment result\"** for a plain-English breakdown.";
    }

    private String noTargetReply(Snapshot s, String lower) {
        return "Hi " + s.studentName + "! I checked your CareerOS data and you **haven't set a target career yet** — "
                + "so readiness, gaps, roadmap and project guidance can't be computed.\n\n"
                + "### ✅ Your next step (2 minutes)\n"
                + "1. Go to **Careers** in the sidebar\n"
                + "2. Pick the role closest to your goal (e.g. Frontend Developer, Data Analyst)\n"
                + "3. Click **Set as target**, then come back and ask me anything\n\n"
                + (s.attempts > 0
                        ? "You've already submitted **" + s.attempts + " assessment(s)** — great momentum. "
                                + "Once the target is set I'll map every score to it instantly.\n"
                        : "Then take your first **Assessment** so I can measure your real skill levels.\n")
                + "\nAsk me **\"What careers fit me?\"** after setting a target and I'll break it down.";
    }

    private String gapsReply(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🎯 Skill gaps for **").append(s.targetCareer).append("**\n\n");
        sb.append("**Readiness: ").append(s.readinessPercent).append("%** (").append(s.readinessLevel).append(") · ")
                .append(s.metSkills).append("/").append(s.totalSkills).append(" skills at target · ")
                .append(s.assessedSkills).append(" assessed\n\n");
        if (s.gaps.isEmpty()) {
            sb.append(s.assessedSkills == 0
                    ? "You haven't been assessed on these skills yet. Take an **Assessment** first — "
                            + "then I'll rank your exact gaps here.\n"
                    : "🎉 **No open gaps!** Every assessed skill meets its target. Keep your roadmap at 100% and "
                            + "strengthen your CV/LinkedIn to convert readiness into interviews.\n");
            return sb.toString();
        }
        sb.append("Biggest gaps first:\n\n");
        int i = 1;
        for (SkillGapResponse g : s.gaps) {
            sb.append(i++).append(". **").append(g.skillName()).append("** — target ")
                    .append(or0(g.targetPercent())).append("%, you: ").append(scoreOrDash(g.scorePercent()))
                    .append(", gap **").append(or0(g.gapPercent())).append("%**");
            if (Boolean.FALSE.equals(g.assessed())) {
                sb.append(" · _not yet assessed_");
            }
            sb.append("\n");
        }
        SkillGapResponse top = s.gaps.get(0);
        sb.append("\n### ✅ What to do next\n");
        sb.append("- **This week:** focus on **").append(top.skillName()).append("** — it's your largest gap (")
                .append(or0(top.gapPercent())).append("%).\n");
        if (!s.nextSteps.isEmpty()) {
            sb.append("- **Roadmap link:** start with _").append(s.nextSteps.get(0)).append("_\n");
        }
        if (!s.projectLines.isEmpty()) {
            sb.append("- **Prove it:** build a small project using **").append(top.skillName()).append("** "
                    + "and mark it complete under **Projects**.\n");
        }
        sb.append("- **Re-take** the relevant assessment after 1–2 weeks to close the gap measurably.\n");
        return sb.toString();
    }

    private String assessmentReply(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📝 Your assessment story\n\n");
        if (s.history == null || s.history.isEmpty()) {
            sb.append("You have **no submitted assessments yet**.\n\n");
            sb.append("### ✅ Next step\nGo to **Assessments** → start the test for **")
                    .append(s.targetCareer).append("** → submit. I'll then explain every score, "
                    + "your readiness (**currently ").append(s.readinessPercent).append("%**), "
                    + "and exactly which skills to fix first.\n");
            return sb.toString();
        }
        sb.append("**").append(s.history.size()).append(" submitted** · latest: ");
        if (s.latestAttempt != null) {
            sb.append("**").append(s.latestAttempt.assessmentTitle()).append("** — **")
                    .append(s.latestAttempt.overallScore()).append("%** (")
                    .append(s.latestAttempt.status()).append(")\n\n");
        } else {
            sb.append("—\n\n");
        }
        sb.append("Recent attempts:\n");
        s.history.stream().limit(5).forEach(a -> sb.append("- **").append(a.assessmentTitle())
                .append("** — ").append(a.overallScore()).append("% · ").append(a.status()).append("\n"));
        sb.append("\n### What this means\n");
        sb.append("- Readiness **").append(s.readinessPercent).append("% (").append(s.readinessLevel).append(")**: ")
                .append(readinessLine(s.readinessPercent)).append("\n");
        if (!s.gaps.isEmpty()) {
            sb.append("- Lowest skill right now: **").append(s.gaps.get(0).skillName()).append("** (gap ")
                    .append(or0(s.gaps.get(0).gapPercent())).append("%). Ask **\"What skills am I missing?\"** "
                    + "for the full ranked list.\n");
        }
        if (!s.strengths.isEmpty()) {
            sb.append("- Strength to showcase on CV: **").append(s.strengths.get(0).skillName()).append("**\n");
        }
        return sb.toString();
    }

    private String roadmapReply(Snapshot s, String raw) {
        StringBuilder sb = new StringBuilder();
        boolean wantsPlan = raw.toLowerCase(Locale.ROOT).matches("(?s).*(plan|30.day|week|month).*");
        sb.append(wantsPlan ? "### 🗺️ Your 30-day focus plan\n\n" : "### 🗺️ What to learn next\n\n");
        sb.append("Roadmap for **").append(s.targetCareer).append("**: **")
                .append(s.roadmapDone).append("/").append(s.roadmapTotal)
                .append(" done (").append(s.roadmapPercent).append("%)**\n\n");
        if (s.nextSteps.isEmpty()) {
            sb.append(s.roadmapTotal == 0
                    ? "Your roadmap is empty for this career — an admin needs to add roadmap items, "
                            + "or pick a career with a published roadmap.\n"
                    : "🎉 **Roadmap complete!** Shift focus to projects + CV/LinkedIn polish.\n");
            return sb.toString();
        }
        if (wantsPlan) {
            String first = s.nextSteps.size() > 0 ? s.nextSteps.get(0) : "—";
            String second = s.nextSteps.size() > 1 ? s.nextSteps.get(1) : null;
            String third = s.nextSteps.size() > 2 ? s.nextSteps.get(2) : null;
            String gapSkill = s.gaps.isEmpty() ? null : s.gaps.get(0).skillName();
            sb.append("**Week 1:** _").append(first).append("_")
                    .append(gapSkill != null ? " → closes gap in **" + gapSkill + "**" : "").append("\n");
            if (second != null) {
                sb.append("**Week 2:** _").append(second).append("_\n");
            }
            if (third != null) {
                sb.append("**Week 3–4:** _").append(third).append("_ + build one mini-project "
                        + "and re-take the related assessment.\n");
            }
            sb.append("\nMark each item complete under **Roadmap** as you finish — "
                    + "your progress percent moves the same day.\n");
        } else {
            sb.append("Focus in this order:\n");
            int i = 1;
            for (String step : s.nextSteps) {
                sb.append(i++).append(". ").append(step).append("\n");
            }
            if (!s.gaps.isEmpty()) {
                sb.append("\nWhy this order: your biggest gap is **").append(s.gaps.get(0).skillName())
                        .append("**, and the first item above attacks it directly.\n");
            }
        }
        return sb.toString();
    }

    private String projectsReply(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🛠️ Projects for **").append(s.targetCareer).append("**\n\n");
        sb.append("**").append(s.projectsDone).append("/").append(s.projectsTotal).append(" complete**\n\n");
        if (s.projectLines.isEmpty()) {
            sb.append("No projects are linked to this career yet. Complete roadmap items first — "
                    + "each one maps to a portfolio-worthy build.\n");
            return sb.toString();
        }
        s.projectLines.stream().limit(6).forEach(line -> sb.append("- ").append(line).append("\n"));
        if (!s.gaps.isEmpty()) {
            sb.append("\n### ✅ Recommended for you\nStart with the project that uses **")
                    .append(s.gaps.get(0).skillName())
                    .append("** — it turns your weakest skill into interview proof fastest.\n");
        }
        sb.append("\nTrack everything under **Projects** (mark In Progress → Completed). "
                + "Recruiters trust finished builds over certificates.\n");
        return sb.toString();
    }

    private String cvReply(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📄 Your CV check\n\n");
        if (!s.hasCv) {
            sb.append("You **haven't uploaded a CV yet**.\n\n");
            sb.append("### ✅ Next step\nGo to **CV** → upload your PDF → come back and ask "
                    + "**\"How can I improve my CV?\"** — I'll compare it against **")
                    .append(s.targetCareer).append("** skill-by-skill.\n");
            return sb.toString();
        }
        sb.append("- Skills detected: **").append(s.cvSkills).append("** · completeness **")
                .append(s.cvCompleteness).append("**\n");
        if (!s.gaps.isEmpty()) {
            sb.append("- Missing from CV (likely): **").append(s.gaps.get(0).skillName()).append("**");
            if (s.gaps.size() > 1) {
                sb.append(", **").append(s.gaps.get(1).skillName()).append("**");
            }
            sb.append("\n\n### ✅ Fix this week\n");
            sb.append("1. Add a **Skills** section listing your target-career keywords\n");
            sb.append("2. Add **one bullet per project** with the skill + outcome "
                    + "(e.g. _\"Built X with ").append(s.gaps.get(0).skillName()).append(" …\"_)\n");
            sb.append("3. Re-upload under **CV** and re-run analysis — completeness should rise.\n");
        } else {
            sb.append("\nYour CV covers the assessed skills well. Next: quantify impact "
                    + "(numbers, outcomes) in every bullet.\n");
        }
        return sb.toString();
    }

    private String linkedInReply(Snapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 💼 Your LinkedIn check\n\n");
        if (!s.hasLinkedIn) {
            sb.append("No LinkedIn profile saved yet.\n\n### ✅ Next step\nGo to **LinkedIn** → paste your "
                    + "profile URL + headline → save. I'll then score completeness (currently **")
                    .append(s.linkedInPercent).append("%**) and alignment with **")
                    .append(s.targetCareer).append("**.\n");
            return sb.toString();
        }
        sb.append("- Completeness **").append(s.linkedInPercent).append("%** · alignment **")
                .append(s.linkedInAlignment).append("%** with **").append(s.targetCareer).append("**\n\n");
        sb.append("### ✅ Fix this week\n");
        sb.append("1. Headline = _Aspiring ").append(s.targetCareer).append(" | top-2 skills_\n");
        sb.append("2. **About** section: 3 lines on what you build + what you're learning\n");
        if (!s.gaps.isEmpty()) {
            sb.append("3. **Skills** section: add **").append(s.gaps.get(0).skillName())
                    .append("** once you finish its roadmap item\n");
        }
        return sb.toString();
    }

    private String profileReply(Snapshot s) {
        return "### 👤 Your CareerOS snapshot\n\n"
                + "- **Target career:** " + s.targetCareer + "\n"
                + "- **Readiness:** " + s.readinessPercent + "% (" + s.readinessLevel + ") · "
                + s.metSkills + "/" + s.totalSkills + " skills at target\n"
                + "- **Roadmap:** " + s.roadmapDone + "/" + s.roadmapTotal + " (" + s.roadmapPercent + "%)\n"
                + "- **Projects:** " + s.projectsDone + "/" + s.projectsTotal + " complete\n"
                + "- **CV:** " + (s.hasCv ? s.cvSkills + " skills detected (" + s.cvCompleteness + ")" : "not uploaded") + "\n"
                + "- **LinkedIn:** " + (s.hasLinkedIn ? s.linkedInPercent + "% complete" : "not connected") + "\n\n"
                + "Ask **\"What should I learn next?\"** and I'll turn this into a concrete weekly plan.";
    }

    private String overviewReply(Snapshot s, String raw) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(s.studentName).append("! Here's your **CareerOS digest** for **")
                .append(s.targetCareer).append("**:\n\n");
        sb.append("- **Readiness ").append(s.readinessPercent).append("% (").append(s.readinessLevel).append(")** — ")
                .append(readinessLine(s.readinessPercent)).append("\n");
        if (!s.gaps.isEmpty()) {
            sb.append("- **Top gap:** ").append(s.gaps.get(0).skillName()).append(" (")
                    .append(or0(s.gaps.get(0).gapPercent())).append("% behind target)\n");
        }
        if (!s.nextSteps.isEmpty()) {
            sb.append("- **Next roadmap step:** ").append(s.nextSteps.get(0)).append("\n");
        }
        sb.append("- **Roadmap** ").append(s.roadmapDone).append("/").append(s.roadmapTotal)
                .append(" · **Projects** ").append(s.projectsDone).append("/").append(s.projectsTotal).append("\n");
        if (!s.hasCv) {
            sb.append("- **CV:** not uploaded — this is costing you shortlists\n");
        }
        if (!s.hasLinkedIn) {
            sb.append("- **LinkedIn:** not connected — recruiters can't find you\n");
        }
        sb.append("\n### ✅ Your 3 moves this week\n");
        if (!s.gaps.isEmpty()) {
            sb.append("1. Study **").append(s.gaps.get(0).skillName()).append("** (largest gap)\n");
        } else {
            sb.append("1. Take an **Assessment** to refresh your scores\n");
        }
        if (!s.nextSteps.isEmpty()) {
            sb.append("2. Complete roadmap item: _").append(s.nextSteps.get(0)).append("_\n");
        } else {
            sb.append("2. Ship one small project and mark it complete\n");
        }
        sb.append("3. ").append(!s.hasCv ? "Upload your **CV**" : "Polish **CV + LinkedIn** with new keywords").append("\n");
        sb.append("\nAsk a follow-up like **\"Explain my assessment result\"** or "
                + "**\"Give me a 30-day plan\"** for more detail.");
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    private boolean isGreeting(String lower) {
        String t = lower.trim();
        return t.matches("^(hi|hii+|hello|hey|yo|namaste|good\\s?(morning|afternoon|evening))(\\s*[!.,]*)\\s*$")
                || t.equals("how are you");
    }

    private boolean isHelp(String lower) {
        return containsAny(lower, "what can you do", "help", "how do you work", "features", "how to use");
    }

    private boolean containsAny(String lower, String... needles) {
        for (String n : needles) {
            if (lower.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private String safeFirstName() {
        try {
            var profile = studentProfileService.getCurrentProfile();
            if (profile.fullName() != null && !profile.fullName().isBlank()) {
                return profile.fullName().strip().split("\\s+")[0];
            }
        } catch (Exception ignored) {
        }
        return "there";
    }

    private String readinessLine(int pct) {
        if (pct >= 80) {
            return "you're close — focus on interview prep + portfolio.";
        }
        if (pct >= 50) {
            return "solid base — 2–3 focused weeks can push you much higher.";
        }
        if (pct > 0) {
            return "early stage — follow the roadmap order below and you'll climb fast.";
        }
        return "take your first assessment so I can measure real scores.";
    }

    private int or0(Integer v) {
        return v == null ? 0 : v;
    }

    private String scoreOrDash(Integer v) {
        return v == null ? "—" : v + "%";
    }
}
