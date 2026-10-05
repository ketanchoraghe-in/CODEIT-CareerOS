package com.codeit.careeros.service;

import com.codeit.careeros.assessment.AssessmentAnswer;
import com.codeit.careeros.assessment.AssessmentAttempt;
import com.codeit.careeros.assessment.AssessmentQuestion;
import com.codeit.careeros.assessment.AssessmentTest;
import com.codeit.careeros.assessment.QuestionOption;
import com.codeit.careeros.assessment.SkillScore;
import com.codeit.careeros.career.CareerSkill;
import com.codeit.careeros.common.enums.AttemptStatus;
import com.codeit.careeros.common.enums.ResultLevel;
import com.codeit.careeros.dto.assessment.AnswerRequest;
import com.codeit.careeros.dto.assessment.AssessmentOverviewResponse;
import com.codeit.careeros.dto.assessment.AttemptProgressResponse;
import com.codeit.careeros.dto.assessment.AttemptResultResponse;
import com.codeit.careeros.dto.assessment.AttemptStartResponse;
import com.codeit.careeros.dto.assessment.AttemptStateResponse;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.AssessmentMapper;
import com.codeit.careeros.repository.AssessmentAnswerRepository;
import com.codeit.careeros.repository.AssessmentAttemptRepository;
import com.codeit.careeros.repository.AssessmentQuestionRepository;
import com.codeit.careeros.repository.AssessmentTestRepository;
import com.codeit.careeros.repository.CareerSkillRepository;
import com.codeit.careeros.repository.QuestionOptionRepository;
import com.codeit.careeros.repository.SkillScoreRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
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

    private final AssessmentTestRepository assessmentTestRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final QuestionOptionRepository optionRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<AssessmentOverviewResponse> listPublishedForCareer(Long careerId) {
        return assessmentTestRepository.findAllByCareerIdOrderByCreatedAtDesc(careerId).stream()
                .filter(AssessmentTest::isPublished)
                .sorted(Comparator.comparing(AssessmentTest::getCreatedAt).reversed())
                .map(assessment -> AssessmentMapper.toOverview(
                        assessment, questionRepository.countByAssessmentIdAndActiveTrue(assessment.getId())))
                .toList();
    }

    /** Starts a new attempt or resumes the in-progress attempt of the student. */
    @Transactional
    public AttemptStartResponse start(Long assessmentId, Long userId) {
        AssessmentTest assessment = assessmentTestRepository.findByIdAndPublishedTrue(assessmentId)
                .orElseThrow(() -> BusinessException.notFound("Assessment not found or not available: " + assessmentId));
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
            List<AssessmentQuestion> questions = questionsForAttempt(
                    attempt.getAssessment().getId(), attempt.getId());
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
        List<AssessmentQuestion> questions = activeQuestions(attempt.getAssessment().getId());
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
        List<AssessmentQuestion> questions = activeQuestions(assessment.getId());
        if (questions.isEmpty()) {
            throw BusinessException.conflict("This assessment has no questions yet");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));
        Instant startedAt = Instant.now();
        AssessmentAttempt attempt = AssessmentAttempt.builder()
                .attemptCode(UUID.randomUUID().toString().replace("-", "").substring(0, 24))
                .user(user)
                .assessment(assessment)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(startedAt)
                .expiresAt(startedAt.plusSeconds(assessment.getDurationMinutes() * 60L))
                .totalQuestions(questions.size())
                .answeredCount(0)
                .build();
        attempt = attemptRepository.save(attempt);
        List<AssessmentQuestion> shuffled = questionsForAttempt(assessment.getId(), attempt.getId());
        log.info("Student {} started attempt {} on assessment {}", userId, attempt.getId(), assessment.getId());
        return AssessmentMapper.toStartResponse(
                attempt, shuffled, optionsForAttempt(shuffled, attempt.getId()), List.of());
    }

    private AttemptStartResponse resumeAttempt(AssessmentAttempt attempt) {
        List<AssessmentQuestion> questions = questionsForAttempt(
                attempt.getAssessment().getId(), attempt.getId());
        Map<Long, List<QuestionOption>> options = optionsForAttempt(questions, attempt.getId());
        List<AssessmentAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        return AssessmentMapper.toStartResponse(attempt, questions, options, answers);
    }

    private List<AssessmentQuestion> activeQuestions(Long assessmentId) {
        return questionRepository.findByAssessmentIdAndActiveTrueOrderByDisplayOrderAsc(assessmentId);
    }

    /**
     * Per-attempt question order. Every retake feels different, while a resume
     * or refresh of the SAME attempt always returns the identical order —
     * the shuffle is seeded by the attempt id, so it is stable per attempt
     * and varied across attempts. Grading is order-independent (grouped by
     * skill, answers keyed by question id).
     */
    private List<AssessmentQuestion> questionsForAttempt(Long assessmentId, Long attemptId) {
        List<AssessmentQuestion> questions = new ArrayList<>(activeQuestions(assessmentId));
        Collections.shuffle(questions, new Random(attemptSeed(attemptId, 0L)));
        return questions;
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