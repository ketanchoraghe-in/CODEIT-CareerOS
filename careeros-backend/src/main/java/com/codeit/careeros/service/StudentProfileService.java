package com.codeit.careeros.service;

import com.codeit.careeros.dto.student.StudentProfileRequest;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.entity.StudentProfile;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.StudentProfileMapper;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public StudentProfileResponse getCurrentProfile() {
        return StudentProfileMapper.toResponse(getProfileForCurrentUser());
    }

    @Transactional
    public StudentProfileResponse updateCurrentProfile(StudentProfileRequest request) {
        StudentProfile profile = getProfileForCurrentUser();
        StudentProfileMapper.applyRequest(profile, request);
        profile = studentProfileRepository.save(profile);
        log.info("Updated profile for student {} ({})", profile.getStudentId(), profile.getUser().getId());
        return StudentProfileMapper.toResponse(profile);
    }

    private StudentProfile getProfileForCurrentUser() {
        Long userId = SecurityUtils.currentUserId();
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Student profile not found for user " + userId));
    }
}