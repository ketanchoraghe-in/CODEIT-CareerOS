package com.codeit.careeros.ai;

import com.codeit.careeros.dto.ai.AiStatusResponse;
import com.codeit.careeros.dto.ai.ChatMessageResponse;
import com.codeit.careeros.dto.ai.ChatReplyResponse;
import com.codeit.careeros.dto.ai.ChatSessionResponse;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.exception.ErrorCode;
import com.codeit.careeros.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sprint 7 AI Career Assistant orchestration.
 *
 * <p>Flow per student message: validate + rate-limit, run a bounded native
 * tool-calling loop (system prompt + MySQL chat history + the allowlisted
 * CareerOS tools, driven by the LLM itself through Spring AI function
 * calling) with a per-call timeout, then persist BOTH turns and return the
 * reply. Persisting only after success means a provider outage never leaves a
 * dangling user message with no answer.
 *
 * <p>The LLM is the conversational intelligence: it decides per question
 * whether CareerOS data is needed, calls the approved tools (possibly in
 * multiple steps), and composes the final natural-language answer from real
 * tool results. There is no keyword routing in this service.
 *
 * <p>Security properties:
 * <ul>
 *   <li>Sessions/messages are owner-scoped — any access to another
 *       student's session fails with 404 (no existence leak).</li>
 *   <li>The model NEVER sees database credentials, SQL, or other
 *       students: tools run in Java against the existing owner-scoped
 *       Sprint 1–6 services and only their structured results re-enter
 *       the prompt.</li>
 *   <li>Without a configured key every send fails fast with 503 before
 *       anything is persisted or any network call made.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareerAssistantService {

    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int HISTORY_MESSAGE_CHARS = 1500;
    private static final int TOOL_OUTPUT_CHARS = 6000;
    private static final int MAX_TOOL_ITERATIONS_HARD_CAP = 10;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatStore store;
    private final AiProperties aiProperties;
    private final AiChatModelResolver chatModels;
    private final CareerAssistantTools tools;
    private final OfflineGuidanceEngine offlineEngine;

    /**
     * Lightweight in-memory per-student rate limiter for the chat endpoint.
     * Instance-local (no extra infra): keeps at most one deque of recent
     * request timestamps per student and prunes entries older than 60s.
     */
    private final ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> rateBuckets =
            new ConcurrentHashMap<>();
    /** Bounded pool so timed-out provider calls cannot leak threads. */
    private final ExecutorService modelExecutor = Executors.newCachedThreadPool();

    public AiStatusResponse status() {
        boolean configured = aiProperties.isConfigured();
        return new AiStatusResponse(
                configured,
                aiProperties.getProvider(),
                aiProperties.effectiveModel(),
                aiProperties.isOfflineFallbackEnabled(),
                configured ? "llm" : "offline-smart");
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions() {
        Long userId = SecurityUtils.currentUserId();
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> new ChatSessionResponse(
                        session.getId(), session.getTitle(),
                        session.getCreatedAt(), session.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ChatSessionResponse createSession(String title) {
        Long userId = SecurityUtils.currentUserId();
        User user = store.loadUser(userId);
        String resolved = title == null || title.isBlank() ? "New conversation" : title.strip();
        if (resolved.length() > 190) {
            resolved = resolved.substring(0, 190);
        }
        AiChatSession session = sessionRepository.save(AiChatSession.builder()
                .user(user)
                .title(resolved)
                .build());
        return new ChatSessionResponse(
                session.getId(), session.getTitle(), session.getCreatedAt(), session.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(Long sessionId) {
        AiChatSession session = ownedSession(sessionId, SecurityUtils.currentUserId());
        return store.loadHistory(session.getId(), SecurityUtils.currentUserId(), Integer.MAX_VALUE).stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(), message.getRole().name(),
                        message.getContent(), message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        store.deleteSession(sessionId, SecurityUtils.currentUserId());
    }

    /**
     * Runs one assistant turn. LLM I/O happens outside any DB transaction;
     * persistence uses short dedicated transactions (see helpers below).
     *
     * <p>The user turn is persisted AFTER a successful provider+tool loop so a
     * provider outage never leaves a dangling user message with no reply.
     *
     * <p>Offline-first (default {@code AI_OFFLINE_FALLBACK=true}): when no LLM
     * is configured OR the provider fails/times out, the built-in
     * {@link OfflineGuidanceEngine} answers from verified CareerOS data so the
     * chat NEVER goes silent like a dead chatbot.
     */
    public ChatReplyResponse sendMessage(Long sessionId, String message) {
        Long userId = SecurityUtils.currentUserId();
        AiChatSession session = store.loadOwnedSession(sessionId, userId);
        String text = message == null ? "" : message.strip();
        if (text.isEmpty()) {
            throw BusinessException.badRequest("Message must not be blank");
        }
        if (text.length() > MAX_MESSAGE_CHARS) {
            throw BusinessException.badRequest("Message must be at most 4000 characters");
        }
        // Only valid, owned turns consume the per-minute budget.
        checkRateLimit(userId);
        if (!aiProperties.isConfigured()) {
            if (aiProperties.isOfflineFallbackEnabled()) {
                return offlineReply(session.getId(), userId, text, false);
            }
            throw BusinessException.serviceUnavailable(aiProperties.isGemini()
                    ? "AI assistant is not configured. Set GEMINI_API_KEY (or AI_API_KEY) "
                            + "and optionally AI_PROVIDER=gemini/AI_MODEL on the backend and try again."
                    : "AI assistant is not configured. Set AI_API_KEY (and optionally "
                            + "AI_PROVIDER/AI_MODEL/AI_BASE_URL) on the backend and try again.");
        }

        List<AiChatMessage> history = store.loadHistory(
                session.getId(), userId, aiProperties.getHistoryLimit());

        List<Message> prompt = new ArrayList<>();
        prompt.add(new SystemMessage(systemPrompt()));
        for (AiChatMessage past : history) {
            String content = truncate(past.getContent(), HISTORY_MESSAGE_CHARS);
            prompt.add(past.getRole() == ChatRole.USER ? new UserMessage(content) : new AssistantMessage(content));
        }
        prompt.add(new UserMessage(truncate(text, MAX_MESSAGE_CHARS)));

        // Native function calling: the LLM sees the tool schemas and decides
        // itself whether CareerOS data is needed. Tool execution stays manual
        // (internalToolExecutionEnabled=false) so the iteration budget,
        // tools-used tracking and error handling below apply uniformly.
        ToolCallback[] toolCallbacks = ToolCallbacks.from(tools);
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (ToolCallback callback : toolCallbacks) {
            callbacks.put(callback.getToolDefinition().name(), callback);
        }
        ChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();

        List<String> toolsUsed = new ArrayList<>();
        try {
            LoopResult loop = runNativeLoop(prompt, toolOptions, callbacks, toolsUsed, text);
            store.saveMessage(session.getId(), userId, ChatRole.USER, text);
            store.saveMessage(session.getId(), userId, ChatRole.ASSISTANT, loop.reply());
            store.touchSession(session.getId(), userId, text);
            // If the model burned its tool budget, the final answer came from
            // the grounded offline engine — badge it as offline-smart.
            String mode = loop.offlineFallback() ? "offline-smart" : "llm";
            return new ChatReplyResponse(
                    sessionId, loop.reply(), List.copyOf(toolsUsed), loop.offlineFallback(), mode);
        } catch (BusinessException ex) {
            // Provider outage / timeout (502) falls back to grounded offline
            // guidance instead of leaving the student with an error bubble.
            if (ex.getErrorCode() == ErrorCode.AI_ERROR && aiProperties.isOfflineFallbackEnabled()) {
                log.warn("LLM provider failed ({}), falling back to offline guidance",
                        ex.getMessage());
                return offlineReply(session.getId(), userId, text, true);
            }
            throw ex;
        }
    }

    /**
     * Always-respond path: runs the zero-key, zero-network guidance engine,
     * persists BOTH turns and returns a ChatGPT-style grounded reply.
     *
     * @param providerFailure true when the configured LLM was reachable but
     *                        failed (as opposed to never configured). In that
     *                        case purely general-knowledge questions get an
     *                        honest "provider unavailable" message instead of
     *                        a hardcoded answer pretending to be the LLM, while
     *                        CareerOS questions still use the offline engine.
     */
    private ChatReplyResponse offlineReply(Long sessionId, Long userId, String text, boolean providerFailure) {
        List<AiChatMessage> history = List.of();
        try {
            history = store.loadHistory(sessionId, userId, aiProperties.getHistoryLimit());
        } catch (Exception ignored) {
        }
        List<String> historyTexts = history.stream()
                .map(AiChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        if (providerFailure && offlineEngine.isGeneralKnowledgeOnly(text)) {
            String reply = "I'm having trouble reaching the AI provider right now, so I can't "
                    + "generate a fresh answer for that general question.\n\n"
                    + "Please try again in a moment — your conversation is saved. In the meantime "
                    + "I can still help with anything grounded in your CareerOS data, like your "
                    + "**skill gaps**, **readiness**, **roadmap**, **projects**, **CV** or **LinkedIn**.";
            store.saveMessage(sessionId, userId, ChatRole.USER, text);
            store.saveMessage(sessionId, userId, ChatRole.ASSISTANT, reply);
            store.touchSession(sessionId, userId, text);
            return new ChatReplyResponse(sessionId, reply, List.of(), true, "offline-smart");
        }
        OfflineGuidanceEngine.GuidanceResult result;
        try {
            result = offlineEngine.reply(text, historyTexts);
        } catch (Exception ex) {
            log.warn("Offline guidance failed: {}", ex.getClass().getSimpleName());
            result = new OfflineGuidanceEngine.GuidanceResult(
                    "I'm here to help with your career! Ask me about your **skill gaps**, "
                            + "**readiness**, **roadmap**, **projects**, **CV** or **LinkedIn** — "
                            + "for example: *What skills am I missing for my target career?*",
                    List.of());
        }
        String reply = result.reply() == null || result.reply().isBlank()
                ? "I'm here to help with your career! Ask me about your **skill gaps**, **readiness**, or **what to learn next**."
                : result.reply();
        store.saveMessage(sessionId, userId, ChatRole.USER, text);
        store.saveMessage(sessionId, userId, ChatRole.ASSISTANT, reply);
        store.touchSession(sessionId, userId, text);
        return new ChatReplyResponse(
                sessionId, reply, List.copyOf(result.toolsUsed()), true, "offline-smart");
    }

    /**
     * Native tool-calling loop. Each turn the model either answers directly
     * (general knowledge — no tools involved) or returns function calls that
     * are executed here against the allowlisted CareerAssistantTools; the
     * structured results re-enter the prompt and the model decides the next
     * step, until it answers or the iteration budget is spent.
     */
    private LoopResult runNativeLoop(List<Message> prompt, ChatOptions toolOptions,
            Map<String, ToolCallback> callbacks, List<String> toolsUsed, String userText) {
        int maxIterations = Math.max(1, Math.min(MAX_TOOL_ITERATIONS_HARD_CAP,
                aiProperties.getMaxToolIterations()));
        for (int i = 0; i < maxIterations; i++) {
            AssistantMessage output = callModel(prompt, toolOptions);
            if (!output.hasToolCalls()) {
                String answer = output.getText();
                if (answer == null || answer.isBlank()) {
                    throw BusinessException.aiError("AI provider returned an empty response");
                }
                return new LoopResult(answer.strip(), false);
            }
            List<ToolResponse> responses = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                responses.add(new ToolResponse(
                        toolCall.id(), toolCall.name(),
                        executeNativeTool(callbacks, toolCall, toolsUsed)));
            }
            // The assistant message WITH its tool calls plus the tool results
            // is what the provider needs to continue the function-call round.
            prompt.add(output);
            prompt.add(ToolResponseMessage.builder().responses(responses).build());
        }
        // Model kept calling tools and never answered: fall back to the
        // grounded offline engine so the student still gets a full answer
        // instead of a "ran out of steps" dead-end.
        if (aiProperties.isOfflineFallbackEnabled()) {
            try {
                List<String> historyTexts = prompt.stream()
                        .filter(message -> message instanceof UserMessage)
                        .map(Message::getText)
                        .filter(text -> text != null)
                        .toList();
                OfflineGuidanceEngine.GuidanceResult offline = offlineEngine.reply(userText, historyTexts);
                for (String t : offline.toolsUsed()) {
                    if (!toolsUsed.contains(t)) {
                        toolsUsed.add(t);
                    }
                }
                return new LoopResult(offline.reply(), true);
            } catch (Exception ex) {
                log.warn("Offline fallback after tool exhaustion failed: {}",
                        ex.getClass().getSimpleName());
            }
        }
        return new LoopResult("I gathered your CareerOS data but ran out of steps. "
                + "Please ask a smaller, focused question (for example about one skill or one roadmap step).",
                false);
    }

    /**
     * Executes one model-requested function call. Only allowlisted tools run;
     * anything else becomes an error observation the model must work around.
     * Tools execute here in the request thread, where the student's security
     * context is intact — identity always comes from that context, never
     * from the model.
     */
    private String executeNativeTool(Map<String, ToolCallback> callbacks,
            AssistantMessage.ToolCall toolCall, List<String> toolsUsed) {
        String name = toolCall.name();
        ToolCallback callback = name == null ? null : callbacks.get(name);
        if (callback == null) {
            return "{\"error\":\"Unknown tool '"
                    + (name == null ? "" : name.replace("\"", "'"))
                    + "'. Available tools: " + String.join(", ", tools.toolNames()) + "\"}";
        }
        String arguments = toolCall.arguments();
        if (arguments == null || arguments.isBlank()) {
            arguments = "{}";
        }
        try {
            String observation = callback.call(arguments);
            if (observation == null || observation.isBlank()) {
                observation = "{\"empty\":true}";
            }
            if (!toolsUsed.contains(name)) {
                toolsUsed.add(name);
            }
            return truncate(observation, TOOL_OUTPUT_CHARS);
        } catch (IllegalArgumentException ex) {
            return "{\"error\":\"Invalid tool arguments. Continue without it.\"}";
        } catch (Exception ex) {
            log.warn("Assistant tool {} failed: {}", name, ex.getClass().getSimpleName());
            return "{\"error\":\"Tool temporarily unavailable. Continue without it.\"}";
        }
    }

    private AssistantMessage callModel(List<Message> prompt, ChatOptions toolOptions) {
        int timeoutSeconds = Math.max(1, Math.min(300, aiProperties.getRequestTimeoutSeconds()));
        List<Message> snapshot = List.copyOf(prompt);
        ChatModel activeModel;
        try {
            activeModel = chatModels.active();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI model resolution failed: {}", ex.getClass().getSimpleName());
            throw BusinessException.aiError("AI service is temporarily unavailable. Please try again.");
        }
        // Tools execute in the request thread (see executeNativeTool), but a
        // provider model could theoretically run callbacks inline — propagate
        // the student's security context so identity never goes missing.
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Future<ChatResponse> future = modelExecutor.submit(() -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(securityContext);
            try {
                return activeModel.call(new Prompt(snapshot, toolOptions));
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        });
        try {
            ChatResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                throw BusinessException.aiError("AI provider returned an empty response");
            }
            return response.getResult().getOutput();
        } catch (BusinessException ex) {
            future.cancel(true);
            throw ex;
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("AI provider call timed out after {}s", timeoutSeconds);
            throw BusinessException.aiError("AI provider timed out. Please try again.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw BusinessException.aiError("AI service is temporarily unavailable. Please try again.");
        } catch (Exception ex) {
            future.cancel(true);
            log.warn("AI provider call failed: {}", ex.getClass().getSimpleName());
            throw BusinessException.aiError(safeProviderMessage(ex));
        }
    }

    /** Sliding-window per-student guard: HTTP 429 instead of unbounded spend. */
    private void checkRateLimit(Long userId) {
        int limit = Math.max(1, Math.min(120, aiProperties.getRateLimitPerMinute()));
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        ConcurrentLinkedDeque<Long> bucket =
                rateBuckets.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
                bucket.pollFirst();
            }
            if (bucket.size() >= limit) {
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "You're sending messages too quickly. Please wait and try again.", 429);
            }
            bucket.addLast(now);
        }
    }

    /**
     * Maps raw provider exceptions to user-safe messages without leaking
     * internals (no class names, URLs, keys, or stack details).
     */
    private String safeProviderMessage(Exception ex) {
        String raw = String.valueOf(ex.getMessage()).toLowerCase();
        if (raw.contains("unauthorized") || raw.contains("invalid api key")
                || raw.contains("incorrect api key") || raw.contains("api key not valid")
                || raw.contains("api-key") || raw.contains("apikey")
                || raw.contains("unauthenticated") || raw.contains("permission denied")
                || raw.contains(" 401") || raw.contains(" 403")) {
            return "AI provider rejected the request (invalid credentials). "
                    + "An administrator should check the AI provider key.";
        }
        if (raw.contains("model") && (raw.contains("not found") || raw.contains("does not exist"))) {
            return "The configured AI model is unavailable. An administrator should check AI_MODEL.";
        }
        if (raw.contains("429") || raw.contains("rate limit") || raw.contains("quota")
                || raw.contains("resource exhausted") || raw.contains("resource_exhausted")) {
            return "AI service is rate-limited right now. Please wait a moment and try again.";
        }
        if (raw.contains("timeout") || raw.contains("timed out")) {
            return "AI provider timed out. Please try again with a shorter question.";
        }
        return "AI service is temporarily unavailable. Please try again.";
    }

    private String systemPrompt() {
        return """
                You are the CODEIT CareerOS AI Assistant: a friendly, general-purpose
                student assistant having a natural chat conversation. You have function
                tools that return THIS student's own verified CareerOS data (profile,
                readiness, skill gaps, assessments, roadmap, projects, CV, LinkedIn).
                You decide yourself, per question, whether you need them.
                HOW TO HANDLE EACH QUESTION:
                1. GENERAL questions ("What is Java?", "Explain Spring Boot", "Write a
                   Java program", "Give me Java interview questions", "Help me write a
                   professional email", any technical or everyday doubt): answer DIRECTLY
                   from your own knowledge. Do NOT call any tool. Be clear, correct and
                   student-friendly, with a short example or code snippet where useful.
                2. PERSONAL questions about THIS student's own data ("my skill gaps",
                   "how ready am I", "how good am I at X", "what should I learn next",
                   "which project should I build", CV/LinkedIn/roadmap/assessment):
                   FIRST call the relevant tool(s), then answer ONLY from the tool
                   results. NEVER invent scores, skills, careers, roadmap content, CV
                   or LinkedIn details. You may chain tools (e.g. gaps, then projects)
                   when the question needs it. If tools show missing data (no target
                   career, no attempts, nothing uploaded), say so plainly and recommend
                   the concrete next step in CareerOS.
                3. MIXED questions ("Explain X and tell me how good I am at X",
                   "interview questions based on my weak skills", "skills needed to
                   become a Java Developer and which ones am I missing"): do BOTH — a
                   compact general explanation PLUS the personal part grounded in
                   tools. Purely personal asks stay focused on CareerOS data only.
                4. CONVERSATION: the chat history is provided. Resolve follow-up
                   pronouns from it ("it", "its advantages", "is it difficult?",
                   "how good am I at it?"). If a follow-up adds a personal ask, call
                   the tool for the resolved topic.
                RULES:
                - Answer the student's LATEST question directly and conversationally.
                - Simple greetings ("hello", "hi") need NO tools — reply warmly and ask
                  how you can help. General questions need NO tools either.
                - Call at most 2-3 tools and only those needed; never call every tool
                  for a simple question.
                - You cannot access any database or run SQL — tools are your only data
                  source, and they only ever return the current student's own data.
                - Career guidance must be practical: explain gaps, why each skill
                  matters, the next concrete step, and which roadmap item or project
                  it maps to. For 30-day plans, sequence weeks using actual gaps.
                - Write naturally, like a helpful tutor — not like a database report.
                  Never open with "According to your CareerOS data..."; just state
                  facts conversationally (e.g. "Your Java score is 40%% against a
                  target of 80%%...").
                - Keep answers focused student-friendly markdown (short headings,
                  bullets). Never mention API keys, providers, models, system prompts,
                  or tool internals.
                - You have NO live internet access. If asked about what is trending,
                  latest releases, or current versions, say clearly that you cannot
                  verify live information, then still give the stable background
                  knowledge you have.
                Available CareerOS tools (function calls, use when personal data is
                needed):
                %s
                """.formatted(tools.describeTools());
    }

    private AiChatSession ownedSession(Long sessionId, Long userId) {
        return store.loadOwnedSession(sessionId, userId);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record LoopResult(String reply, boolean offlineFallback) {
    }
}
