package com.codeit.careeros.admin;

import com.codeit.careeros.career.Career;
import com.codeit.careeros.career.CareerSkill;
import com.codeit.careeros.dto.career.CareerRequest;
import com.codeit.careeros.dto.career.CareerResponse;
import com.codeit.careeros.dto.career.CareerSkillRequest;
import com.codeit.careeros.dto.career.CareerSummaryResponse;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.CareerMapper;
import com.codeit.careeros.repository.AssessmentTestRepository;
import com.codeit.careeros.repository.CareerRepository;
import com.codeit.careeros.repository.CareerSkillRepository;
import com.codeit.careeros.repository.SkillRepository;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.skill.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin write side of the career master and the competency framework
 * (career &rarr; skill weights). Weights must form a valid distribution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCareerService {

    private final CareerRepository careerRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final SkillRepository skillRepository;
    private final AssessmentTestRepository assessmentTestRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public List<CareerSummaryResponse> listAll() {
        return careerRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(career -> CareerMapper.toSummary(
                        career, careerSkillRepository.countByCareerId(career.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerResponse get(Long id) {
        Career career = findCareer(id);
        return CareerMapper.toResponse(career, careerSkillRepository.findByCareerIdOrderByWeightPercentDesc(id));
    }

    @Transactional
    public CareerResponse create(CareerRequest request) {
        if (careerRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw BusinessException.conflict("A career named '" + request.name().trim() + "' already exists");
        }
        Career career = new Career();
        CareerMapper.applyRequest(career, request);
        career = careerRepository.save(career);
        if (request.skills() != null) {
            replaceSkills(career, request.skills());
        }
        log.info("Admin created career {} ({})", career.getName(), career.getId());
        return get(career.getId());
    }

    @Transactional
    public CareerResponse update(Long id, CareerRequest request) {
        Career career = findCareer(id);
        String trimmedName = request.name().trim();
        careerRepository.findByNameIgnoreCase(trimmedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("A career named '" + trimmedName + "' already exists");
                });
        CareerMapper.applyRequest(career, request);
        career = careerRepository.save(career);
        if (request.skills() != null) {
            replaceSkills(career, request.skills());
        }
        log.info("Admin updated career {} ({})", career.getName(), career.getId());
        return get(career.getId());
    }

    /** Replaces the whole competency framework of a career (docs section 12). */
    @Transactional
    public CareerResponse replaceSkills(Long careerId, List<CareerSkillRequest> skillRequests) {
        return replaceSkills(findCareer(careerId), skillRequests);
    }

    @Transactional
    public CareerResponse replaceSkills(Career career, List<CareerSkillRequest> skillRequests) {
        if (skillRequests == null || skillRequests.isEmpty()) {
            throw BusinessException.badRequest("At least one skill mapping is required");
        }
        validateSkillRequests(skillRequests);
        careerSkillRepository.deleteByCareerId(career.getId());
        // Force the removals to the database now: replacement inserts are
        // flushed before queued deletes, which would trip the unique key.
        careerSkillRepository.flush();
        Map<Long, Skill> skills = skillRepository.findAllById(
                        skillRequests.stream().map(CareerSkillRequest::skillId).toList())
                .stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
        for (CareerSkillRequest request : skillRequests) {
            Skill skill = skills.get(request.skillId());
            if (skill == null) {
                throw BusinessException.badRequest("Skill not found: " + request.skillId());
            }
            careerSkillRepository.save(CareerSkill.builder()
                    .career(career)
                    .skill(skill)
                    .weightPercent(request.weightPercent())
                    .requiredLevel(CareerMapper.parseSkillLevel(request.requiredLevel(), "requiredLevel"))
                    .targetPercent(request.targetPercent() != null ? request.targetPercent() : 80)
                    .build());
        }
        log.info("Admin updated competency framework for career {} ({} skills)",
                career.getName(), skillRequests.size());
        return get(career.getId());
    }

    @Transactional
    public void delete(Long id) {
        Career career = findCareer(id);
        if (assessmentTestRepository.existsByCareerId(id)) {
            throw BusinessException.conflict("Career is used by assessments and cannot be deleted");
        }
        if (studentProfileRepository.existsByTargetCareerId(id)) {
            throw BusinessException.conflict("Career is targeted by students and cannot be deleted");
        }
        careerSkillRepository.deleteByCareerId(id);
        careerRepository.delete(career);
        log.info("Admin deleted career {} ({})", career.getName(), career.getId());
    }

    private Career findCareer(Long id) {
        return careerRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Career not found: " + id));
    }

    private void validateSkillRequests(List<CareerSkillRequest> requests) {
        long distinctSkills = requests.stream().map(CareerSkillRequest::skillId).distinct().count();
        if (distinctSkills != requests.size()) {
            throw BusinessException.badRequest("A skill can only be mapped once per career");
        }
        int totalWeight = requests.stream().mapToInt(CareerSkillRequest::weightPercent).sum();
        if (totalWeight != 100) {
            throw BusinessException.badRequest(
                    "Skill weights must total exactly 100% (current total: " + totalWeight + "%)");
        }
        for (CareerSkillRequest request : requests) {
            CareerMapper.parseSkillLevel(request.requiredLevel(), "requiredLevel");
            if (request.targetPercent() != null) {
                int target = request.targetPercent();
                if (target < 1 || target > 100) {
                    throw BusinessException.badRequest("Target percent must be between 1 and 100");
                }
            }
        }
    }
}