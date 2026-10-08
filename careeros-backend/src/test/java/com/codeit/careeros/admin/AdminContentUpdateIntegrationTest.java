package com.codeit.careeros.admin;

import com.codeit.careeros.TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the "replace children" admin flows: replacing a career's competency
 * framework and replacing a question's options. Both delete existing rows and
 * insert new ones in one transaction, so they must not trip unique keys.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminContentUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Replace the competency framework of an existing career")
    void replaceFramework() throws Exception {
        String token = adminToken();

        long skillA = createSkill(token, "Framework Skill A", "TESTING");
        long skillB = createSkill(token, "Framework Skill B", "TESTING");

        MvcResult created = mockMvc.perform(post("/api/v1/admin/careers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Framework Career",
                                  "category": "BUSINESS",
                                  "difficultyLevel": "BEGINNER",
                                  "published": true,
                                  "skills": [
                                    {"skillId": %d, "weightPercent": 100, "requiredLevel": "BASIC"}
                                  ]
                                }
                                """.formatted(skillA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills.length()").value(1))
                .andReturn();
        long careerId = parseId(created);

        mockMvc.perform(put("/api/v1/admin/careers/" + careerId + "/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"skillId": %d, "weightPercent": 60, "requiredLevel": "ADVANCED", "targetPercent": 80},
                                  {"skillId": %d, "weightPercent": 40, "requiredLevel": "INTERMEDIATE", "targetPercent": 75}
                                ]
                                """.formatted(skillA, skillB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/careers/" + careerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills.length()").value(2))
                .andExpect(jsonPath("$.data.skills[0].weightPercent").value(60));

        mockMvc.perform(put("/api/v1/admin/careers/" + careerId + "/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"skillId": %d, "weightPercent": 10, "requiredLevel": "BASIC"}
                                ]
                                """.formatted(skillA)))
                .andExpect(status().isBadRequest());

        deleteResource(token, "/api/v1/admin/careers/" + careerId);
        deleteResource(token, "/api/v1/admin/skills/" + skillA);
        deleteResource(token, "/api/v1/admin/skills/" + skillB);
    }

    @Test
    @DisplayName("Update a question replaces its options")
    void updateQuestionOptions() throws Exception {
        String token = adminToken();

        long skillId = createSkill(token, "Question Skill", "TESTING");

        MvcResult career = mockMvc.perform(post("/api/v1/admin/careers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Question Career",
                                  "category": "BUSINESS",
                                  "difficultyLevel": "BEGINNER",
                                  "published": true,
                                  "skills": [
                                    {"skillId": %d, "weightPercent": 100, "requiredLevel": "BASIC"}
                                  ]
                                }
                                """.formatted(skillId)))
                .andExpect(status().isOk())
                .andReturn();
        long careerId = parseId(career);

        MvcResult assessment = mockMvc.perform(post("/api/v1/admin/assessments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "careerId": %d,
                                  "title": "Question Assessment",
                                  "durationMinutes": 10,
                                  "published": true
                                }
                                """.formatted(careerId)))
                .andExpect(status().isOk())
                .andReturn();
        long assessmentId = parseId(assessment);

        MvcResult question = mockMvc.perform(post("/api/v1/admin/assessments/" + assessmentId + "/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentId": %d,
                                  "skillId": %d,
                                  "questionText": "Original?",
                                  "difficulty": "BEGINNER",
                                  "options": [
                                    {"optionText": "yes", "correct": true},
                                    {"optionText": "no", "correct": false}
                                  ]
                                }
                                """.formatted(assessmentId, skillId)))
                .andExpect(status().isOk())
                .andReturn();
        long questionId = parseId(question);

        mockMvc.perform(put("/api/v1/admin/questions/" + questionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentId": %d,
                                  "skillId": %d,
                                  "questionText": "Updated?",
                                  "difficulty": "INTERMEDIATE",
                                  "options": [
                                    {"optionText": "alpha", "correct": false},
                                    {"optionText": "beta", "correct": true},
                                    {"optionText": "gamma", "correct": false}
                                  ]
                                }
                                """.formatted(assessmentId, skillId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionText").value("Updated?"))
                .andExpect(jsonPath("$.data.options.length()").value(3));

        deleteResource(token, "/api/v1/admin/questions/" + questionId);
        deleteResource(token, "/api/v1/admin/assessments/" + assessmentId);
        deleteResource(token, "/api/v1/admin/careers/" + careerId);
        deleteResource(token, "/api/v1/admin/skills/" + skillId);
    }

    private long createSkill(String token, String name, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/skills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "category": "%s"}
                                """.formatted(name, category)))
                .andExpect(status().isOk())
                .andReturn();
        return parseId(result);
    }

    private long parseId(MvcResult result) throws Exception {
        JsonNode node = mapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    private void deleteResource(String token, String path) throws Exception {
        mockMvc.perform(delete(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@test.local",
                                  "password": "Admin@123Test"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return TestSupport.token(login, "accessToken");
    }
}


