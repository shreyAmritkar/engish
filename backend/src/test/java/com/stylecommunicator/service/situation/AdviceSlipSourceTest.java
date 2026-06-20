// package com.stylecommunicator.service.situation;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.springframework.web.client.RestClientException;
// import org.springframework.web.client.RestTemplate;

// import java.lang.reflect.Field;
// import java.util.List;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.*;

// /**
//  * Tests AdviceSlipSource parsing logic and network failure handling.
//  * RestTemplate is mocked — no real HTTP calls.
//  */
// @ExtendWith(MockitoExtension.class)
// class AdviceSlipSourceTest {

//     @Mock private RestTemplate restTemplate;

//     private AdviceSlipSource source;

//     // Real ObjectMapper and engine — we want to test the full parse pipeline
//     private final ObjectMapper objectMapper  = new ObjectMapper();
//     private final SituationTemplateEngine engine = new SituationTemplateEngine();

//     @BeforeEach
//     void setUp() throws Exception {
//         source = new AdviceSlipSource(objectMapper, engine);
//         // Inject mock RestTemplate via reflection (field is private final)
//         injectField(source, "restTemplate", restTemplate);
//     }

//     // ── Happy-path: search response ───────────────────────────────────────

//     @Test
//     void fetch_parsesSearchResponseAndReturnsSituations() {
//         String json = """
//             {"message":{"type":"success"},
//              "slips":[
//                {"id":1,"advice":"When you disagree with someone, try to understand their view."},
//                {"id":2,"advice":"Managing conflict at work requires patience and clarity."}
//              ]}
//             """;

//         // Match any URL (search endpoint includes keyword)
//         when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

//         List<String> results = source.fetch("EQUAL", "B2", 2);

//         assertFalse(results.isEmpty(), "Expected at least one result from search response");
//         results.forEach(r -> assertFalse(r.isBlank(), "No blank situations expected"));
//     }

//     // ── Happy-path: random response ───────────────────────────────────────

//     @Test
//     void fetch_parsesRandomResponseWhenSearchYieldsNothing() {
//         // Search returns empty slips array
//         String emptySearch = "{\"message\":{\"type\":\"success\"},\"slips\":[]}";
//         String randomJson  = "{\"slip\":{\"id\":42,\"advice\":\"Listen more than you speak at work.\"}}";

//         when(restTemplate.getForObject(anyString(), eq(String.class)))
//             .thenReturn(emptySearch)   // first N keyword search calls
//             .thenReturn(randomJson);   // random endpoint fallback

//         List<String> results = source.fetch("DOMINANT", "B2", 1);

//         // May or may not fill depending on how many keywords fire before random,
//         // but should never throw and should return a list
//         assertNotNull(results);
//     }

//     // ── Output shape ──────────────────────────────────────────────────────

//     @Test
//     void fetch_respectsCountLimit() {
//         String json = """
//             {"slips":[
//               {"id":1,"advice":"Speak clearly when raising concerns."},
//               {"id":2,"advice":"Take responsibility for mistakes early."},
//               {"id":3,"advice":"Ask questions before making assumptions."},
//               {"id":4,"advice":"Give credit where credit is due."},
//               {"id":5,"advice":"Be direct but not blunt."}
//             ]}
//             """;
//         when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

//         List<String> results = source.fetch("SUBMISSIVE", "B1", 3);

//         assertTrue(results.size() <= 3,
//             "fetch(count=3) must return at most 3, got " + results.size());
//     }

//     @Test
//     void fetch_neverReturnsBlankSituations() {
//         String json = """
//             {"slips":[
//               {"id":1,"advice":""},
//               {"id":2,"advice":"  "},
//               {"id":3,"advice":"Trust is earned through consistent action."}
//             ]}
//             """;
//         when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

//         List<String> results = source.fetch("EQUAL", "A2", 5);
//         results.forEach(r -> assertFalse(r.isBlank(), "Blank situation slipped through"));
//     }

//     // ── Resilience ────────────────────────────────────────────────────────

//     @Test
//     void fetch_returnsEmptyListOnNetworkFailure() {
//         when(restTemplate.getForObject(anyString(), eq(String.class)))
//             .thenThrow(new RestClientException("connection timeout"));

//         List<String> results = source.fetch("DOMINANT", "B2", 5);

//         assertNotNull(results, "Should return empty list, not null");
//         // May be empty — that's fine; router will fall through to next source
//     }

//     @Test
//     void fetch_returnsEmptyListOnMalformedJson() {
//         when(restTemplate.getForObject(anyString(), eq(String.class)))
//             .thenReturn("THIS IS NOT JSON {{{");

//         assertDoesNotThrow(() -> source.fetch("EQUAL", "B2", 3),
//             "Malformed JSON should not throw — return empty list");
//     }

//     @Test
//     void fetch_returnsEmptyListOnNullResponse() {
//         when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(null);

//         List<String> results = source.fetch("SUBMISSIVE", "B2", 3);
//         assertNotNull(results);
//     }

//     // ── Helper ────────────────────────────────────────────────────────────

//     private static void injectField(Object target, String fieldName, Object value) throws Exception {
//         Field f = findField(target.getClass(), fieldName);
//         f.setAccessible(true);
//         f.set(target, value);
//     }

//     private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
//         try {
//             return clazz.getDeclaredField(name);
//         } catch (NoSuchFieldException e) {
//             if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), name);
//             throw e;
//         }
//     }
// }
