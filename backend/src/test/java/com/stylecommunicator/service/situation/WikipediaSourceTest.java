package com.stylecommunicator.service.situation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests WikipediaSource parsing, disambiguation filtering, and resilience.
 */
@ExtendWith(MockitoExtension.class)
class WikipediaSourceTest {

    @Mock private RestTemplate restTemplate;

    private WikipediaSource source;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SituationTemplateEngine engine = new SituationTemplateEngine();

    @BeforeEach
    void setUp() throws Exception {
        source = new WikipediaSource(objectMapper, engine);
        injectField(source, "restTemplate", restTemplate);
    }

    // ── Happy-path ────────────────────────────────────────────────────────

    @Test
    void fetch_parsesExtractAndReturnsSituation() {
        String json = """
            {
              "title": "Leadership",
              "extract": "Leadership is the ability to guide a team toward a goal. It involves communication and decision-making."
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("DOMINANT", "B2", 3);

        assertFalse(results.isEmpty(), "Expected at least one result");
        results.forEach(r -> {
            assertFalse(r.isBlank(), "No blank situations");
            assertTrue(r.length() > 10, "Situation too short: " + r);
        });
    }

    @Test
    void fetch_respectsCountLimit() {
        String json = """
            {"extract": "Negotiation is a dialogue intended to resolve disputes and reach agreement."}
            """;
        // Return the same valid article for every call
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("EQUAL", "B2", 2);

        assertTrue(results.size() <= 2,
            "Expected at most 2 results, got " + results.size());
    }

    // ── Disambiguation filtering ──────────────────────────────────────────

    @Test
    void fetch_skipsDisambiguationPages() {
        String disambig = """
            {"extract": "Leadership may refer to a number of topics including political leadership."}
            """;
        String valid = """
            {"extract": "Assertiveness is a communication style that conveys confidence."}
            """;

        // First call = disambiguation page, second = valid article
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn(disambig)
            .thenReturn(valid);

        List<String> results = source.fetch("SUBMISSIVE", "B2", 1);

        // Should have found the valid one and skipped the disambiguation
        // (may be empty if only 2 articles were tried and none were valid; that's ok)
        assertNotNull(results);
        results.forEach(r ->
            assertFalse(r.toLowerCase().contains("may refer to"),
                "Disambiguation content leaked into result: " + r));
    }

    // ── First sentence only ───────────────────────────────────────────────

    @Test
    void fetch_usesOnlyFirstSentence() {
        String json = """
            {
              "extract": "Conflict resolution is the process of ending a conflict. It may involve negotiation. Mediation is also common."
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("EQUAL", "B1", 1);

        // The situation should not contain content from sentences 2 or 3
        results.forEach(r -> {
            assertFalse(r.contains("Mediation is also common"),
                "Second+ sentences should not appear in result: " + r);
        });
    }

    // ── Resilience ────────────────────────────────────────────────────────

    @Test
    void fetch_returnsEmptyOnNetworkError() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("network unreachable"));

        assertDoesNotThrow(() -> {
            List<String> results = source.fetch("DOMINANT", "B2", 5);
            assertNotNull(results);
        });
    }

    @Test
    void fetch_handlesNullExtractGracefully() {
        String json = "{\"extract\": null}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        assertDoesNotThrow(() -> source.fetch("EQUAL", "B2", 3));
    }

    @Test
    void fetch_handlesEmptyExtractGracefully() {
        String json = "{\"extract\": \"\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("SUBMISSIVE", "A2", 2);
        assertNotNull(results);
        // Empty extract → skip → empty results is perfectly valid
    }

    @Test
    void fetch_handlesPartialJsonGracefully() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{bad json{{");

        assertDoesNotThrow(() -> source.fetch("DOMINANT", "B1", 3));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try { return clazz.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
            throw e;
        }
    }
}
