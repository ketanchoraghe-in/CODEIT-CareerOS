package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.cv.CvAnalysisResponse;
import com.codeit.careeros.dto.cv.CvDocumentResponse;
import com.codeit.careeros.service.CvAnalysisService;
import com.codeit.careeros.service.CvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sprint 5 student CV: upload/replace, current document, download and
 * analysis. Everything is scoped to the authenticated student — a student
 * can only ever see their own CV.
 */
@Tag(name = "CV", description = "CV upload, download and analysis")
@RestController
@RequestMapping("/api/v1/cvs")
@RequiredArgsConstructor
public class CvController {

    private final CvService cvService;
    private final CvAnalysisService cvAnalysisService;

    @Operation(summary = "Upload a CV (PDF, DOC or DOCX, max 5 MB); replaces the previous one")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CvDocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("CV uploaded", cvService.upload(file));
    }

    @Operation(summary = "Current student's CV, or null when none was uploaded yet")
    @GetMapping("/me")
    public ApiResponse<CvDocumentResponse> myDocument() {
        return ApiResponse.success(cvService.myDocument());
    }

    @Operation(summary = "Analysis of the current student's CV against their target career")
    @GetMapping("/analysis")
    public ApiResponse<CvAnalysisResponse> analyze() {
        return ApiResponse.success(cvAnalysisService.analyze());
    }

    @Operation(summary = "Download the current student's own CV file")
    @GetMapping("/download")
    public ResponseEntity<Resource> download() {
        CvService.CvDownload download = cvService.download();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename())
                        .build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.bytes().length)
                .body(new ByteArrayResource(download.bytes()));
    }
}
