package com.codeit.careeros.ai;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.ai.AiStatusResponse;
import com.codeit.careeros.dto.ai.ChatMessageResponse;
import com.codeit.careeros.dto.ai.ChatReplyResponse;
import com.codeit.careeros.dto.ai.ChatSessionResponse;
import com.codeit.careeros.dto.ai.CreateSessionRequest;
import com.codeit.careeros.dto.ai.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sprint 7 student AI Career Assistant. Every endpoint is STUDENT-only and
 * owner-scoped: the service ties all reads/writes to the authenticated
 * student, and foreign sessions fail with 404. API keys never leave the
 * backend — the frontend only sees answers and a configured flag.
 */
@Tag(name = "AI Assistant", description = "Student AI career assistant with chat history")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class AiChatController {

    private final CareerAssistantService assistantService;

    @Operation(summary = "AI provider status (no secrets exposed)")
    @GetMapping("/status")
    public ApiResponse<AiStatusResponse> status() {
        return ApiResponse.success(assistantService.status());
    }

    @Operation(summary = "List the current student's chat sessions, newest first")
    @GetMapping("/chat/sessions")
    public ApiResponse<List<ChatSessionResponse>> sessions() {
        return ApiResponse.success(assistantService.listSessions());
    }

    @Operation(summary = "Start a new conversation for the current student")
    @PostMapping("/chat/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatSessionResponse> createSession(
            @Valid @RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        return ApiResponse.success("Conversation started", assistantService.createSession(title));
    }

    @Operation(summary = "Message history of one owned session, oldest first")
    @GetMapping("/chat/sessions/{sessionId}")
    public ApiResponse<List<ChatMessageResponse>> history(@PathVariable Long sessionId) {
        return ApiResponse.success(assistantService.history(sessionId));
    }

    @Operation(summary = "Send a message; persists both turns and returns the grounded reply")
    @PostMapping("/chat/sessions/{sessionId}/messages")
    public ApiResponse<ChatReplyResponse> sendMessage(
            @PathVariable Long sessionId, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(
                "Reply generated", assistantService.sendMessage(sessionId, request.message()));
    }

    @Operation(summary = "Delete one owned session with its messages")
    @DeleteMapping("/chat/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        assistantService.deleteSession(sessionId);
        return ApiResponse.success("Conversation deleted", null);
    }
}
