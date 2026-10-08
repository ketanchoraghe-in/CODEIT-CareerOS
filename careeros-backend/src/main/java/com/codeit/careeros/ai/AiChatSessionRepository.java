package com.codeit.careeros.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    List<AiChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
