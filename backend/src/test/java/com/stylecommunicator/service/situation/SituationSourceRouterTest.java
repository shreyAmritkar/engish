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
    @Mock private NewsSource           newsSource;
    @Mock private LlmSituationSource   llmSource;

    private SituationSourceRouter router;

    @BeforeEach
    void setUp() {
        router = new SituationSourceRouter(
            repository, adviceSlipSource, wikipediaSource, newsSource, llmSource
        );
    }

    // ── Priority: AdviceSlip fills the batch alone ────────────────────────

    @Test
    void replenishAsync_usesAdviceSlipFirstAndSkipsOtherSources() {
        List<String> fullBatch = nSituations(SituationSourceRouter.BATCH_SIZE);
        when(adviceSlipSource.fetch(eq("DOMINANT"), eq("B2"), anyInt())).thenReturn(fullBatch);

        router.replenishAsync("DOMINANT", "B2");

        // AdviceSlip filled everything — Wikipedia/News/LLM must NOT be called
        verifyNoInteractions(wikipediaSource);
        verifyNoInteractions(newsSource);
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
        verifyNoInteractions(newsSource);
        verifyNoInteractions(llmSource);
    }

    // ── Complete failure: AdviceSlip fails → Wikipedia called ─────────────

    @Test
    void replenishAsync_callsWikipediaWhenAdviceSlipReturnsEmpty() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt()))
                .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("DOMINANT", "B2");

        verify(adviceSlipSource, times(1)).fetch(anyString(), anyString(), anyInt());
        verify(wikipediaSource,  times(1)).fetch(anyString(), anyString(), anyInt());
        verifyNoInteractions(newsSource);
        verifyNoInteractions(llmSource);
    }

    // ── Complete failure: AdviceSlip + Wikipedia fail → News called ───────

    @Test
    void replenishAsync_callsNewsWhenAdviceSlipAndWikipediaFail() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(newsSource.fetch(anyString(), anyString(), anyInt()))
                .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("EQUAL", "B1");

        verify(adviceSlipSource, times(1)).fetch(anyString(), anyString(), anyInt());
        verify(wikipediaSource,  times(1)).fetch(anyString(), anyString(), anyInt());
        verify(newsSource,       times(1)).fetch(anyString(), anyString(), anyInt());
        verifyNoInteractions(llmSource);
    }

    // ── Complete failure: AdviceSlip + Wikipedia + News fail → LLM called ─

    @Test
    void replenishAsync_callsLlmWhenAllThreeExternalSourcesFail() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(newsSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(llmSource.fetch(anyString(), anyString(), anyInt()))
                .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("SUBMISSIVE", "B2");

        verify(adviceSlipSource, times(1)).fetch(anyString(), anyString(), anyInt());
        verify(wikipediaSource,  times(1)).fetch(anyString(), anyString(), anyInt());
        verify(newsSource,       times(1)).fetch(anyString(), anyString(), anyInt());
        verify(llmSource,        times(1)).fetch(anyString(), anyString(), anyInt());
    }

    // ── Each step only asks for what's still missing ──────────────────────

    @Test
    void replenishAsync_eachSourceAskedOnlyForRemainingCount() {
        int adviceContribution = 4;
        int wikiContribution   = 5;
        // Total = 9, BATCH_SIZE = 12, so News should be asked for 3

        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt()))
                .thenReturn(nSituations(adviceContribution));
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt()))
                .thenReturn(nSituations(wikiContribution));
        when(newsSource.fetch(anyString(), anyString(), anyInt()))
                .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE - adviceContribution - wikiContribution));

        router.replenishAsync("EQUAL", "B2");

        // News should be asked for exactly 3 (12 - 4 - 5)
        int expected = SituationSourceRouter.BATCH_SIZE - adviceContribution - wikiContribution;
        verify(newsSource).fetch(anyString(), anyString(), eq(expected));
        verifyNoInteractions(llmSource);
    }

    // ── LLM is last resort ────────────────────────────────────────────────

    @Test
    void replenishAsync_onlyCallsLlmWhenAllOtherSourcesExhausted() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(newsSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(llmSource.fetch(anyString(), anyString(), anyInt())).thenReturn(nSituations(5));

        router.replenishAsync("SUBMISSIVE", "B2");

        verify(adviceSlipSource, times(1)).fetch(anyString(), anyString(), anyInt());
        verify(wikipediaSource,  times(1)).fetch(anyString(), anyString(), anyInt());
        verify(newsSource,       times(1)).fetch(anyString(), anyString(), anyInt());
        verify(llmSource,        times(1)).fetch(anyString(), anyString(), anyInt());
    }

    // ── Persistence ───────────────────────────────────────────────────────

    @Test
    void replenishAsync_persistsAllCollectedSituations() {
        int count = 8;
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(nSituations(count));
        when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        router.replenishAsync("DOMINANT", "B2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SituationEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<SituationEntry> saved = captor.getValue();
        assertEquals(count, saved.size(), "All generated situations must be persisted");

        // Every saved entry must have correct power/level
        saved.forEach(e -> {
            assertEquals("DOMINANT", e.getPower());
            assertEquals("B2",       e.getLevel());
            assertFalse(e.getText().isBlank(), "Blank text should not be persisted");
        });
    }

    @Test
    void replenishAsync_doesNotPersistWhenAllSourcesReturnEmpty() {
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(wikipediaSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(newsSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(llmSource.fetch(anyString(), anyString(), anyInt())).thenReturn(List.of());

        router.replenishAsync("EQUAL", "A1");

        verify(repository, never()).saveAll(anyList());
    }

    // ── Blank-text filter ─────────────────────────────────────────────────

    @Test
    void replenishAsync_filtersOutBlankSituationsBeforePersisting() {
        List<String> withBlanks = List.of("Valid situation text.", "", "  ", "Another valid one.");
        when(adviceSlipSource.fetch(anyString(), anyString(), anyInt())).thenReturn(withBlanks);
        when(repository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        router.replenishAsync("EQUAL", "B2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SituationEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        captor.getValue().forEach(e ->
            assertFalse(e.getText().isBlank(), "Blank entry persisted: '" + e.getText() + "'"));
    }

    // ── Source is called with the right power/level ───────────────────────

    @Test
    void replenishAsync_forwardsCorrectPowerAndLevel() {
        when(adviceSlipSource.fetch(eq("SUBMISSIVE"), eq("A1"), anyInt()))
            .thenReturn(nSituations(SituationSourceRouter.BATCH_SIZE));

        router.replenishAsync("SUBMISSIVE", "A1");

        verify(adviceSlipSource).fetch("SUBMISSIVE", "A1", SituationSourceRouter.BATCH_SIZE);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private List<String> nSituations(int n) {
        return java.util.stream.IntStream.range(0, n)
            .mapToObj(i -> "Test situation text number " + i + ".")
            .toList();
    }
}