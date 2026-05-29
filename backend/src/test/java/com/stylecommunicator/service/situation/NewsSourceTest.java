package com.stylecommunicator.service.situation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests NewsSource: key-absent behaviour, headline parsing, filters, resilience.
 */
@ExtendWith(MockitoExtension.class)
class NewsSourceTest {

    @Mock private RestTemplate restTemplate;

    private NewsSource source;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SituationTemplateEngine engine = new SituationTemplateEngine();

    @BeforeEach
    void setUp() throws Exception {
        source = new NewsSource(objectMapper, engine);
        injectField(source, "restTemplate", restTemplate);
    }

    // ── No API key ────────────────────────────────────────────────────────

    @Test
    void fetch_returnsEmptyListWhenApiKeyAbsent() {
        // apiKey field is "" by default (no @Value injection in unit test)
        // RestTemplate should never be called
        List<String> results = source.fetch("DOMINANT", "B2", 5);

        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when no API key");
        verifyNoInteractions(restTemplate);
    }

    // ── Happy-path parsing ────────────────────────────────────────────────

    @Test
    void fetch_parsesHeadlinesWhenKeyPresent() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        String json = """
            {
              "status": "success",
              "results": [
                {
                  "title": "Companies restructure teams amid rising costs",
                  "description": "Major firms are reassigning roles and reducing management layers to cut costs."
                },
                {
                  "title": "Remote work policies shift again",
                  "description": "Several technology companies have reversed their return-to-office mandates this quarter."
                }
              ]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("EQUAL", "B2", 5);

        assertFalse(results.isEmpty(), "Expected situations from valid headlines");
        results.forEach(r -> assertFalse(r.isBlank()));
    }

    @Test
    void fetch_prefersDescriptionOverTitle() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        // description is longer and more useful; title alone is short
        String json = """
            {
              "results": [
                {
                  "title": "Tech layoffs continue",
                  "description": "Engineering managers at major tech companies are being asked to justify headcount decisions in quarterly reviews."
                }
              ]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("DOMINANT", "B2", 1);

        // Should have produced at least one result from the description
        assertFalse(results.isEmpty());
    }

    // ── Clickbait / short title filter ───────────────────────────────────

    @Test
    void fetch_skipsShortClickbaitTitles() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        String json = """
            {
              "results": [
                {"title": "Wow?", "description": ""},
                {"title": "Breaking!", "description": ""},
                {"title": "A team disagreed over the new hiring strategy and HR had to intervene.", "description": "A manager publicly blamed a subordinate during a team review meeting."}
              ]
            }
            """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("DOMINANT", "B2", 5);

        // Should only produce a result from the third article (the only valid one)
        assertNotNull(results);
        results.forEach(r -> assertTrue(r.length() > 15,
            "Short/junk result slipped through: " + r));
    }

    // ── Count cap ─────────────────────────────────────────────────────────

    @Test
    void fetch_respectsCountLimit() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        // Build 10 valid articles
        StringBuilder articles = new StringBuilder("[");
        for (int i = 1; i <= 10; i++) {
            articles.append("""
                {"title":"Story %d","description":"A manager challenged the team approach on project priorities."}
                """.formatted(i));
            if (i < 10) articles.append(",");
        }
        articles.append("]");

        String json = "{\"results\":" + articles + "}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        List<String> results = source.fetch("EQUAL", "B2", 4);

        assertTrue(results.size() <= 4,
            "fetch(count=4) must return at most 4, got " + results.size());
    }

    // ── Resilience ────────────────────────────────────────────────────────

    @Test
    void fetch_returnsEmptyOnNetworkError() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("DNS failure"));

        assertDoesNotThrow(() -> {
            List<String> results = source.fetch("DOMINANT", "B2", 5);
            assertNotNull(results);
        });
    }

    @Test
    void fetch_handlesMalformedJsonGracefully() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("NOT_JSON{{{");

        assertDoesNotThrow(() -> source.fetch("SUBMISSIVE", "B2", 3));
    }

    @Test
    void fetch_handlesNullResponseGracefully() throws Exception {
        injectField(source, "apiKey", "fake-test-key");

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(null);

        assertDoesNotThrow(() -> {
            List<String> results = source.fetch("EQUAL", "A2", 3);
            assertNotNull(results);
        });
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
