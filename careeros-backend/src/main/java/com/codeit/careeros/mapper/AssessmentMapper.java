package com.codeit.careeros.mapper;

import com.codeit.careeros.assessment.AssessmentAnswer;
import com.codeit.careeros.assessment.AssessmentAttempt;
import com.codeit.careeros.assessment.AssessmentQuestion;
import com.codeit.careeros.assessment.AssessmentTest;
import com.codeit.careeros.assessment.QuestionOption;
import com.codeit.careeros.assessment.SkillScore;
import com.codeit.careeros.dto.assessment.AssessmentAdminResponse;
import com.codeit.careeros.dto.assessment.AssessmentOverviewResponse;
import com.codeit.careeros.dto.assessment.AttemptQuestionResponse;
import com.codeit.careeros.dto.assessment.AttemptResultResponse;
import com.codeit.careeros.dto.assessment.AttemptStartResponse;
import com.codeit.careeros.dto.assessment.AttemptStateResponse;
import com.codeit.careeros.dto.assessment.OptionResponse;
import com.codeit.careeros.dto.assessment.QuestionAdminResponse;
import com.codeit.careeros.dto.assessment.QuestionOptionAdminResponse;
import com.codeit.careeros.dto.assessment.SkillScoreResponse;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AssessmentMapper {

    private AssessmentMapper() {
    }

    /** Saved option per question, so resumes restore the student's picks. */
    public static Map<Long, Long> selectedOptionByQuestion(List<AssessmentAnswer> answers) {
        Map<Long, Long> selected = new HashMap<>();
        for (AssessmentAnswer answer : answers) {
            if (answer.getSelectedOption() != null) {
                selected.put(answer.getQuestion().getId(), answer.getSelectedOption().getId());
            }
        }
        return selected;
    }

    public static AssessmentOverviewResponse toOverview(AssessmentTest assessment, long questionCount) {
        return new AssessmentOverviewResponse(
                assessment.getId(),
                assessment.getCareer().getId(),
                assessment.getCareer().getName(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getDurationMinutes(),
                questionCount);
    }

    public static AttemptQuestionResponse toQuestionResponse(
            AssessmentQuestion question, List<QuestionOption> options, Long selectedOptionId) {
        // Option order is the per-attempt order supplied by the service (shuffled
        // per attempt, display order from the database otherwise) — do NOT re-sort.
        return new AttemptQuestionResponse(
                question.getId(),
                question.getDisplayOrder(),
                question.getSkill().getName(),
                question.getSkill().getCategory().name(),
                question.getQuestionText(),
                question.getDifficulty().name(),
                selectedOptionId,
                options.stream()
                        .map(o -> new OptionResponse(o.getId(), o.getOptionText(), o.getDisplayOrder()))
                        .toList());
    }

    public static AttemptStartResponse toStartResponse(
            AssessmentAttempt attempt,
            List<AssessmentQuestion> questions,
            Map<Long, List<QuestionOption>> optionsByQuestionId,
            List<AssessmentAnswer> answers) {
        List<Long> answeredIds = answers.stream().map(a -> a.getQuestion().getId()).toList();
        Map<Long, Long> selectedByQuestion = selectedOptionByQuestion(answers);
        return new AttemptStartResponse(
                attempt.getId(),
                attempt.getAttemptCode(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                attempt.getAssessment().getCareer().getId(),
                attempt.getAssessment().getCareer().getName(),
                attempt.getAssessment().getDurationMinutes(),
                attempt.getTotalQuestions(),
                attempt.getStartedAt(),
                attempt.getExpiresAt(),
                answeredIds,
                questions.stream()
                        .map(q -> toQuestionResponse(
                                q,
                                optionsByQuestionId.getOrDefault(q.getId(), List.of()),
                                selectedByQuestion.get(q.getId())))
                        .toList());
    }

    public static AttemptStateResponse toStateResponse(
            AssessmentAttempt attempt,
            List<Long> answeredQuestionIds,
            List<AttemptQuestionResponse> questions,
            List<SkillScore> skillScores) {
        return new AttemptStateResponse(
                attempt.getId(),
                attempt.getAttemptCode(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                attempt.getAssessment().getCareer().getId(),
                attempt.getAssessment().getCareer().getName(),
                attempt.getStatus().name(),
                attempt.getAssessment().getDurationMinutes(),
                attempt.getTotalQuestions(),
                attempt.getAnsweredCount(),
                attempt.getOverallScore(),
                attempt.getStartedAt(),
                attempt.getExpiresAt(),
                attempt.getSubmittedAt(),
                answeredQuestionIds,
                questions,
                skillScores == null ? null : skillScores.stream().map(AssessmentMapper::toSkillScoreResponse).toList());
    }

    public static AttemptResultResponse toResultResponse(AssessmentAttempt attempt, List<SkillScore> skillScores) {
        return new AttemptResultResponse(
                attempt.getId(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                attempt.getAssessment().getCareer().getId(),
                attempt.getAssessment().getCareer().getName(),
                attempt.getStatus().name(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                attempt.getTotalQuestions(),
                attempt.getAnsweredCount(),
                attempt.getOverallScore(),
                skillScores.stream().map(AssessmentMapper::toSkillScoreResponse).toList());
    }

    public static SkillScoreResponse toSkillScoreResponse(SkillScore score) {
        return new SkillScoreResponse(
                score.getSkill().getId(),
                score.getSkill().getName(),
                score.getSkill().getCategory().name(),
                score.getWeightPercent(),
                score.getTargetPercent(),
                score.getQuestionCount(),
                score.getCorrectCount(),
                score.getScorePercent(),
                score.getLevel().name());
    }

    public static AssessmentAdminResponse toAdminResponse(AssessmentTest assessment, long questionCount) {
        return new AssessmentAdminResponse(
                assessment.getId(),
                assessment.getCareer().getId(),
                assessment.getCareer().getName(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getDurationMinutes(),
                assessment.isPublished(),
                questionCount,
                assessment.getCreatedAt());
    }

    public static QuestionAdminResponse toQuestionAdminResponse(
            AssessmentQuestion question, List<QuestionOption> options) {
        return new QuestionAdminResponse(
                question.getId(),
                question.getAssessment().getId(),
                question.getSkill().getId(),
                question.getSkill().getName(),
                question.getQuestionText(),
                question.getQuestionType().name(),
                question.getDifficulty().name(),
                question.getExplanation(),
                question.getDisplayOrder(),
                question.isActive(),
                question.getCreatedAt(),
                options.stream()
                        .sorted(Comparator.comparingInt(QuestionOption::getDisplayOrder))
                        .map(o -> new QuestionOptionAdminResponse(
                                o.getId(), o.getOptionText(), o.isCorrect(), o.getDisplayOrder()))
                        .toList());
    }
}