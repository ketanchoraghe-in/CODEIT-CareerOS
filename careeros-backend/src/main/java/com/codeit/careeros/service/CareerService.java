package com.codeit.careeros.service;

import com.codeit.careeros.career.Career;
import com.codeit.careeros.career.CareerSkill;
import com.codeit.careeros.dto.career.CareerResponse;
import com.codeit.careeros.dto.career.CareerSkillResponse;
import com.codeit.careeros.dto.career.CareerSummaryResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.entity.StudentProfile;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.CareerMapper;
import com.codeit.careeros.mapper.StudentProfileMapper;
import com.codeit.careeros.repository.CareerRepository;
import com.codeit.careeros.repository.CareerSkillRepository;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read side of the career master (public) plus the student's target career
 * selection. Writes to the career master itself live in the admin module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerService {

    private final CareerRepository careerRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public List<CareerSummaryResponse> listPublishedCareers() {
        return careerRepository.findAllByPublishedTrueOrderByNameAsc().stream()
                .map(career -> CareerMapper.toSummary(
                        career, careerSkillRepository.countByCareerId(career.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerResponse getPublishedCareer(Long id) {
        Career career = careerRepository.findByIdAndPublishedTrue(id)
                .orElseThrow(() -> BusinessException.notFound("Career not found: " + id));
        List<CareerSkill> skills = careerSkillRepository.findByCareerIdOrderByWeightPercentDesc(id);
        return CareerMapper.toResponse(career, skills);
    }

    @Transactional(readOnly = true)
    public List<CareerSkillResponse> getCareerSkills(Long careerId) {
        Career career = careerRepository.findByIdAndPublishedTrue(careerId)
                .orElseThrow(() -> BusinessException.notFound("Career not found: " + careerId));
        return careerSkillRepository.findByCareerIdOrderByWeightPercentDesc(career.getId()).stream()
                .map(CareerMapper::toSkillResponse)
                .toList();
    }

    /** Career must exist and be published for a student to target it. */
    @Transactional
    public StudentProfileResponse setTargetCareer(Long careerId) {
        Long userId = SecurityUtils.currentUserId();
        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Student profile not found for user " + userId));

        Career career = careerRepository.findByIdAndPublishedTrue(careerId)
                .orElseThrow(() -> BusinessException.notFound("Career not found or not available: " + careerId));

        profile.setTargetCareer(career);
        profile = studentProfileRepository.save(profile);
        log.info("Student {} set target career to {} ({})", profile.getStudentId(), career.getName(), career.getId());
        return StudentProfileMapper.toResponse(profile);
    }
}
