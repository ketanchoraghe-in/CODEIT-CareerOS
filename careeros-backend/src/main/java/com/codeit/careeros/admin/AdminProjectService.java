package com.codeit.careeros.admin;

import com.codeit.careeros.dto.admin.AdminProjectResponse;
import com.codeit.careeros.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin read-only browser for the project catalog (published and drafts). No mutations. */
@Service
@RequiredArgsConstructor
public class AdminProjectService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<AdminProjectResponse> listAll() {
        return projectRepository.findAll(Sort.by("career.id").ascending().and(Sort.by("displayOrder").ascending()))
                .stream()
                .map(project -> new AdminProjectResponse(
                        project.getId(),
                        project.getTitle(),
                        project.getDescription(),
                        project.getCareer() != null ? project.getCareer().getId() : null,
                        project.getCareer() != null ? project.getCareer().getName() : null,
                        project.getDifficulty() != null ? project.getDifficulty().name() : null,
                        project.getEstimatedWeeks(),
                        project.getDisplayOrder(),
                        project.isPublished(),
                        project.getCreatedAt()))
                .toList();
    }
}
