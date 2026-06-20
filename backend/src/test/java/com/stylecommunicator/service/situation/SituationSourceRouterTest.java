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

/**
 * Tests SituationSourceRouter: LLM fetch + persistence.
 *
 * AdviceSlip and Wikipedia were removed — LLM is now the sole source.
 * Repository and LlmSituationSource are mocked — no DB, no network.
 */
@ExtendWith(MockitoExtension.class)
class SituationSourceRouterTest {

    @Mock private SituationRepository repository;
    @Mock private LlmSituationSource  llmSource;

    private SituationSourceRouter router;

    @BeforeEach
    void setUp() {
        router = new SituationSourceRouter(repository, llmSource);
    }

    // ── Happy path: LLM fills the batch ───────────────────────────────────

    @Test
    void replenishAsync_fetchesFromLlmAndPersists() {
        List<String> batch = nSituations(SituationSourceRouter.BATCH_SIZE);
        when(llmSource.fetch(eq("DOMINANT"), eq("B2"), eq(SituationSourceRouter.BATCH_SIZE)))
            .thenReturn(batch);

        router.replenishAsync("DOMINANT", "B2");

        verify(llmSource, times(1)).fetch("DOMINANT", "B2", SituationSourceRouter.BATCH_SIZE);
        verify(repository, times(1)).saveAll(anyList());
    }

    // ── LLM returns empty → nothing persisted ─────────────────────────────

    @Test
    void replenishAsync_persistsNothingWhenLlmReturnsEmpty() {
        when(llmSource.fetch(anyString(), anyString(), anyInt())).thenReturn(Collections.emptyList());

        router.replenishAsync("DOMINANT", "B2");

        verifyNoInteractions(repository);
    }

    // ── Persistence: entries are saved with correct metadata ──────────────

    @Test
    @SuppressWarnings("unchecked")
    void replenishAsync_persistsSituationsWithCorrectPowerLevelAndSource() {
        List<String> batch = nSituations(3);
        when(llmSource.fetch(eq("DOMINANT"), eq("B2"), anyInt())).thenReturn(batch);

        router.replenishAsync("DOMINANT", "B2");

        ArgumentCaptor<List<SituationEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<SituationEntry> saved = captor.getValue();
        assertEquals(3, saved.size());
        saved.forEach(e -> {
            assertEquals("DOMINANT", e.getPower());
            assertEquals("B2", e.getLevel());
            assertEquals("LLM", e.getSource());
            assertNotNull(e.getText());
        });
    }

    // ── Correct batch size requested from LLM ─────────────────────────────

    @Test
    void replenishAsync_requestsFullBatchSizeFromLlm() {
        when(llmSource.fetch(anyString(), anyString(), anyInt()))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("SUBMISSIVE", "A2");

        verify(llmSource).fetch("SUBMISSIVE", "A2", SituationSourceRouter.BATCH_SIZE);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private List<String> nSituations(int n) {
        return java.util.stream.IntStream.range(0, n)
            .mapToObj(i -> "Situation " + i)
            .toList();
    }
}
