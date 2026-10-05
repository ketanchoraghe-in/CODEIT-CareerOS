package com.codeit.careeros.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup guard that verifies the {@link ChatModel} resolved for the
 * configured {@code AI_PROVIDER} is the expected implementation.
 *
 * <p>Selection itself is handled by {@link AiChatModelResolver}, so a mismatch
 * can only happen when the wiring is bypassed — e.g. an explicit
 * {@code spring.ai.model.chat} pin that contradicts {@code AI_PROVIDER}, which
 * would silently bill/call the wrong provider. That is logged as an error at
 * startup instead of failing silently.
 *
 * <p>Explicit {@code @Primary} models (test stubs, deliberate user overrides)
 * are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderConsistencyCheck {

    private final AiProperties aiProperties;
    private final AiChatModelResolver chatModels;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        ChatModel active;
        try {
            active = chatModels.active();
        } catch (Exception ex) {
            log.error("AI provider check: no ChatModel could be resolved for AI_PROVIDER={}. "
                    + "Chat will use the offline fallback or report AI_ERROR.", aiProperties.getProvider());
            return;
        }
        String modelClass = active.getClass().getName();
        boolean isOpenAi = modelClass.equals("org.springframework.ai.openai.OpenAiChatModel");
        boolean isGemini = modelClass.equals("org.springframework.ai.google.genai.GoogleGenAiChatModel");
        if (!isOpenAi && !isGemini) {
            return;
        }
        if (aiProperties.isGemini() && !isGemini) {
            log.error("AI provider mismatch: AI_PROVIDER=gemini but the active ChatModel is {} ({}). "
                    + "Replies are NOT coming from Gemini. Check spring.ai.model.chat overrides "
                    + "and set the provider via the AI_PROVIDER environment variable.",
                    modelClass, chatModels.activeBeanName());
        } else if (!aiProperties.isGemini() && !isOpenAi) {
            log.error("AI provider mismatch: AI_PROVIDER={} but the active ChatModel is {} ({}). "
                            + "Check spring.ai.model.chat overrides.",
                    aiProperties.getProvider(), modelClass, chatModels.activeBeanName());
        } else {
            log.info("AI provider active: provider={} model={} chatModel={}",
                    aiProperties.getProvider(), aiProperties.effectiveModel(),
                    active.getClass().getSimpleName());
        }
    }
}
