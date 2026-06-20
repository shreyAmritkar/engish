package com.stylecommunicator.service.situation;

import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.SituationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SituationSourceRouterTest {

    @Mock private SituationRepository repository;
    @Mock private LlmSituationSource  llmSource;

    private SituationSourceRouter router;

    @BeforeEach
    void setUp() {
        router = new SituationSourceRouter(repository, llmSource);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    void replenishAsync_fetchesFromLlmAndPersists() {
        when(llmSource.fetch("DOMINANT", "B2", "PROFESSIONAL", SituationSourceRouter.BATCH_SIZE))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("DOMINANT", "B2", "PROFESSIONAL");

        verify(llmSource).fetch("DOMINANT", "B2", "PROFESSIONAL", SituationSourceRouter.BATCH_SIZE);
        verify(repository).saveAll(anyList());
    }

    // ── LLM empty → nothing saved ─────────────────────────────────────────

    @Test
    void replenishAsync_persistsNothingWhenLlmReturnsEmpty() {
        when(llmSource.fetch(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Collections.emptyList());

        router.replenishAsync("DOMINANT", "B2", "PROFESSIONAL");

        verifyNoInteractions(repository);
    }

    // ── Entries saved with correct metadata ──────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void replenishAsync_savesEntriesWithCorrectFields() {
        when(llmSource.fetch(eq("EQUAL"), eq("B1"), eq("CASUAL"), anyInt()))
            .thenReturn(nSituations(3));

        router.replenishAsync("EQUAL", "B1", "CASUAL");

        ArgumentCaptor<List<SituationEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<SituationEntry> saved = captor.getValue();
        assertEquals(3, saved.size());
        saved.forEach(e -> {
            assertEquals("EQUAL",   e.getPower());
            assertEquals("B1",      e.getLevel());
            assertEquals("CASUAL",  e.getContext());
            assertEquals("LLM",     e.getSource());
            assertNotNull(e.getText());
            assertFalse(e.getText().isBlank());
        });
    }

    // ── Context is passed to LLM ──────────────────────────────────────────

    @Test
    void replenishAsync_passesDramaticContextToLlm() {
        when(llmSource.fetch("SUBMISSIVE", "A2", "DRAMATIC", SituationSourceRouter.BATCH_SIZE))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("SUBMISSIVE", "A2", "DRAMATIC");

        verify(llmSource).fetch("SUBMISSIVE", "A2", "DRAMATIC", SituationSourceRouter.BATCH_SIZE);
    }

    // ── Backward-compat two-arg overload defaults to PROFESSIONAL ─────────

    @Test
    @SuppressWarnings("unchecked")
    void replenishAsync_twoArgOverloadDefaultsToProfessional() {
        when(llmSource.fetch(eq("DOMINANT"), eq("B2"), eq("PROFESSIONAL"), anyInt()))
            .thenReturn(nSituations(2));

        router.replenishAsync("DOMINANT", "B2");

        ArgumentCaptor<List<SituationEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        captor.getValue().forEach(e ->
            assertEquals("PROFESSIONAL", e.getContext()));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private List<String> nSituations(int n) {
        return java.util.stream.IntStream.range(0, n)
            .mapToObj(i -> "Situation " + i)
            .toList();
    }
}
