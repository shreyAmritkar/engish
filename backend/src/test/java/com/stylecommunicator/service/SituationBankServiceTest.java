package com.stylecommunicator.service;

import com.stylecommunicator.entity.SituationEntry;
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
 * required word / emotional context selection.
 */
@ExtendWith(MockitoExtension.class)
class SituationBankServiceTest {

    @Mock private SituationRepository   repository;
    @Mock private SituationSourceRouter router;

    private SituationBankService service;

    private final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SituationBankService(repository, router);
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
        // Stock = 3, threshold = 6 → should trigger async replenish
        when(repository.countByPowerAndLevel("EQUAL", "B1")).thenReturn(3L);
        when(repository.findCandidates(anyString(), anyString(), any(Pageable.class)))
            .thenReturn(List.of(entry("EQUAL", "B1", "Resolve peer disagreement.")));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.pickSituation("EQUAL", "B1", USER);

        verify(router, times(1)).replenishAsync("EQUAL", "B1");
    }

    @Test
    void pickSituation_doesNotTriggerReplenishWhenStockAdequate() {
        when(repository.countByPowerAndLevel("DOMINANT", "B2")).thenReturn(20L);
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
