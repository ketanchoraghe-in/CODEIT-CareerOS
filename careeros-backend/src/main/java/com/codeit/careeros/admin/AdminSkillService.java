package com.codeit.careeros.admin;

import com.codeit.careeros.dto.skill.SkillRequest;
import com.codeit.careeros.dto.skill.SkillResponse;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.SkillMapper;
import com.codeit.careeros.repository.AssessmentQuestionRepository;
import com.codeit.careeros.repository.CareerSkillRepository;
import com.codeit.careeros.repository.SkillRepository;
import com.codeit.careeros.skill.Skill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin write side of the skill master and skill categories. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSkillService {

    private final SkillRepository skillRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final AssessmentQuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<SkillResponse> listAll() {
        return skillRepository.findAllByOrderByNameAsc().stream()
                .map(SkillMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillResponse get(Long id) {
        return SkillMapper.toResponse(findSkill(id));
    }

    @Transactional
    public SkillResponse create(SkillRequest request) {
        if (skillRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw BusinessException.conflict("A skill named '" + request.name().trim() + "' already exists");
        }
        Skill skill = new Skill();
        SkillMapper.applyRequest(skill, request);
        skill = skillRepository.save(skill);
        log.info("Admin created skill {} ({})", skill.getName(), skill.getId());
        return SkillMapper.toResponse(skill);
    }

    @Transactional
    public SkillResponse update(Long id, SkillRequest request) {
        Skill skill = findSkill(id);
        String trimmedName = request.name().trim();
        skillRepository.findByNameIgnoreCase(trimmedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("A skill named '" + trimmedName + "' already exists");
                });
        SkillMapper.applyRequest(skill, request);
        skill = skillRepository.save(skill);
        log.info("Admin updated skill {} ({})", skill.getName(), skill.getId());
        return SkillMapper.toResponse(skill);
    }

    @Transactional
    public void delete(Long id) {
        Skill skill = findSkill(id);
        if (careerSkillRepository.existsBySkillId(id)) {
            throw BusinessException.conflict("Skill is mapped to a career competency framework and cannot be deleted");
        }
        if (questionRepository.countBySkillId(id) > 0) {
            throw BusinessException.conflict("Skill is used by assessment questions and cannot be deleted");
        }
        skillRepository.delete(skill);
        log.info("Admin deleted skill {} ({})", skill.getName(), skill.getId());
    }

    private Skill findSkill(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Skill not found: " + id));
    }
}