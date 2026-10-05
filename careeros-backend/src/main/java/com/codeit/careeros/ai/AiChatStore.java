package com.codeit.careeros.ai;

import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sprint 7 persistence behind short transactions. The chat loop in
 * {@link CareerAssistantService} performs LLM I/O outside transactions,
 * so every read/write here runs in its own dedicated transaction —
 * including the ownership check, which fails with 404 for foreign sessions.
 */
@Component
@RequiredArgsConstructor
public class AiChatStore {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AiChatSession loadOwnedSession(Long sessionId, Long userId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> BusinessException.notFound("Chat session not found"));
        if (!session.getUser().getId().equals(userId)) {
            throw BusinessException.notFound("Chat session not found");
        }
        return session;
    }

    @Transactional(readOnly = true)
    public User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public List<AiChatMessage> loadHistory(Long sessionId, Long userId, int limit) {
        AiChatSession session = loadOwnedSession(sessionId, userId);
        List<AiChatMessage> all =
                messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        int bounded = Math.max(2, Math.min(60, limit));
        return all.size() <= bounded ? all : all.subList(all.size() - bounded, all.size());
    }

    @Transactional
    public AiChatSession saveSession(AiChatSession session) {
        return sessionRepository.save(session);
    }

    @Transactional
    public void saveMessage(Long sessionId, Long userId, ChatRole role, String content) {
        AiChatSession session = loadOwnedSession(sessionId, userId);
        messageRepository.save(AiChatMessage.builder()
                .session(session)
                .role(role)
                .content(content)
                .build());
    }

    @Transactional
    public void touchSession(Long sessionId, Long userId, String firstMessage) {
        AiChatSession session = loadOwnedSession(sessionId, userId);
        long userTurns = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.USER)
                .count();
        if (userTurns <= 1) {
            String title = firstMessage.length() > 60 ? firstMessage.substring(0, 60) + "…" : firstMessage;
            session.setTitle(title);
        }
        sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        AiChatSession session = loadOwnedSession(sessionId, userId);
        messageRepository.deleteBySessionId(session.getId());
        sessionRepository.delete(session);
    }
}
