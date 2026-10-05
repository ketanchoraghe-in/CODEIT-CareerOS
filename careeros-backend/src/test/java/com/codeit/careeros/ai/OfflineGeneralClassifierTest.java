package com.codeit.careeros.ai;

import com.codeit.careeros.service.CareerInsightService;
import com.codeit.careeros.service.CvAnalysisService;
import com.codeit.careeros.service.LinkedInService;
import com.codeit.careeros.service.ProjectService;
import com.codeit.careeros.service.RoadmapService;
import com.codeit.careeros.service.StudentProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the general-vs-personal classifier used when the configured
 * LLM fails: purely general questions get an honest "provider unavailable"
 * message, everything else keeps its grounded offline answer.
 */
class OfflineGeneralClassifierTest {

    private OfflineGuidanceEngine engine() {
        return new OfflineGuidanceEngine(
                Mockito.mock(StudentProfileService.class),
                Mockito.mock(CareerInsightService.class),
                Mockito.mock(RoadmapService.class),
                Mockito.mock(ProjectService.class),
                Mockito.mock(CvAnalysisService.class),
                Mockito.mock(LinkedInService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What is Java?",
            "Explain Spring Boot.",
            "Write a Java program to reverse a string.",
            "Explain recursion.",
            "What is REST API?",
            "Give me Java interview questions.",
            "Explain ArrayList vs LinkedList.",
            "Help me write a professional email.",
            "What is trending in Java right now?"
    })
    @DisplayName("Purely general questions are classified as general knowledge")
    void generalQuestions_classifiedGeneral(String message) {
        assertThat(engine().isGeneralKnowledgeOnly(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What are my skill gaps?",
            "How ready am I?",
            "How good am I at Java?",
            "What should I learn next?",
            "Which projects should I build?",
            "Explain Spring Boot and tell me how good I am at it.",
            "What skills do I need to become a Java developer and which am I missing?",
            "hello",
            "Why is it useful?",
            "Is it difficult to learn?"
    })
    @DisplayName("Personal, mixed, greetings and follow-ups are NOT general-only")
    void nonGeneralQuestions_notGeneralOnly(String message) {
        assertThat(engine().isGeneralKnowledgeOnly(message)).isFalse();
    }

    @Test
    @DisplayName("Blank input is not general-only")
    void blank_notGeneralOnly() {
        assertThat(engine().isGeneralKnowledgeOnly("   ")).isFalse();
        assertThat(engine().isGeneralKnowledgeOnly(null)).isFalse();
    }
}
