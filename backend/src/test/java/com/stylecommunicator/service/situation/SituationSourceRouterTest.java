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
 * Tests SituationSourceRouter priority order, fallback chaining, and persistence.
 *
 * All sources and the repository are mocked — no DB, no network.
 */
@ExtendWith(MockitoExtension.class)
class SituationSourceRouterTest {

    @Mock private SituationRepository repository;
    @Mock private AdviceSlipSource     adviceSlipSource;
    @Mock private WikipediaSource      wikipediaSource;
    @Mock private LlmSituationSource   llmSource;

    private SituationSourceRouter router;

    @BeforeEach
    void setUp() {
        router = new SituationSourceRouter(
            repository, adviceSlipSource, wikipediaSource, llmSource
        );
    }

    // ── Priority: AdviceSlip fills the batch alone ────────────────────────

    @Test
    void replenishAsync_usesAdviceSlipFirstAndSkipsOtherSources() {
        List<String> fullBatch = nSituations(SituationSourceRouter.BATCH_SIZE);
        when(adviceSlipSource.fetch(eq("DOMINANT"), eq("B2"), anyInt())).thenReturn(fullBatch);

        router.replenishAsync("DOMINANT", "B2");

        // AdviceSlip filled everything — Wikipedia/LLM must NOT be called
        verifyNoInteractions(wikipediaSource);
        verifyNoInteractions(llmSource);
    }

    // ── Fallback: AdviceSlip partial → Wikipedia fills the rest ──────────

    @Test
    void replenishAsync_fallsBackToWikipediaWhenAdviceSlipPartial() {
        int partial = SituationSourceRouter.BATCH_SIZE / 2;
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt()))
            .thenReturn(nSituations(partial));
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt()))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE - partial));

        router.replenishAsync("EQUAL", "B1");

        verify(adviceSlipSource, times(1)).fetch(anyString(), anyString(), anyInt());
        verify(wikipediaSource,  times(1)).fetch(anyString(), anyString(), anyInt());
        verifyNoInteractions(llmSource);
    }

    // ── Complete failure: AdviceSlip fails → Wikipedia called ─────────────

    @Test
    void replenishAsync_callsWikipediaWhenAdviceSlipReturnsEmpty() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt()))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("SUBMISSIVE", "A2");

        verify(wikipediaSource, times(1)).fetch(anyString(), anyString(), anyInt());
        verifyNoInteractions(llmSource);
    }

    // ── Correct shortfall passed to Wikipedia ────────────────────────────

    @Test
    void replenishAsync_passesCorrectShortfallToWikipedia() {
        int adviceCount = 4;
        int expected = SituationSourceRouter.BATCH_SIZE - adviceCount;
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt()))
            .thenReturn(nSituations(adviceCount));
        when(wikipediaSource.fetch(anyString(), anyString(), eq(expected)))
            .thenReturn(nSituations(expected));

        router.replenishAsync("DOMINANT", "C1");

        verify(wikipediaSource).fetch(anyString(), anyString(), eq(expected));
    }

    // ── LLM last-resort: both free sources fail ───────────────────────────

    @Test
    void replenishAsync_usesLlmWhenAllFreeSourcesFail() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(llmSource.fetch(anyString(), anyString(), anyInt()))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("EQUAL", "B2");

        verify(llmSource, times(1)).fetch(anyString(), anyString(), anyInt());
    }

    // ── All sources exhausted → nothing persisted ─────────────────────────

    @Test
    void replenishAsync_persistsNothingWhenAllSourcesExhausted() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(llmSource.fetch(anyString(), anyString(), anyInt())).thenReturn(Collections.emptyList());

        router.replenishAsync("DOMINANT", "B2");

        verifyNoInteractions(repository);
    }

    // ── Persistence: entries are saved with correct metadata ──────────────

    @Test
    @SuppressWarnings("unchecked")
    void replenishAsync_persistsSituationsWithCorrectPowerAndLevel() {
        List<String> batch = nSituations(3);
        when(adviceSlipSource.fetch(eq("DOMINANT"), eq("B2"), anyInt())).thenReturn(batch);

        router.replenishAsync("DOMINANT", "B2");

        ArgumentCaptor<List<SituationEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<SituationEntry> saved = captor.getValue();
        assertEquals(3, saved.size());
        saved.forEach(e -> {
            assertEquals("DOMINANT", e.getPower());
            assertEquals("B2", e.getLevel());
            assertNotNull(e.getText());
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private List<String> nSituations(int n) {
        return java.util.stream.IntStream.range(0, n)
            .mapToObj(i -> "Situation " + i)
            .toList();
    }
}
