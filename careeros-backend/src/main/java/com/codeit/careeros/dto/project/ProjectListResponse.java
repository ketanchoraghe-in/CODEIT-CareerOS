package com.codeit.careeros.dto.project;

import java.util.List;

/**
 * Project recommendations of the current student for their target career.
 * Without a target career this is an empty payload ({@code hasTarget=false}).
 */
public record ProjectListResponse(
        Long careerId,
        String careerName,
        Boolean hasTarget,
        Integer totalProjects,
        Integer completedProjects,
        Integer inProgressProjects,
        List<ProjectResponse> projects) {
}
