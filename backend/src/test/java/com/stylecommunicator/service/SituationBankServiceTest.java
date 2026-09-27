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

@ExtendWith(MockitoExtension.class)
class SituationBankServiceTest {

    @Mock private SituationRepository       repository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private SituationSourceRouter     router;
    @Mock private SituationLoadingService   loadingService;
    private SituationBankService service;

    private final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SituationBankService(repository, practiceSessionRepository, router, loadingService);
        lenient().when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(any()))
            .thenReturn(List.of());
    }

    // ── Happy-path: returns text from DB entry ────────────────────────────

    @Test
    void pickSituation_returnsTextFromDbEntry() {
        SituationEntry entry = entry("EQUAL", "B2", "You disagree with a peer on priorities.");
        when(repository.countByPowerAndLevelAndContext("EQUAL", "B2", "PROFESSIONAL")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = service.pickSituation("EQUAL", "B2", USER, 7, "MODERATE");

        assertEquals("You disagree with a peer on priorities.", result);
    }

    // ── Backward-compat overload defaults to PROFESSIONAL context ─────────

    @Test
    void pickSituation_threeArgOverloadUsesDefaultContext() {
        SituationEntry entry = entry("DOMINANT", "B2", "A client pushes back.");
        when(repository.countByPowerAndLevelAndContext("DOMINANT", "B2", "PROFESSIONAL")).thenReturn(10L);
        when(repository.findCandidates(eq("DOMINANT"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = service.pickSituation("DOMINANT", "B2", USER);

        assertEquals("A client pushes back.", result);
    }

    // ── Null/blank inputs normalise gracefully ────────────────────────────

    @Test
    void pickSituation_nullPowerNormalisesToEqual() {
        SituationEntry entry = entry("EQUAL", "B2", "Collaborate with a peer.");
        when(repository.countByPowerAndLevelAndContext("EQUAL", "B2", "PROFESSIONAL")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.pickSituation(null, "B2", USER, 7, "MODERATE"));
    }

    @Test
    void pickSituation_nullLevelNormalisesToB2() {
        SituationEntry entry = entry("DOMINANT", "B2", "Lead the meeting.");
        when(repository.countByPowerAndLevelAndContext("DOMINANT", "B2", "PROFESSIONAL")).thenReturn(10L);
        when(repository.findCandidates(eq("DOMINANT"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> service.pickSituation("DOMINANT", null, USER, 7, "MODERATE"));
    }

    // ── Replenishment trigger ─────────────────────────────────────────────

    @Test
    void pickSituation_triggersReplenishWhenStockBelowThreshold() {
        when(repository.countByPowerAndLevelAndContext("EQUAL", "B1", "PROFESSIONAL")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B1"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry("EQUAL", "B1", "Resolve peer disagreement.")));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("EQUAL", "B1", USER, 7, "MODERATE");

        verify(router, times(1)).replenishAsync("EQUAL", "B1", "PROFESSIONAL");
    }

    @Test
    void pickSituation_doesNotTriggerReplenishWhenStockAdequate() {
        when(repository.countByPowerAndLevelAndContext("DOMINANT", "B2", "PROFESSIONAL")).thenReturn(50L);
        when(repository.findCandidates(eq("DOMINANT"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry("DOMINANT", "B2", "Address a junior's pushback.")));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("DOMINANT", "B2", USER, 7, "MODERATE");

        verify(router, never()).replenishAsync(anyString(), anyString(), anyString());
    }

    // ── Context-specific pool empty → fallback to any-context ────────────

    @Test
    void pickSituation_fallsBackToAnyContextWhenContextPoolEmpty() {
        when(repository.countByPowerAndLevelAndContext("EQUAL", "B2", "DRAMATIC")).thenReturn(0L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), eq("DRAMATIC"), any(Pageable.class)))
            .thenReturn(List.of()); // dramatic pool empty
        when(repository.findCandidatesAnyContext(eq("EQUAL"), eq("B2"), any(Pageable.class)))
            .thenReturn(List.of(entry("EQUAL", "B2", "Fallback professional situation.")));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = service.pickSituation("EQUAL", "B2", USER, 2, "EXPRESSIVE");

        assertEquals("Fallback professional situation.", result);
    }

    // ── Fallback when DB is completely empty ──────────────────────────────

    @Test
    void pickSituation_returnsInlineFallbackWhenDbEmpty() {
        when(repository.countByPowerAndLevelAndContext(anyString(), anyString(), anyString())).thenReturn(0L);
        when(repository.findCandidates(anyString(), anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of());
        when(repository.findCandidatesAnyContext(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of());

        String result = service.pickSituation("DOMINANT", "B2", USER, 7, "MODERATE");

        assertFalse(result.isBlank(), "Inline fallback must not be blank");
        verify(router, times(1)).replenishAsync(anyString(), anyString(), anyString());
    }

    @Test
    void pickSituation_inlineFallbackIsDifferentPerPower() {
        when(repository.countByPowerAndLevelAndContext(anyString(), anyString(), anyString())).thenReturn(0L);
        when(repository.findCandidates(anyString(), anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of());
        when(repository.findCandidatesAnyContext(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of());

        String dominant   = service.pickSituation("DOMINANT",   "B2", USER, 7, "MODERATE");
        String submissive = service.pickSituation("SUBMISSIVE", "B2", USER, 7, "MODERATE");
        String equal      = service.pickSituation("EQUAL",      "B2", USER, 7, "MODERATE");

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
        when(repository.countByPowerAndLevelAndContext("EQUAL", "B2", "PROFESSIONAL")).thenReturn(50L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(seen, fresh));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < 10; i++) {
            String result = service.pickSituation("EQUAL", "B2", USER, 7, "MODERATE");
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
        when(repository.countByPowerAndLevelAndContext("EQUAL", "B2", "PROFESSIONAL")).thenReturn(50L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(onlyOption));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        String result = service.pickSituation("EQUAL", "B2", USER, 7, "MODERATE");
        assertEquals("Only situation available.", result);
    }

    // ── Use count incremented ─────────────────────────────────────────────

    @Test
    void pickSituation_incrementsUseCountOnSelectedEntry() {
        SituationEntry entry = entry("EQUAL", "B2", "Handle a peer conflict.");
        entry.setUseCount(3);

        when(repository.countByPowerAndLevelAndContext("EQUAL", "B2", "PROFESSIONAL")).thenReturn(10L);
        when(repository.findCandidates(eq("EQUAL"), eq("B2"), eq("PROFESSIONAL"), any(Pageable.class)))
            .thenReturn(List.of(entry));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("EQUAL", "B2", USER, 7, "MODERATE");

        assertEquals(4, entry.getUseCount());
    }

    // ── pickRequiredWord & pickEmotionalContext ───────────────────────────

    @Test
    void pickRequiredWord_neverReturnsBlank() {
        for (int i = 0; i < 20; i++)
            assertFalse(service.pickRequiredWord().isBlank());
    }

    @Test
    void pickEmotionalContext_neverReturnsBlank() {
        for (int i = 0; i < 20; i++)
            assertFalse(service.pickEmotionalContext().isBlank());
    }

    // ── BACKGROUND pre-load sweep ────────────────────────────────────────

    @Test
    void backgroundReplenishSweep_noOpWhenStrategyIsOnDemand() {
        when(loadingService.shouldPreLoadInBackground()).thenReturn(false);

        service.backgroundReplenishSweep();

        verifyNoInteractions(repository, router);
    }

    @Test
    void backgroundReplenishSweep_replenishesOnlyLowStockBuckets() {
        when(loadingService.shouldPreLoadInBackground()).thenReturn(true);
        // Every bucket "well stocked" except DOMINANT/B2/CASUAL
        when(repository.countByPowerAndLevelAndContext(anyString(), anyString(), anyString()))
            .thenReturn(999L);
        when(repository.countByPowerAndLevelAndContext("DOMINANT", "B2", "CASUAL"))
            .thenReturn(5L);

        service.backgroundReplenishSweep();

        verify(router, times(1)).replenishAsync("DOMINANT", "B2", "CASUAL");
        verify(router, never()).replenishAsync(eq("EQUAL"), anyString(), anyString());
    }

    @Test
    void backgroundReplenishSweep_skipsBucketWhenExecutorQueueIsFull() {
        when(loadingService.shouldPreLoadInBackground()).thenReturn(true);
        when(repository.countByPowerAndLevelAndContext(anyString(), anyString(), anyString()))
            .thenReturn(0L);
        doThrow(new java.util.concurrent.RejectedExecutionException("queue full"))
            .when(router).replenishAsync(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> service.backgroundReplenishSweep());
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private SituationEntry entry(String power, String level, String text) {
        SituationEntry e = new SituationEntry();
        e.setPower(power);
        e.setLevel(level);
        e.setText(text);
        e.setSource("SEED");
        e.setContext("PROFESSIONAL");
        e.setUseCount(0);
        return e;
    }
}
