package com.codeit.careeros.service;

import com.codeit.careeros.cv.CvDocument;
import com.codeit.careeros.dto.cv.CvAnalysisResponse;
import com.codeit.careeros.dto.cv.CvCompletenessResponse;
import com.codeit.careeros.dto.cv.CvSectionResponse;
import com.codeit.careeros.dto.cv.DetectedSkillResponse;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.insight.SkillGapResponse;
import com.codeit.careeros.repository.CvDocumentRepository;
import com.codeit.careeros.repository.SkillRepository;
import com.codeit.careeros.security.SecurityUtils;
import com.codeit.careeros.skill.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 5 CV analysis. Detected skills come from matching the extracted CV
 * text against the live CareerOS skill master; the matching/missing/gap
 * view reuses the Sprint 3 readiness gaps ({@link CareerInsightService})
 * directly — there is deliberately no second gap calculation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvAnalysisService {

    private final CvDocumentRepository cvDocumentRepository;
    private final SkillRepository skillRepository;
    private final CareerInsightService careerInsightService;

    /** Full analysis of the current student's CV (empty payload when none uploaded). */
    @Transactional(readOnly = true)
    public CvAnalysisResponse analyze() {
        Long userId = SecurityUtils.currentUserId();
        CvDocument document = cvDocumentRepository.findByUserId(userId).orElse(null);
        if (document == null || document.getExtractedText() == null) {
            return new CvAnalysisResponse(false, false, null, null, null,
                    List.of(), List.of(), List.of(),
                    new CvCompletenessResponse(0, 0, 0, List.of(), List.of()),
                    List.of(), 0, 0);
        }

        String text = document.getExtractedText();
        String normalized = normalize(text);
        Map<Long, DetectedSkillResponse> detected = new LinkedHashMap<>();
        for (Skill skill : skillRepository.findAllByOrderByNameAsc()) {
            if (!skill.isActive()) {
                continue;
            }
            if (mentions(normalized, skill.getName())) {
                detected.put(skill.getId(), new DetectedSkillResponse(
                        skill.getId(), skill.getName(), skill.getCategory().name()));
            }
        }

        ReadinessResponse readiness = careerInsightService.myReadiness();
        List<SkillGapResponse> matched = new ArrayList<>();
        List<SkillGapResponse> missing = new ArrayList<>();
        if (readiness.hasTarget()) {
            for (SkillGapResponse gap : readiness.gaps()) {
                if (detected.containsKey(gap.skillId())) {
                    matched.add(gap);
                } else {
                    missing.add(gap);
                }
            }
        }

        CvCompletenessResponse completeness = completenessOf(text);
        List<CvSectionResponse> sections = sectionsOf(text);
        int ats = atsScoreOf(text);
        int frameworkSize = matched.size() + missing.size();
        int alignment = frameworkSize == 0 ? 0
                : Math.round((matched.size() * 100.0f) / frameworkSize);
        int overall = readiness.hasTarget() && frameworkSize > 0
                ? Math.round(completeness.scorePercent() * 0.5f + alignment * 0.3f + ats * 0.2f)
                : Math.round(completeness.scorePercent() * 0.6f + ats * 0.4f);
        log.info("CV analysis for student {}: {} detected skills, {}/{} framework matched",
                userId, detected.size(), matched.size(), matched.size() + missing.size());
        return new CvAnalysisResponse(
                true,
                readiness.hasTarget(),
                readiness.targetCareerId(),
                readiness.targetCareerName(),
                CvService.toResponse(document),
                List.copyOf(detected.values()),
                matched,
                missing,
                completeness,
                sections,
                ats,
                overall);
    }

    /**
     * Matches a master skill name against normalized CV text. Real CVs write
     * the same skill many ways ("HTML5, CSS3", "HTML / CSS", "SpringBoot",
     * "NodeJS", "REST API", "Jenkins" for "CI/CD (Jenkins)", "Git" for
     * "Git & GitHub"), so matching is deliberately recall-friendly — but
     * always on whole-word boundaries, so "JavaScript" never implies "Java",
     * "interaction" never implies "React" and "laws" never implies "AWS".
     *
     * <p>A skill matches when ANY of these holds:
     * <ol>
     *   <li>The full normalized name occurs as a whole word/phrase.</li>
     *   <li>The glued form occurs ("SpringBoot", "ReactNative", "NodeJS").</li>
     *   <li>A known abbreviation/variant occurs ("HTML5" via "html",
     *       "MySQL" for "SQL", "K8s", "NLP", ...).</li>
     *   <li>For names with {@code &}, {@code /} or parentheses, ANY
     *       alternative part matches ("Git" alone matches "Git & GitHub",
     *       "Jenkins" alone matches "CI/CD (Jenkins)").</li>
     *   <li>Otherwise every significant token matches, or one long
     *       distinctive token does ("Selenium" alone matches
     *       "Selenium WebDriver"). Generic qualifiers ("Administration",
     *       "Fundamentals", "Testing", "Design", ...) never match alone.</li>
     * </ol>
     */
    static boolean mentions(String normalizedText, String skillName) {
        if (skillName == null) {
            return false;
        }
        String normalizedSkill = normalize(skillName);
        if (normalizedSkill.isEmpty()) {
            return false;
        }
        // 1. Full name as a whole word/phrase.
        if (containsWord(normalizedText, normalizedSkill)) {
            return true;
        }
        // 2. Glued form covers "SpringBoot", "ReactNative", "PowerBI", "NodeJS".
        String compactSkill = normalizedSkill.replaceAll("[^a-z0-9]+", "");
        if (compactSkill.length() >= 6
                && normalizedText.replaceAll("[^a-z0-9]+", "").contains(compactSkill)) {
            return true;
        }
        // 3. Known abbreviations / variants ("MySQL" for "SQL", "K8s", "NLP" ...).
        for (String alias : aliasesFor(normalizedSkill)) {
            if (!alias.isEmpty() && containsWord(normalizedText, alias)) {
                return true;
            }
        }
        // 4. Alternative parts ("HTML" or "CSS" for "HTML/CSS", "Git" for
        // "Git & GitHub", "Jenkins" for "CI/CD (Jenkins)").
        List<String> segments = segmentsOf(skillName);
        if (segments.size() > 1) {
            for (String segment : segments) {
                if (segmentMatches(normalizedText, segment)) {
                    return true;
                }
            }
            return false;
        }
        // 5. Plain multi-word name: every significant token, or one long
        // distinctive token ("Selenium" for "Selenium WebDriver").
        return segmentMatches(normalizedText, normalizedSkill);
    }

    /**
     * Tokens so generic they only count inside the full phrase ("cloud" alone
     * must not match "Cloud Security" from "Cloud Practitioner course").
     */
    private static final java.util.Set<String> NEEDS_PHRASE = java.util.Set.of("cloud", "database");

    /** A segment matches when every significant token is present, or one long distinctive token is. */
    private static boolean segmentMatches(String normalizedText, String normalizedSegment) {
        List<String> tokens = new ArrayList<>();
        for (String token : normalizedSegment.split("[. ]+")) {
            if (token.length() >= 2 && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return false;
        }
        if (tokens.size() == 1 && NEEDS_PHRASE.contains(tokens.get(0))) {
            return containsWord(normalizedText, normalizedSegment);
        }
        boolean allPresent = true;
        for (String token : tokens) {
            if (containsWord(normalizedText, token)) {
                if (token.length() >= 8) {
                    return true;
                }
            } else {
                allPresent = false;
            }
        }
        return allPresent;
    }

    /**
     * Splits a raw skill name into alternative parts: parenthesized groups
     * become their own alternatives ("CI/CD (Jenkins)" → Jenkins, CI, CD),
     * then the remainder splits on {@code &} and {@code /} ("HTML/CSS" →
     * HTML, CSS). Plain names yield a single segment.
     */
    private static List<String> segmentsOf(String skillName) {
        List<String> segments = new ArrayList<>();
        Matcher parens = Pattern.compile("\\(([^()]*)\\)").matcher(skillName);
        StringBuffer rest = new StringBuffer();
        while (parens.find()) {
            for (String part : parens.group(1).split("[&/]+")) {
                String normalized = normalize(part);
                if (!normalized.isEmpty()) {
                    segments.add(normalized);
                }
            }
            parens.appendReplacement(rest, " ");
        }
        parens.appendTail(rest);
        for (String part : rest.toString().split("[&/]+")) {
            String normalized = normalize(part);
            if (!normalized.isEmpty()) {
                segments.add(normalized);
            }
        }
        return segments;
    }

    /** Whole-word search that also works for symbols ("C++", "C#", "Node.js"). */
    private static boolean containsWord(String haystack, String needle) {
        return Pattern.compile("(^|[^a-z0-9+#.])" + Pattern.quote(needle) + "([^a-z0-9+#.]|$)")
                .matcher(haystack).find();
    }

    /**
     * Generic qualifiers that must never match a skill alone: "Linux" matches
     * "Linux Administration", but "Administration", "Testing" or "Design"
     * alone match nothing.
     */
    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "administration", "administrator", "fundamentals", "fundamental",
            "operations", "operation", "testing", "design", "designer",
            "analysis", "analyst", "analytics", "engineering", "engineer",
            "development", "developer", "management", "manager",
            "language", "languages", "processing", "learning",
            "system", "systems", "application", "applications",
            "rest", "data", "power", "deep", "natural", "security");

    /**
     * Abbreviations and common variants per normalized skill name.
     * Keys AND values use {@link #normalize} spacing (letter/digit pairs are
     * split: "K8s" is "k 8 s", "J2EE" is "j 2 ee").
     */
    private static final Map<String, List<String>> SKILL_ALIASES = Map.ofEntries(
            Map.entry("javascript", List.of("js", "es 6")),
            Map.entry("typescript", List.of("ts")),
            Map.entry("python", List.of("py")),
            Map.entry("sql", List.of("mysql", "postgresql", "postgres", "sqlite", "pl sql", "t sql")),
            Map.entry("react", List.of("reactjs", "react.js")),
            Map.entry("node.js", List.of("nodejs", "node")),
            Map.entry("machine learning", List.of("ml")),
            Map.entry("deep learning", List.of("dl")),
            Map.entry("natural language processing", List.of("nlp")),
            Map.entry("generative ai", List.of("genai", "ai")),
            Map.entry("kubernetes", List.of("k 8 s")),
            Map.entry("mongodb", List.of("mongo")),
            Map.entry("rest apis", List.of("rest api", "restful")),
            Map.entry("microservices", List.of("microservice")),
            Map.entry("java", List.of("core java", "j 2 ee")),
            Map.entry("aws", List.of("amazon web services")),
            Map.entry("ci cd jenkins", List.of("cicd")),
            Map.entry("api testing", List.of("postman")),
            Map.entry("penetration testing", List.of("pentest", "pen test")),
            Map.entry("data visualization", List.of("tableau")),
            Map.entry("database administration", List.of("database administrator")));

    private static List<String> aliasesFor(String normalizedSkill) {
        List<String> aliases = SKILL_ALIASES.get(normalizedSkill);
        return aliases == null ? List.of() : aliases;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        // Split letter<->digit so versions never glue tokens: "HTML5" becomes
        // "html 5", letting the "html" / "css" word matchers work.
        String split = value.toLowerCase(Locale.ROOT)
                .replaceAll("(?<=[a-z])(?=\\d)|(?<=\\d)(?=[a-z])", " ");
        return split.replaceAll("[^a-z0-9+#.]+", " ").strip();
    }

    /**
     * Generic CV quality checks from section signals in the text — not
     * career-specific rules, just whether a recruiter can find the basics.
     */
    static CvCompletenessResponse completenessOf(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> strengths = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        check(lower, containsSection(lower, "education", "qualification", "degree", "university", "college"),
                "Education section found", "Add an Education section with your degree, college and year.",
                strengths, suggestions);
        check(lower, containsSection(lower, "experience", "employment", "work history", "internship"),
                "Experience section found", "Add an Experience or Internship section with what you built and delivered.",
                strengths, suggestions);
        check(lower, containsSection(lower, "project"),
                "Projects section found", "Add a Projects section — recruiters look for proof of applied skills.",
                strengths, suggestions);
        check(lower, containsSection(lower, "skill", "technical", "technologies", "tools"),
                "Skills section found", "Add a Skills section listing the tools and technologies you know.",
                strengths, suggestions);
        check(lower, containsSection(lower, "certification", "certificate", "course"),
                "Certifications or courses found", "Add Certifications or Courses to stand out from other applicants.",
                strengths, suggestions);
        check(lower, Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                        Pattern.CASE_INSENSITIVE).matcher(text).find(),
                "Contact email found", "Add a contact email so recruiters can reach you.",
                strengths, suggestions);
        check(lower, text.strip().length() >= 500,
                "Good detail level", "Add more detail — strong CVs describe outcomes, not just duties.",
                strengths, suggestions);

        int total = strengths.size() + suggestions.size();
        int passed = strengths.size();
        int score = total == 0 ? 0 : Math.round((passed * 100.0f) / total);
        return new CvCompletenessResponse(score, total, passed, strengths, suggestions);
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d[\\d\\s\\-().]{7,}\\d)");

    /**
     * Per-section breakdown reusing the same keyword signals as
     * {@link #completenessOf}. Contact additionally requires a phone number;
     * structure requires enough detail plus at least three core sections.
     */
    static List<CvSectionResponse> sectionsOf(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasEmail = EMAIL_PATTERN.matcher(text).find();
        boolean hasPhone = hasPhoneNumber(text);
        boolean skills = containsSection(lower, "skill", "technical", "technologies", "tools");
        boolean experience = containsSection(lower, "experience", "employment", "work history", "internship");
        boolean education = containsSection(lower, "education", "qualification", "degree", "university", "college");
        boolean projects = containsSection(lower, "project");
        boolean certifications = containsSection(lower, "certification", "certificate", "course");
        boolean summary = containsSection(lower, "summary", "objective", "profile", "about");
        int coreSections = (skills ? 1 : 0) + (experience ? 1 : 0) + (education ? 1 : 0)
                + (projects ? 1 : 0) + (summary ? 1 : 0);
        boolean structured = text.strip().length() >= 500 && coreSections >= 3;

        List<CvSectionResponse> sections = new ArrayList<>();
        sections.add(new CvSectionResponse("contact", "Contact Information", hasEmail && hasPhone,
                hasEmail && hasPhone ? "Email and phone found — recruiters can reach you."
                        : !hasEmail && !hasPhone ? "Add a contact email and phone number at the top of your CV."
                        : !hasEmail ? "Add a contact email so recruiters can reach you."
                        : "Add a phone number so recruiters can reach you."));
        sections.add(new CvSectionResponse("summary", "Professional Summary", summary,
                summary ? "Summary section found — it frames who you are in seconds."
                        : "Add a 2–3 line professional summary: who you are, key skills, what you want next."));
        sections.add(new CvSectionResponse("skills", "Skills", skills,
                skills ? "Skills section found and matched against the CareerOS skill catalog."
                        : "Add a Skills section listing the tools and technologies you know."));
        sections.add(new CvSectionResponse("experience", "Experience", experience,
                experience ? "Experience section found — describe outcomes, not just duties."
                        : "Add an Experience or Internship section with what you built and delivered."));
        sections.add(new CvSectionResponse("education", "Education", education,
                education ? "Education section found with degree and college details."
                        : "Add an Education section with your degree, college and year."));
        sections.add(new CvSectionResponse("projects", "Projects", projects,
                projects ? "Projects section found — recruiters look for proof of applied skills."
                        : "Add a Projects section — recruiters look for proof of applied skills."));
        sections.add(new CvSectionResponse("certifications", "Certifications", certifications,
                certifications ? "Certifications or courses found."
                        : "Add Certifications or Courses to stand out from other applicants."));
        sections.add(new CvSectionResponse("structure", "Resume Structure", structured,
                structured ? "Good detail level with clear sections a recruiter can skim."
                        : "Add more detail across at least three sections — strong CVs describe outcomes."));
        return sections;
    }

    /**
     * ATS (machine-readability) score: share of six real parse signals
     * passed — contact email and phone present as text, standard
     * skill/experience/education headings, and an extractable text layer of
     * at least 300 characters (scanned images without text fail this).
     */
    /**
     * A phone number counts only with 10–15 digits. The raw pattern also
     * matches year ranges like "2020 - 2024", which must not mark the
     * contact section as present.
     */
    static boolean hasPhoneNumber(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group(1).replaceAll("\\D", "");
            if (digits.length() >= 10 && digits.length() <= 15) {
                return true;
            }
        }
        return false;
    }

    static int atsScoreOf(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int passed = 0;
        if (EMAIL_PATTERN.matcher(text).find()) {
            passed++;
        }
        if (hasPhoneNumber(text)) {
            passed++;
        }
        if (containsSection(lower, "skill", "technical", "technologies", "tools")) {
            passed++;
        }
        if (containsSection(lower, "experience", "employment", "work history", "internship")) {
            passed++;
        }
        if (containsSection(lower, "education", "qualification", "degree", "university", "college")) {
            passed++;
        }
        if (text.strip().length() >= 300) {
            passed++;
        }
        return Math.round((passed * 100.0f) / 6);
    }

    private static void check(String ignored, boolean passed, String strength, String suggestion,
                              List<String> strengths, List<String> suggestions) {
        if (passed) {
            strengths.add(strength);
        } else {
            suggestions.add(suggestion);
        }
    }

    /**
     * Heading-aware section detection. The old version matched a keyword
     * anywhere in the CV text, so a sentence like "hands-on internship
     * experience building APIs" incorrectly marked the whole Experience
     * section as present even when the resume had no such section.
     * Now a keyword only counts when it appears as a section heading:
     * a short heading line containing the keyword, or a line starting
     * with the keyword (covers inline "Experience: ..." headings).
     */
    private static boolean containsSection(String fullText, String... keywords) {
        if (fullText == null || fullText.isBlank()) {
            return false;
        }
        String[] lines = fullText.split("\\R");
        for (String rawLine : lines) {
            String stripped = rawLine.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            // Drop leading bullets/numbers so "- EXPERIENCE" still matches.
            String cleaned = stripped.replaceAll("^[^A-Za-z0-9]+", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            String lowerLine = cleaned.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                String kw = keyword.toLowerCase(Locale.ROOT);
                // Leading word-boundary only (same as the old matcher) so
                // plural headings ("Skills", "Projects", "Certifications")
                // still match singular keywords ("skill", "project", ...).
                if (Pattern.compile("^" + Pattern.quote(kw)).matcher(lowerLine).find()) {
                    return true;
                }
                if (cleaned.length() <= 45
                        && Pattern.compile("\\b" + Pattern.quote(kw))
                                .matcher(lowerLine).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
