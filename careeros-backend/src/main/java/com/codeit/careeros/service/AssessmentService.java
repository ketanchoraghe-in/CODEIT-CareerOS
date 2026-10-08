package com.codeit.careeros.service;

import com.codeit.careeros.assessment.AssessmentAnswer;
import com.codeit.careeros.assessment.AssessmentAttempt;
import com.codeit.careeros.assessment.AssessmentAttemptQuestion;
import com.codeit.careeros.assessment.AssessmentQuestion;
import com.codeit.careeros.assessment.AssessmentTest;
import com.codeit.careeros.assessment.QuestionOption;
import com.codeit.careeros.assessment.SkillScore;
import com.codeit.careeros.career.CareerSkill;
import com.codeit.careeros.common.enums.AttemptStatus;
import com.codeit.careeros.common.enums.DifficultyLevel;
import com.codeit.careeros.common.enums.ResultLevel;
import com.codeit.careeros.dto.assessment.AnswerRequest;
import com.codeit.careeros.dto.assessment.AssessmentOverviewResponse;
import com.codeit.careeros.dto.assessment.AttemptProgressResponse;
import com.codeit.careeros.dto.assessment.AttemptResultResponse;
import com.codeit.careeros.dto.assessment.AttemptStartResponse;
import com.codeit.careeros.dto.assessment.AttemptStateResponse;
import com.codeit.careeros.entity.StudentProfile;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.AssessmentMapper;
import com.codeit.careeros.repository.AssessmentAnswerRepository;
import com.codeit.careeros.repository.AssessmentAttemptQuestionRepository;
import com.codeit.careeros.repository.AssessmentAttemptRepository;
import com.codeit.careeros.repository.AssessmentQuestionRepository;
import com.codeit.careeros.repository.AssessmentTestRepository;
import com.codeit.careeros.repository.CareerSkillRepository;
import com.codeit.careeros.repository.QuestionOptionRepository;
import com.codeit.careeros.repository.SkillScoreRepository;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sprint 2 assessment engine (docs sections 14 and 15). Students start an
 * assessment, answer questions (autosaved), submit, and receive per-skill
 * scores plus an overall score weighted by the career competency framework.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    public static final int DEFAULT_TARGET_PERCENT = 80;

    /**
     * Questions served per attempt. Banks at or below this size are served in
     * full (shuffled); larger banks are sampled down to this size so retakes
     * vary while every attempt stays a complete, balanced assessment.
     */
    public static final int MAX_ATTEMPT_QUESTIONS = 20;

    /**
     * Framework weight assumed for a bank skill that has no explicit
     * CareerSkill mapping, so legacy bank questions stay eligible instead of
     * being silently dropped from every attempt.
     */
    static final int DEFAULT_UNMAPPED_SKILL_WEIGHT = 5;

    private final AssessmentTestRepository assessmentTestRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAttemptQuestionRepository attemptQuestionRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final QuestionOptionRepository optionRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<AssessmentOverviewResponse> listPublishedForCareer(Long careerId) {
        return assessmentTestRepository.findAllByCareerIdOrderByCreatedAtDesc(careerId).stream()
                .filter(AssessmentTest::isPublished)
                .sorted(Comparator.comparing(AssessmentTest::getCreatedAt).reversed())
                .map(assessment -> AssessmentMapper.toOverview(
                        assessment,
                        Math.min(questionRepository.countByAssessmentIdAndActiveTrue(assessment.getId()),
                                MAX_ATTEMPT_QUESTIONS)))
                .toList();
    }

    /** Starts a new attempt or resumes the in-progress attempt of the student. */
    @Transactional
    public AttemptStartResponse start(Long assessmentId, Long userId) {
        AssessmentTest assessment = assessmentTestRepository.findByIdAndPublishedTrue(assessmentId)
                .orElseThrow(() -> BusinessException.notFound("Assessment not found or not available: " + assessmentId));
        ensureMatchesTargetCareer(assessment, userId);
        return attemptRepository
                .findByUserIdAndAssessmentIdAndStatus(userId, assessmentId, AttemptStatus.IN_PROGRESS)
                .map(attempt -> {
                    if (isExpired(attempt)) {
                        expire(attempt);
                        return createAttempt(assessment, userId);
                    }
                    return resumeAttempt(attempt);
                })
                .orElseGet(() -> createAttempt(assessment, userId));
    }

    /** Current state: questions while in progress, graded results once submitted. */
    @Transactional
    public AttemptStateResponse getState(Long attemptId, Long userId) {
        AssessmentAttempt attempt = ownedAttempt(attemptId, userId);
        if (attempt.getStatus() == AttemptStatus.EXPIRED) {
            return AssessmentMapper.toStateResponse(attempt, List.of(), null, List.of());
        }
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            if (isExpired(attempt)) {
                expire(attempt);
                return AssessmentMapper.toStateResponse(attempt, List.of(), null, List.of());
            }
            List<AssessmentQuestion> questions = attemptQuestions(attempt);
            Map<Long, List<QuestionOption>> options = optionsForAttempt(questions, attempt.getId());
            List<AssessmentAnswer> answers = answerRepository.findByAttemptId(attemptId);
            List<Long> answeredIds = answers.stream().map(a -> a.getQuestion().getId()).toList();
            Map<Long, Long> selectedByQuestion = AssessmentMapper.selectedOptionByQuestion(answers);
            return AssessmentMapper.toStateResponse(
                    attempt,
                    answeredIds,
                    questions.stream()
                            .map(q -> AssessmentMapper.toQuestionResponse(
                                    q,
                                    options.getOrDefault(q.getId(), List.of()),
                                    selectedByQuestion.get(q.getId())))
                            .toList(),
                    null);
        }
        List<SkillScore> scores = skillScoreRepository.findByAttemptIdOrderByWeightPercentDesc(attemptId);
        return AssessmentMapper.toStateResponse(attempt, List.of(), null, scores);
    }

    /** Latest attempt (any status) of the current student for an assessment. */
    @Transactional(readOnly = true)
    public Optional<AttemptStateResponse> myLatestAttempt(Long assessmentId, Long userId) {
        return attemptRepository.findFirstByUserIdAndAssessmentIdOrderByStartedAtDesc(userId, assessmentId)
                .map(attempt -> getState(attempt.getId(), userId));
    }

    /** Autosaves a student's answer for one question of an in-progress attempt. */
    @Transactional
    public AttemptProgressResponse saveAnswer(Long attemptId, Long userId, AnswerRequest request) {
        AssessmentAttempt attempt = ownedAttempt(attemptId, userId);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw BusinessException.badRequest("This attempt has already been submitted");
        }
        ensureNotExpired(attempt);
        AssessmentQuestion question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> BusinessException.badRequest("Question not found: " + request.questionId()));
        if (!question.getAssessment().getId().equals(attempt.getAssessment().getId())) {
            throw BusinessException.badRequest("Question does not belong to this assessment");
        }
        if (attemptQuestionRepository.countByAttemptId(attemptId) > 0
                && !attemptQuestionRepository.existsByAttemptIdAndQuestionId(attemptId, question.getId())) {
            throw BusinessException.badRequest("Question does not belong to this attempt");
        }
        QuestionOption option = optionRepository.findById(request.optionId())
                .orElseThrow(() -> BusinessException.badRequest("Option not found: " + request.optionId()));
        if (!option.getQuestion().getId().equals(question.getId())) {
            throw BusinessException.badRequest("Option does not belong to this question");
        }
        AssessmentAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, question.getId())
                .orElseGet(() -> AssessmentAnswer.builder().attempt(attempt).question(question).build());
        answer.setSelectedOption(option);
        answerRepository.save(answer);

        long answeredCount = answerRepository.countByAttemptId(attemptId);
        attempt.setAnsweredCount((int) answeredCount);
        attemptRepository.save(attempt);

        List<Long> answeredIds = answerRepository.findByAttemptId(attemptId).stream()
                .map(a -> a.getQuestion().getId())
                .toList();
        return new AttemptProgressResponse(attempt.getId(), (int) answeredCount, attempt.getTotalQuestions(), answeredIds);
    }

    /** Grades an in-progress attempt and persists skill-wise scores and the overall score. */
    @Transactional
    public AttemptResultResponse submit(Long attemptId, Long userId) {
        AssessmentAttempt attempt = ownedAttempt(attemptId, userId);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw BusinessException.badRequest("This attempt has already been submitted");
        }
        ensureNotExpired(attempt);
        List<AssessmentQuestion> questions = attemptQuestions(attempt);
        List<AssessmentAnswer> answers = answerRepository.findByAttemptId(attemptId);

        Map<Long, CareerSkill> framework = careerSkillRepository
                .findByCareerIdOrderByWeightPercentDesc(attempt.getAssessment().getCareer().getId()).stream()
                .collect(Collectors.toMap(cs -> cs.getSkill().getId(), Function.identity()));

        Map<Long, List<AssessmentQuestion>> questionsBySkill = questions.stream()
                .collect(Collectors.groupingBy(q -> q.getSkill().getId(), Collectors.toList()));
        Map<Long, Long> correctBySkill = answers.stream()
                .filter(a -> a.getSelectedOption() != null && a.getSelectedOption().isCorrect())
                .collect(Collectors.groupingBy(a -> a.getQuestion().getSkill().getId(), Collectors.counting()));

        List<SkillScore> scores = new ArrayList<>();
        for (Map.Entry<Long, List<AssessmentQuestion>> entry : questionsBySkill.entrySet()) {
            Long skillId = entry.getKey();
            int questionCount = entry.getValue().size();
            int correctCount = correctBySkill.getOrDefault(skillId, 0L).intValue();
            int scorePercent = AssessmentScoring.skillScore(correctCount, questionCount);
            CareerSkill mapping = framework.get(skillId);
            int weightPercent = mapping != null ? mapping.getWeightPercent() : 0;
            int targetPercent = mapping != null ? mapping.getTargetPercent() : DEFAULT_TARGET_PERCENT;
            scores.add(SkillScore.builder()
                    .attempt(attempt)
                    .skill(entry.getValue().get(0).getSkill())
                    .scorePercent(scorePercent)
                    .correctCount(correctCount)
                    .questionCount(questionCount)
                    .weightPercent(weightPercent)
                    .targetPercent(targetPercent)
                    .level(ResultLevel.fromScore(scorePercent))
                    .build());
        }

        int overall = AssessmentScoring.overallScore(scores.stream()
                .map(s -> new AssessmentScoring.WeightedScore(s.getWeightPercent(), s.getScorePercent()))
                .toList());

        attempt.setAnsweredCount(answers.size());
        attempt.setOverallScore(overall);
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attemptRepository.save(attempt);
        skillScoreRepository.saveAll(scores);

        log.info("Student {} submitted attempt {} with overall score {}",
                userId, attemptId, overall);
        return AssessmentMapper.toResultResponse(attempt, scores);
    }

    private AttemptStartResponse createAttempt(AssessmentTest assessment, Long userId) {
        List<AssessmentQuestion> bank = activeQuestions(assessment.getId());
        if (bank.isEmpty()) {
            throw BusinessException.conflict("This assessment has no questions yet");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));
        Instant startedAt = Instant.now();
        List<AssessmentQuestion> selected = selectQuestionsForNewAttempt(assessment, userId, bank);
        AssessmentAttempt attempt = AssessmentAttempt.builder()
                .attemptCode(UUID.randomUUID().toString().replace("-", "").substring(0, 24))
                .user(user)
                .assessment(assessment)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(startedAt)
                .expiresAt(startedAt.plusSeconds(assessment.getDurationMinutes() * 60L))
                .totalQuestions(selected.size())
                .answeredCount(0)
                .build();
        attempt = attemptRepository.save(attempt);
        persistAttemptQuestions(attempt, selected);
        log.info("Student {} started attempt {} on assessment {} ({} of {} bank questions)",
                userId, attempt.getId(), assessment.getId(), selected.size(), bank.size());
        return AssessmentMapper.toStartResponse(
                attempt, selected, optionsForAttempt(selected, attempt.getId()), List.of());
    }

    private AttemptStartResponse resumeAttempt(AssessmentAttempt attempt) {
        List<AssessmentQuestion> questions = attemptQuestions(attempt);
        Map<Long, List<QuestionOption>> options = optionsForAttempt(questions, attempt.getId());
        List<AssessmentAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        return AssessmentMapper.toStartResponse(attempt, questions, options, answers);
    }

    private List<AssessmentQuestion> activeQuestions(Long assessmentId) {
        return questionRepository.findByAssessmentIdAndActiveTrueOrderByDisplayOrderAsc(assessmentId);
    }

    /**
     * Frozen question set of an attempt, in presentation order. Attempts
     * created before per-attempt sets existed have no rows; they fall back to
     * the legacy behaviour (whole bank, order seeded by the attempt id) so
     * existing attempts stay valid and grade exactly as before.
     */
    private List<AssessmentQuestion> attemptQuestions(AssessmentAttempt attempt) {
        List<AssessmentAttemptQuestion> rows = attemptQuestionRepository
                .findByAttemptIdOrderByDisplayOrderAsc(attempt.getId());
        if (!rows.isEmpty()) {
            return rows.stream().map(AssessmentAttemptQuestion::getQuestion).toList();
        }
        return legacyQuestionsForAttempt(attempt.getAssessment().getId(), attempt.getId());
    }

    /**
     * Builds the randomized set for a NEW attempt from the career's question
     * bank: small banks are served in full (shuffled); larger banks are
     * sampled to {@link #MAX_ATTEMPT_QUESTIONS} with per-skill quotas
     * proportional to the live CareerSkill weights, preferring questions the
     * student has not seen before and balancing difficulties inside each
     * skill. The returned order is already shuffled.
     */
    private List<AssessmentQuestion> selectQuestionsForNewAttempt(
            AssessmentTest assessment, Long userId, List<AssessmentQuestion> bank) {
        Random rng = ThreadLocalRandom.current();
        if (bank.size() <= MAX_ATTEMPT_QUESTIONS) {
            List<AssessmentQuestion> all = new ArrayList<>(bank);
            Collections.shuffle(all, rng);
            return all;
        }
        Map<Long, Integer> weights = frameworkWeights(assessment.getCareer().getId());
        Map<Long, List<AssessmentQuestion>> bySkill = bank.stream()
                .collect(Collectors.groupingBy(q -> q.getSkill().getId()));
        List<Long> skillIds = bySkill.keySet().stream()
                .sorted(Comparator.comparingInt((Long skillId) ->
                        weights.getOrDefault(skillId, DEFAULT_UNMAPPED_SKILL_WEIGHT)).reversed())
                .toList();

        Map<Long, Integer> quota = largestRemainderQuotas(
                skillIds, weights, bySkill, MAX_ATTEMPT_QUESTIONS);
        Set<Long> seen = new HashSet<>(
                attemptQuestionRepository.findSeenQuestionIds(userId, assessment.getId()));

        Map<Long, List<AssessmentQuestion>> unseenBySkill = new HashMap<>();
        Map<Long, List<AssessmentQuestion>> seenBySkill = new HashMap<>();
        for (Long skillId : skillIds) {
            unseenBySkill.put(skillId, new ArrayList<>());
            seenBySkill.put(skillId, new ArrayList<>());
            for (AssessmentQuestion question : bySkill.get(skillId)) {
                (seen.contains(question.getId()) ? seenBySkill.get(skillId) : unseenBySkill.get(skillId))
                        .add(question);
            }
        }
        int unseenTotal = unseenBySkill.values().stream().mapToInt(List::size).sum();

        List<AssessmentQuestion> selected = new ArrayList<>(MAX_ATTEMPT_QUESTIONS);
        if (unseenTotal >= MAX_ATTEMPT_QUESTIONS) {
            // Enough fresh questions: quota-based sampling from the unseen
            // pool (per-skill shortfalls spill into seen questions).
            for (Long skillId : skillIds) {
                int need = quota.getOrDefault(skillId, 0);
                if (need <= 0) {
                    continue;
                }
                List<AssessmentQuestion> ordered = new ArrayList<>(
                        orderByDifficultyRoundRobin(unseenBySkill.get(skillId), rng));
                ordered.addAll(orderByDifficultyRoundRobin(seenBySkill.get(skillId), rng));
                selected.addAll(ordered.subList(0, Math.min(need, ordered.size())));
            }
        } else {
            // Fewer fresh questions than needed: serve every remaining unseen
            // question first, then refill from seen questions quota-aware.
            for (Long skillId : skillIds) {
                selected.addAll(unseenBySkill.get(skillId));
            }
            int remaining = MAX_ATTEMPT_QUESTIONS - selected.size();
            Map<Long, Integer> refillQuota = largestRemainderQuotas(
                    skillIds, weights, seenBySkill, remaining);
            for (Long skillId : skillIds) {
                int need = refillQuota.getOrDefault(skillId, 0);
                if (need <= 0) {
                    continue;
                }
                List<AssessmentQuestion> ordered =
                        orderByDifficultyRoundRobin(seenBySkill.get(skillId), rng);
                selected.addAll(ordered.subList(0, Math.min(need, ordered.size())));
            }
        }
        Collections.shuffle(selected, rng);
        return selected;
    }

    /** Live CareerSkill weights keyed by skill id (the competency framework). */
    private Map<Long, Integer> frameworkWeights(Long careerId) {
        return careerSkillRepository.findByCareerIdOrderByWeightPercentDesc(careerId).stream()
                .collect(Collectors.toMap(
                        row -> row.getSkill().getId(), CareerSkill::getWeightPercent, (a, b) -> a));
    }

    /**
     * Per-skill question quotas proportional to framework weights (largest
     * remainder method), guaranteeing every represented skill at least one
     * question and never more than its available questions (surplus is passed
     * to the next-heaviest skill with spare capacity).
     */
    private Map<Long, Integer> largestRemainderQuotas(
            List<Long> skillIds,
            Map<Long, Integer> weights,
            Map<Long, List<AssessmentQuestion>> bySkill,
            int size) {
        int totalWeight = skillIds.stream()
                .mapToInt(skill -> weights.getOrDefault(skill, DEFAULT_UNMAPPED_SKILL_WEIGHT))
                .sum();
        Map<Long, Integer> quota = new HashMap<>();
        Map<Long, Double> remainder = new HashMap<>();
        int allocated = 0;
        for (Long skillId : skillIds) {
            double exact = totalWeight <= 0 ? 0
                    : (double) size * weights.getOrDefault(skillId, DEFAULT_UNMAPPED_SKILL_WEIGHT)
                            / totalWeight;
            int whole = (int) Math.floor(exact);
            quota.put(skillId, whole);
            remainder.put(skillId, exact - whole);
            allocated += whole;
        }
        List<Long> byRemainder = new ArrayList<>(skillIds);
        byRemainder.sort(Comparator.comparingDouble((Long skill) -> remainder.get(skill)).reversed()
                .thenComparingInt(skill -> -weights.getOrDefault(skill, DEFAULT_UNMAPPED_SKILL_WEIGHT)));
        int cursor = 0;
        while (allocated < size && cursor < byRemainder.size()) {
            quota.merge(byRemainder.get(cursor++), 1, Integer::sum);
            allocated++;
        }
        // Every represented skill appears at least once: steal from the
        // fattest quota when necessary.
        for (Long skillId : skillIds) {
            if (quota.get(skillId) > 0 || bySkill.get(skillId).isEmpty()) {
                continue;
            }
            Long donor = skillIds.stream()
                    .filter(candidate -> quota.get(candidate) > 1)
                    .findFirst().orElse(null);
            if (donor == null) {
                break;
            }
            quota.merge(donor, -1, Integer::sum);
            quota.put(skillId, 1);
        }
        // Cap at available questions; hand surplus down the weight order.
        boolean moved;
        do {
            moved = false;
            for (Long skillId : skillIds) {
                int spare = quota.get(skillId) - bySkill.get(skillId).size();
                while (spare > 0) {
                    Long receiver = skillIds.stream()
                            .filter(candidate -> quota.get(candidate) < bySkill.get(candidate).size())
                            .findFirst().orElse(null);
                    if (receiver == null) {
                        break;
                    }
                    quota.merge(skillId, -1, Integer::sum);
                    quota.merge(receiver, 1, Integer::sum);
                    spare--;
                    moved = true;
                }
            }
        } while (moved);
        return quota;
    }

    /**
     * Orders candidates so difficulties interleave (BEGINNER,
     * INTERMEDIATE, ADVANCED, …) with random order inside each difficulty,
     * giving every sampled skill a balanced mix whenever the bank allows it.
     */
    private List<AssessmentQuestion> orderByDifficultyRoundRobin(
            List<AssessmentQuestion> pool, Random rng) {
        Map<DifficultyLevel, List<AssessmentQuestion>> buckets = new EnumMap<>(DifficultyLevel.class);
        for (DifficultyLevel level : DifficultyLevel.values()) {
            buckets.put(level, new ArrayList<>());
        }
        for (AssessmentQuestion question : pool) {
            buckets.get(question.getDifficulty()).add(question);
        }
        buckets.values().forEach(bucket -> Collections.shuffle(bucket, rng));
        List<AssessmentQuestion> ordered = new ArrayList<>(pool.size());
        boolean added;
        do {
            added = false;
            for (DifficultyLevel level : DifficultyLevel.values()) {
                List<AssessmentQuestion> bucket = buckets.get(level);
                if (!bucket.isEmpty()) {
                    ordered.add(bucket.remove(0));
                    added = true;
                }
            }
        } while (added);
        return ordered;
    }

    private void persistAttemptQuestions(AssessmentAttempt attempt, List<AssessmentQuestion> selected) {
        List<AssessmentAttemptQuestion> rows = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            rows.add(AssessmentAttemptQuestion.builder()
                    .attempt(attempt)
                    .question(selected.get(i))
                    .displayOrder(i)
                    .build());
        }
        attemptQuestionRepository.saveAll(rows);
    }

    private Map<Long, List<QuestionOption>> optionsForAttempt(
            List<AssessmentQuestion> questions, Long attemptId) {
        Map<Long, List<QuestionOption>> byQuestion = new HashMap<>();
        for (AssessmentQuestion question : questions) {
            List<QuestionOption> options = new ArrayList<>(
                    optionRepository.findByQuestionIdOrderByDisplayOrderAsc(question.getId()));
            Collections.shuffle(options, new Random(attemptSeed(attemptId, question.getId())));
            byQuestion.put(question.getId(), options);
        }
        return byQuestion;
    }

    private static long attemptSeed(Long attemptId, Long salt) {
        return attemptId * 1_000_003L + salt * 31L + 17L;
    }

    /** Pre-Sprint-9 fallback: whole bank in attempt-seeded order. */
    private List<AssessmentQuestion> legacyQuestionsForAttempt(Long assessmentId, Long attemptId) {
        List<AssessmentQuestion> questions = new ArrayList<>(activeQuestions(assessmentId));
        Collections.shuffle(questions, new Random(attemptSeed(attemptId, 0L)));
        return questions;
    }

    /**
     * Guards the career &rarr; assessment mapping at start time: a student who
     * has selected a target career may only start assessments belonging to
     * that career, so manually swapping the assessment id cannot start an
     * unrelated career's assessment. Students without a target career yet
     * (or non-student callers with no profile) are not restricted, keeping
     * career selection itself and legacy flows working.
     */
    private void ensureMatchesTargetCareer(AssessmentTest assessment, Long userId) {
        StudentProfile profile = studentProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null || profile.getTargetCareer() == null) {
            return;
        }
        if (!profile.getTargetCareer().getId().equals(assessment.getCareer().getId())) {
            throw BusinessException.forbidden(
                    "This assessment belongs to '" + assessment.getCareer().getName()
                            + "' but your target career is '" + profile.getTargetCareer().getName()
                            + "'. Select the matching career first.");
        }
    }

    private AssessmentAttempt ownedAttempt(Long attemptId, Long userId) {
        AssessmentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> BusinessException.notFound("Attempt not found: " + attemptId));
        if (!attempt.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("You do not have access to this attempt");
        }
        return attempt;
    }

    /**
     * Server-side enforcement of the attempt duration. The frontend timer is
     * only a visual aid; once the deadline passes, answers and submissions
     * are rejected and the attempt is moved to the terminal EXPIRED state.
     */
    private boolean isExpired(AssessmentAttempt attempt) {
        return attempt.getExpiresAt() != null && Instant.now().isAfter(attempt.getExpiresAt());
    }

    private void expire(AssessmentAttempt attempt) {
        // Committed in its own transaction: callers reject the write with an
        // exception afterwards, which would roll back this transition otherwise.
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(tx -> {
            AssessmentAttempt managed = attemptRepository.findById(attempt.getId())
                    .orElseThrow(() -> BusinessException.notFound("Attempt not found: " + attempt.getId()));
            managed.setStatus(AttemptStatus.EXPIRED);
            attemptRepository.save(managed);
        });
        attempt.setStatus(AttemptStatus.EXPIRED);
        log.info("Attempt {} expired without submission", attempt.getId());
    }

    private void ensureNotExpired(AssessmentAttempt attempt) {
        if (isExpired(attempt)) {
            expire(attempt);
            throw BusinessException.gone("The time limit for this attempt has expired");
        }
    }
}