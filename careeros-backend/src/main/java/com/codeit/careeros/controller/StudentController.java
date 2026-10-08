package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.student.StudentProfileRequest;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.service.StudentProfilePhotoService;
import com.codeit.careeros.service.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Student", description = "Student profile endpoints")
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentProfileService studentProfileService;
    private final StudentProfilePhotoService studentProfilePhotoService;

    @Operation(summary = "Get the profile of the authenticated student")
    @GetMapping("/me")
    public ApiResponse<StudentProfileResponse> getMyProfile() {
        return ApiResponse.success(studentProfileService.getCurrentProfile());
    }

    @Operation(summary = "Update the profile of the authenticated student")
    @PutMapping("/me")
    public ApiResponse<StudentProfileResponse> updateMyProfile(@Valid @RequestBody StudentProfileRequest request) {
        return ApiResponse.success("Profile updated", studentProfileService.updateCurrentProfile(request));
    }

    @Operation(summary = "Upload (or replace) the profile photo (JPG, PNG or WebP, max 2 MB)")
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StudentProfileResponse> uploadPhoto(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("Profile photo uploaded", studentProfilePhotoService.upload(file));
    }

    @Operation(summary = "Download the current student's own profile photo")
    @GetMapping("/me/photo")
    public ResponseEntity<Resource> downloadPhoto() {
        StudentProfilePhotoService.PhotoDownload download = studentProfilePhotoService.download();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.bytes().length)
                .body(new ByteArrayResource(download.bytes()));
    }

    @Operation(summary = "Remove the current student's profile photo")
    @DeleteMapping("/me/photo")
    public ApiResponse<StudentProfileResponse> deletePhoto() {
        return ApiResponse.success("Profile photo removed", studentProfilePhotoService.delete());
    }
}