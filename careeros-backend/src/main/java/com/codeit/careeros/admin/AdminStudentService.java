package com.codeit.careeros.admin;

import com.codeit.careeros.common.PageResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.entity.StudentProfile;
import com.codeit.careeros.mapper.StudentProfileMapper;
import com.codeit.careeros.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStudentService {

    private final StudentProfileRepository studentProfileRepository;

    @Transactional(readOnly = true)
    public PageResponse<StudentProfileResponse> listStudents(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StudentProfile> result = studentProfileRepository.findAll(pageable);
        return new PageResponse<>(
                result.getContent().stream().map(StudentProfileMapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long countStudents() {
        return studentProfileRepository.count();
    }
}