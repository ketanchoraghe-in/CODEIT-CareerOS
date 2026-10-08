package com.codeit.careeros.admin;

import com.codeit.careeros.assessment.AssessmentQuestion;
import com.codeit.careeros.assessment.AssessmentTest;
import com.codeit.careeros.assessment.QuestionOption;
import com.codeit.careeros.career.Career;
import com.codeit.careeros.common.enums.DifficultyLevel;
import com.codeit.careeros.common.enums.QuestionType;
import com.codeit.careeros.dto.assessment.AssessmentAdminDetailResponse;
import com.codeit.careeros.dto.assessment.AssessmentAdminResponse;
import com.codeit.careeros.dto.assessment.AssessmentRequest;
import com.codeit.careeros.dto.assessment.QuestionAdminResponse;
import com.codeit.careeros.dto.assessment.QuestionOptionRequest;
import com.codeit.careeros.dto.assessment.QuestionRequest;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.AssessmentMapper;
import com.codeit.careeros.mapper.CareerMapper;
import com.codeit.careeros.repository.AssessmentAnswerRepository;
import com.codeit.careeros.repository.AssessmentAttemptQuestionRepository;
import com.codeit.careeros.repository.AssessmentAttemptRepository;
import com.codeit.careeros.repository.AssessmentQuestionRepository;
import com.codeit.careeros.repository.AssessmentTestRepository;
import com.codeit.careeros.repository.CareerRepository;
import com.codeit.careeros.repository.QuestionOptionRepository;
import com.codeit.careeros.repository.SkillRepository;
import com.codeit.careeros.skill.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Admin write side of assessments and the question bank. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAssessmentService {

    private final AssessmentTestRepository assessmentTestRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final CareerRepository careerRepository;
    private final SkillRepository skillRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentAttemptQuestionRepository attemptQuestionRepository;
    private final AssessmentAnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public List<AssessmentAdminResponse> listAll() {
        return assessmentTestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(assessment -> AssessmentMapper.toAdminResponse(
                        assessment, questionRepository.countByAssessmentIdAndActiveTrue(assessment.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentAdminDetailResponse get(Long id) {
        AssessmentTest assessment = findAssessment(id);
        List<QuestionAdminResponse> questions = questionRepository
                .findByAssessmentIdOrderByDisplayOrderAsc(id).stream()
                .map(question -> AssessmentMapper.toQuestionAdminResponse(
                        question, optionRepository.findByQuestionIdOrderByDisplayOrderAsc(question.getId())))
                .toList();
        return new AssessmentAdminDetailResponse(
                assessment.getId(),
                assessment.getCareer().getId(),
                assessment.getCareer().getName(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getDurationMinutes(),
                assessment.isPublished(),
                questionRepository.countByAssessmentIdAndActiveTrue(assessment.getId()),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt(),
                questions);
    }

    @Transactional
    public AssessmentAdminResponse create(AssessmentRequest request) {
        Career career = findCareer(request.careerId());
        AssessmentTest assessment = AssessmentTest.builder()
                .career(career)
                .title(request.title().trim())
                .description(request.description())
                .durationMinutes(request.durationMinutes())
                .published(request.published() != null && request.published())
                .build();
        assessment = assessmentTestRepository.save(assessment);
        log.info("Admin created assessment {} '{}'", assessment.getId(), assessment.getTitle());
        return AssessmentMapper.toAdminResponse(assessment, 0);
    }

    @Transactional
    public AssessmentAdminResponse update(Long id, AssessmentRequest request) {
        AssessmentTest assessment = findAssessment(id);
        Career career = findCareer(request.careerId());
        assessment.setCareer(career);
        assessment.setTitle(request.title().trim());
        assessment.setDescription(request.description());
        assessment.setDurationMinutes(request.durationMinutes());
        if (request.published() != null) {
            assessment.setPublished(request.published());
        }
        assessment = assessmentTestRepository.save(assessment);
        log.info("Admin updated assessment {} '{}'", assessment.getId(), assessment.getTitle());
        return AssessmentMapper.toAdminResponse(
                assessment, questionRepository.countByAssessmentIdAndActiveTrue(assessment.getId()));
    }

    @Transactional
    public void delete(Long id) {
        AssessmentTest assessment = findAssessment(id);
        if (attemptRepository.existsByAssessmentId(id)) {
            throw BusinessException.conflict("Assessment has student attempts and cannot be deleted");
        }
        questionRepository.findByAssessmentIdOrderByDisplayOrderAsc(id)
                .forEach(question -> optionRepository.deleteByQuestionId(question.getId()));
        questionRepository.findByAssessmentIdOrderByDisplayOrderAsc(id)
                .forEach(questionRepository::delete);
        assessmentTestRepository.delete(assessment);
        log.info("Admin deleted assessment {} '{}'", assessment.getId(), assessment.getTitle());
    }

    @Transactional(readOnly = true)
    public List<QuestionAdminResponse> listQuestions(Long assessmentId) {
        findAssessment(assessmentId);
        return questionRepository.findByAssessmentIdOrderByDisplayOrderAsc(assessmentId).stream()
                .map(question -> AssessmentMapper.toQuestionAdminResponse(
                        question, optionRepository.findByQuestionIdOrderByDisplayOrderAsc(question.getId())))
                .toList();
    }

    @Transactional
    public QuestionAdminResponse createQuestion(Long assessmentId, QuestionRequest request) {
        AssessmentTest assessment = findAssessment(assessmentId);
        Skill skill = findSkill(request.skillId());
        validateOptions(request.options());
        List<QuestionOption> builtOptions = toOptions(request.options());
        int displayOrder = request.displayOrder() != null
                ? request.displayOrder()
                : questionRepository.findByAssessmentIdOrderByDisplayOrderAsc(assessmentId).stream()
                        .mapToInt(AssessmentQuestion::getDisplayOrder)
                        .max().orElse(0) + 1;
        AssessmentQuestion question = AssessmentQuestion.builder()
                .assessment(assessment)
                .skill(skill)
                .questionText(request.questionText().trim())
                .questionType(parseQuestionType(request.questionType()))
                .difficulty(CareerMapper.parseEnum(DifficultyLevel.class, request.difficulty(), "difficulty"))
                .explanation(request.explanation())
                .displayOrder(displayOrder)
                .active(request.active() == null || request.active())
                .build();
        question = questionRepository.save(question);
        persistOptions(question, builtOptions);
        log.info("Admin created question {} in assessment {}", question.getId(), assessmentId);
        return AssessmentMapper.toQuestionAdminResponse(
                question, optionRepository.findByQuestionIdOrderByDisplayOrderAsc(question.getId()));
    }

    @Transactional
    public QuestionAdminResponse updateQuestion(Long questionId, QuestionRequest request) {
        AssessmentQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> BusinessException.notFound("Question not found: " + questionId));
        AssessmentTest assessment = findAssessment(request.assessmentId());
        Skill skill = findSkill(request.skillId());
        validateOptions(request.options());
        question.setAssessment(assessment);
        question.setSkill(skill);
        question.setQuestionText(request.questionText().trim());
        question.setQuestionType(parseQuestionType(request.questionType()));
        question.setDifficulty(CareerMapper.parseEnum(DifficultyLevel.class, request.difficulty(), "difficulty"));
        question.setExplanation(request.explanation());
        if (request.displayOrder() != null) {
            question.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            question.setActive(request.active());
        }
        question = questionRepository.save(question);
        optionRepository.deleteByQuestionId(questionId);
        // Force the removals to the database before the replacement options
        // are inserted (queued deletes run after queued inserts at flush).
        optionRepository.flush();
        persistOptions(question, toOptions(request.options()));
        log.info("Admin updated question {}", questionId);
        return AssessmentMapper.toQuestionAdminResponse(
                question, optionRepository.findByQuestionIdOrderByDisplayOrderAsc(question.getId()));
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        AssessmentQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> BusinessException.notFound("Question not found: " + questionId));
        if (answerRepository.existsByQuestionId(questionId)
                || attemptQuestionRepository.existsByQuestionId(questionId)) {
            throw BusinessException.conflict("Question has student attempts and cannot be deleted");
        }
        optionRepository.deleteByQuestionId(questionId);
        questionRepository.delete(question);
        log.info("Admin deleted question {}", questionId);
    }

    private AssessmentTest findAssessment(Long id) {
        return assessmentTestRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Assessment not found: " + id));
    }

    private Career findCareer(Long id) {
        return careerRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Career not found: " + id));
    }

    private Skill findSkill(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Skill not found: " + id));
    }

    private QuestionType parseQuestionType(String value) {
        if (value == null || value.isBlank()) {
            return QuestionType.MCQ;
        }
        try {
            return QuestionType.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Invalid questionType '" + value + "'");
        }
    }

    private void validateOptions(List<QuestionOptionRequest> options) {
        if (options == null || options.size() < 2) {
            throw BusinessException.badRequest("A question needs at least 2 options");
        }
        long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.correct())).count();
        if (correctCount != 1) {
            throw BusinessException.badRequest("Exactly one option must be marked correct");
        }
        Set<String> seen = new HashSet<>();
        for (QuestionOptionRequest option : options) {
            String key = option.optionText().trim().toLowerCase();
            if (!seen.add(key)) {
                throw BusinessException.badRequest("Option text must be unique within a question");
            }
        }
    }

    private List<QuestionOption> toOptions(List<QuestionOptionRequest> options) {
        return options.stream()
                .map(option -> QuestionOption.builder()
                        .optionText(option.optionText().trim())
                        .correct(Boolean.TRUE.equals(option.correct()))
                        .displayOrder(option.displayOrder() != null
                                ? option.displayOrder()
                                : options.indexOf(option) + 1)
                        .build())
                .toList();
    }

    private void persistOptions(AssessmentQuestion question, List<QuestionOption> options) {
        for (QuestionOption option : options) {
            option.setQuestion(question);
            optionRepository.save(option);
        }
    }
}