package com.codeit.careeros.ai;

import com.codeit.careeros.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Selects the active Spring AI {@link ChatModel} for the configured
 * {@code AI_PROVIDER} at call time.
 *
 * <p>Both provider starters coexist on the classpath, so up to two
 * {@code ChatModel} beans exist ({@code openAiChatModel} from the
 * OpenAI-compatible starter — also used for Ollama/custom endpoints via
 * {@code AI_BASE_URL} — and {@code googleGenAiChatModel} from the official
 * Google GenAI starter). Injecting {@code ChatModel} by type would be
 * ambiguous, and gating auto-configurations via {@code spring.ai.model.chat}
 * cannot see test/yml-level provider settings early enough, so selection
 * happens here instead — fully deterministic in every environment.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>An explicit {@code @Primary} {@code ChatModel} wins (test stubs and
 *       deliberate user overrides).</li>
 *   <li>Otherwise the bean matching {@code AI_PROVIDER}: {@code gemini} →
 *       {@code googleGenAiChatModel}, anything else → {@code openAiChatModel}.</li>
 *   <li>Otherwise, if exactly one {@code ChatModel} exists (e.g. the user
 *       pinned {@code spring.ai.model.chat} to a single implementation), use it.</li>
 * </ol>
 *
 * <p>The resolved model is cached: the provider cannot change without a
 * restart. A missing model surfaces as {@code AI_ERROR} (502), which the
 * service maps to the offline fallback when enabled — the chat never goes
 * silent.
 */
@Component
@RequiredArgsConstructor
public class AiChatModelResolver {

    static final String OPENAI_BEAN = "openAiChatModel";
    static final String GEMINI_BEAN = "googleGenAiChatModel";

    private final AiProperties aiProperties;
    private final ApplicationContext context;

    private volatile ChatModel cached;

    /** The {@link ChatModel} for the active provider. */
    public ChatModel active() {
        ChatModel hit = cached;
        if (hit != null) {
            return hit;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            cached = resolve();
            return cached;
        }
    }

    /** Bean name of the active model (for status logs and consistency checks). */
    public String activeBeanName() {
        for (String name : context.getBeanNamesForType(ChatModel.class)) {
            if (context.getBean(name, ChatModel.class) == active()) {
                return name;
            }
        }
        return active().getClass().getSimpleName();
    }

    private ChatModel resolve() {
        String[] names = context.getBeanNamesForType(ChatModel.class);
        // 1. Explicit @Primary wins (test stubs, user overrides).
        for (String name : names) {
            if (isPrimary(name)) {
                return context.getBean(name, ChatModel.class);
            }
        }
        // 2. Provider mapping.
        String preferred = aiProperties.isGemini() ? GEMINI_BEAN : OPENAI_BEAN;
        for (String name : names) {
            if (preferred.equals(name)) {
                return context.getBean(name, ChatModel.class);
            }
        }
        // 3. Single implementation (user pinned spring.ai.model.chat).
        if (names.length == 1) {
            return context.getBean(names[0], ChatModel.class);
        }
        throw BusinessException.aiError("AI provider is temporarily unavailable. Please try again.");
    }

    private boolean isPrimary(String beanName) {
        if (context instanceof ConfigurableApplicationContext configurable) {
            try {
                return configurable.getBeanFactory().getBeanDefinition(beanName).isPrimary();
            } catch (NoSuchBeanDefinitionException ex) {
                return false;
            }
        }
        return false;
    }
}
