package com.stylecommunicator.service;

import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.SituationRepository;
import com.stylecommunicator.service.situation.SituationSourceRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests SituationBankService: DB pick logic, low-stock trigger, fallback strings,
 * required word / emotional context selection, and recent-repeat avoidance.
 */
@ExtendWith(MockitoExtension.class)
class SituationBankServiceTest {

    @Mock private SituationRepository        repository;
    @Mock private PracticeSessionRepository  practiceSessionRepository;
    @Mock private SituationSourceRouter      router;

    private SituationBankService service;

    private final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SituationBankService(repository, practiceSessionRepository, router);
        // Default: user has no session history unless a test overrides this.
        lenient().when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(any()))
            .thenReturn(List.of());
    }

    // ── Happy-path: returns text from DB entry ────────────────────────────

    @Test
    void pickSituation_returnsTextFromDbEntry() {
        SituationEntry entry = entry("EQUAL", "B2", "You disagree with a peer on priorities.");
        when(repository.countByPowerAndLevel("EQUAL", "B2")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = service.pickSituation("EQUAL", "B2", USER);

        assertEquals("You disagree with a peer on priorities.", result);
    }

    // ── Backward-compat overload defaults to B2 ──────────────────────────

    @Test
    void pickSituation_twoArgOverloadDefaultsToB2() {
        SituationEntry entry = entry("DOMINANT", "B2", "A client pushes back.");
        when(repository.countByPowerAndLevel("DOMINANT", "B2")).thenReturn(10L);
        when(repository.findCandidates(eq("DOMINANT"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = service.pickSituation("DOMINANT", USER);

        assertEquals("A client pushes back.", result);
    }

    // ── Null/blank inputs normalise gracefully ────────────────────────────

    @Test
    void pickSituation_nullPowerNormalisesToEqual() {
        SituationEntry entry = entry("EQUAL", "B2", "Collaborate with a peer.");
        when(repository.countByPowerAndLevel("EQUAL", "B2")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.pickSituation(null, "B2", USER));
    }

    @Test
    void pickSituation_nullLevelNormalisesToB2() {
        SituationEntry entry = entry("DOMINANT", "B2", "Lead the meeting.");
        when(repository.countByPowerAndLevel("DOMINANT", "B2")).thenReturn(10L);
        when(repository.findCandidates(eq("DOMINANT"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.pickSituation("DOMINANT", null, USER));
    }

    // ── Replenishment trigger ─────────────────────────────────────────────

    @Test
    void pickSituation_triggersReplenishWhenStockBelowThreshold() {
        // Stock = 10, threshold = 40 → should trigger async replenish
        when(repository.countByPowerAndLevel("EQUAL", "B1")).thenReturn(10L);
        when(repository.findCandidates(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of(entry("EQUAL", "B1", "Resolve peer disagreement.")));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("EQUAL", "B1", USER);

        verify(router, times(1)).replenishAsync("EQUAL", "B1");
    }

    @Test
    void pickSituation_doesNotTriggerReplenishWhenStockAdequate() {
        when(repository.countByPowerAndLevel("DOMINANT", "B2")).thenReturn(50L);
        when(repository.findCandidates(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of(entry("DOMINANT", "B2", "Address a junior's pushback.")));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("DOMINANT", "B2", USER);

        verify(router, never()).replenishAsync(anyString(), anyString());
    }

    // ── Fallback when DB is empty ─────────────────────────────────────────

    @Test
    void pickSituation_returnsInlineFallbackWhenDbEmpty() {
        when(repository.countByPowerAndLevel(anyString(), anyString())).thenReturn(0L);
        when(repository.findCandidates(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of());

        String result = service.pickSituation("DOMINANT", "B2", USER);

        assertFalse(result.isBlank(), "Inline fallback must not be blank");
        // Replenishment should have been triggered
        verify(router, times(1)).replenishAsync(anyString(), anyString());
    }

    @Test
    void pickSituation_inlineFallbackIsDifferentPerPower() {
        when(repository.countByPowerAndLevel(anyString(), anyString())).thenReturn(0L);
        when(repository.findCandidates(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of());

        String dominant   = service.pickSituation("DOMINANT",   "B2", USER);
        String submissive = service.pickSituation("SUBMISSIVE", "B2", USER);
        String equal      = service.pickSituation("EQUAL",      "B2", USER);

        // All three should be different
        assertNotEquals(dominant, submissive);
        assertNotEquals(dominant, equal);
        assertNotEquals(submissive, equal);
    }

    // ── Avoids repeating a situation the user just saw ────────────────────

    @Test
    void pickSituation_excludesSituationsUserRecentlySaw() {
        SituationEntry seen  = entry("EQUAL", "B2", "Already seen this one.");
        SituationEntry fresh = entry("EQUAL", "B2", "Brand new situation.");

        com.stylecommunicator.entity.PracticeSession priorSession =
            new com.stylecommunicator.entity.PracticeSession();
        priorSession.setSituation("Already seen this one.");

        when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(List.of(priorSession));
        when(repository.countByPowerAndLevel("EQUAL", "B2")).thenReturn(50L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(seen, fresh));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Run many times — should never return the already-seen text
        // since "fresh" is the only entry left after filtering.
        for (int i = 0; i < 10; i++) {
            String result = service.pickSituation("EQUAL", "B2", USER);
            assertEquals("Brand new situation.", result);
        }
    }

    @Test
    void pickSituation_fallsBackToFullPoolWhenEverythingWasRecentlySeen() {
        SituationEntry onlyOption = entry("EQUAL", "B2", "Only situation available.");

        com.stylecommunicator.entity.PracticeSession priorSession =
            new com.stylecommunicator.entity.PracticeSession();
        priorSession.setSituation("Only situation available.");

        when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(List.of(priorSession));
        when(repository.countByPowerAndLevel("EQUAL", "B2")).thenReturn(50L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(onlyOption));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Even though the user has "seen" it, it's the only option in the
        // pool, so the service must still return it rather than fail.
        String result = service.pickSituation("EQUAL", "B2", USER);
        assertEquals("Only situation available.", result);
    }

    // ── Use count incremented ─────────────────────────────────────────────

    @Test
    void pickSituation_incrementsUseCountOnSelectedEntry() {
        SituationEntry entry = entry("EQUAL", "B2", "Handle a peer conflict.");
        entry.setUseCount(3);

        when(repository.countByPowerAndLevel("EQUAL", "B2")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("EQUAL", "B2", USER);

        // useCount should have been incremented to 4
        assertEquals(4, entry.getUseCount(), "useCount should be incremented after pick");
    }

    // ── pickRequiredWord ──────────────────────────────────────────────────

    @Test
    void pickRequiredWord_neverReturnsBlank() {
        for (int i = 0; i < 20; i++) {
            assertFalse(service.pickRequiredWord().isBlank(),
                "pickRequiredWord() returned blank on iteration " + i);
        }
    }

    // ── pickEmotionalContext ──────────────────────────────────────────────

    @Test
    void pickEmotionalContext_neverReturnsBlank() {
        for (int i = 0; i < 20; i++) {
            assertFalse(service.pickEmotionalContext().isBlank(),
                "pickEmotionalContext() returned blank on iteration " + i);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private SituationEntry entry(String power, String level, String text) {
        SituationEntry e = new SituationEntry();
        e.setPower(power);
        e.setLevel(level);
        e.setText(text);
        e.setSource("SEED");
        e.setUseCount(0);
        return e;
    }
}
