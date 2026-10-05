package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.report.AssessmentReportResponse;
import com.codeit.careeros.dto.report.OverallReportResponse;
import com.codeit.careeros.dto.report.ReportSummary;
import com.codeit.careeros.dto.roadmap.RoadmapResponse;
import com.codeit.careeros.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Student reports: JSON views for the Reports page plus real PDF
 * downloads generated from live data. Everything is owner-scoped to
 * the authenticated student.
 */
@Tag(name = "Reports", description = "Student career reports with PDF downloads")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "List the available reports with live-data status")
    @GetMapping
    public ApiResponse<List<ReportSummary>> list() {
        return ApiResponse.success(reportService.listReports());
    }

    @Operation(summary = "Career readiness report (JSON)")
    @GetMapping("/readiness")
    public ApiResponse<ReadinessResponse> readiness() {
        return ApiResponse.success(reportService.readinessReport());
    }

    @Operation(summary = "Skill assessment report (JSON)")
    @GetMapping("/assessment")
    public ApiResponse<AssessmentReportResponse> assessment() {
        return ApiResponse.success(reportService.assessmentReport());
    }

    @Operation(summary = "Career gap analysis report (JSON)")
    @GetMapping("/gap-analysis")
    public ApiResponse<ReadinessResponse> gapAnalysis() {
        return ApiResponse.success(reportService.gapReport());
    }

    @Operation(summary = "Learning / roadmap progress report (JSON)")
    @GetMapping("/roadmap")
    public ApiResponse<RoadmapResponse> roadmap() {
        return ApiResponse.success(reportService.roadmapReport());
    }

    @Operation(summary = "Overall CareerOS progress report (JSON)")
    @GetMapping("/overall")
    public ApiResponse<OverallReportResponse> overall() {
        return ApiResponse.success(reportService.overallReport());
    }

    @Operation(summary = "Download a report as PDF (generated from live data)")
    @GetMapping("/{type}/download")
    public ResponseEntity<Resource> download(@PathVariable String type) {
        byte[] pdf = reportService.generatePdf(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(reportService.pdfFilename(type))
                        .build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(new ByteArrayResource(pdf));
    }
}
