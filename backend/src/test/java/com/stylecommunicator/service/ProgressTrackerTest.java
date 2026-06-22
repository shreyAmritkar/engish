package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressTrackerTest {

    @Mock private UserProgressRepository userProgressRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;

    private ProgressTracker tracker;

    private final UUID USER = UUID.randomUUID();

    private static final Map<String, Integer> HIGH_SCORES = Map.of(
        "confidence", 90, "tone", 88, "persuasion", 92,
        "emotional_control", 85, "professionalism", 91, "style_match", 87
    );

    private static final Map<String, Integer> LOW_SCORES = Map.of(
        "confidence", 30, "tone", 25, "persuasion", 20,
        "emotional_control", 35, "professionalism", 28, "style_match", 22
    );

    private static final Map<String, Integer> MID_SCORES = Map.of(
        "confidence", 60, "tone", 62, "persuasion", 58,
        "emotional_control", 65, "professionalism", 61, "style_match", 59
    );

    @BeforeEach
    void setUp() {
        tracker = new ProgressTracker(userProgressRepository, practiceSessionRepository);
        lenient().when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(any()))
            .thenReturn(List.of());
        lenient().when(userProgressRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── New user gets a fresh UserProgress ───────────────────────────────

    @Test
    void update_newUser_createsProgressRecord() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());

        UserProgress result = tracker.update(USER, MID_SCORES);

        assertNotNull(result);
        assertEquals(USER, result.getUserId());
        assertEquals(1, result.getTotalSessions());
    }

    // ── EMA averages blend old and new ────────────────────────────────────

    @Test
    void update_existingUser_blendsPreviousAveragesWithEma() {
        UserProgress existing = progressWithAverages(Map.of(
            "confidence", 50.0, "tone", 50.0, "persuasion", 50.0,
            "emotional_control", 50.0, "professionalism", 50.0, "style_match", 50.0
        ));
        when(userProgressRepository.findById(USER)).thenReturn(Optional.of(existing));

        tracker.update(USER, HIGH_SCORES);

        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(userProgressRepository).save(captor.capture());

        double confidenceAvg = captor.getValue().getAverageScores().get("confidence");
        // EMA: 50 * 0.7 + 90 * 0.3 = 35 + 27 = 62
        assertEquals(62.0, confidenceAvg, 0.1);
    }

    // ── totalSessions increments ──────────────────────────────────────────

    @Test
    void update_incrementsTotalSessions() {
        UserProgress existing = progressWithAverages(new HashMap<>());
        existing.setTotalSessions(4);
        when(userProgressRepository.findById(USER)).thenReturn(Optional.of(existing));

        UserProgress result = tracker.update(USER, MID_SCORES);

        assertEquals(5, result.getTotalSessions());
    }

    // ── Weak areas identified ─────────────────────────────────────────────

    @Test
    void update_lowScores_populatesWeakAreas() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());

        UserProgress result = tracker.update(USER, LOW_SCORES);

        assertFalse(result.getWeakAreas().isEmpty(),
            "Should identify weak areas when scores are low");
        assertTrue(result.getWeakAreas().stream()
            .allMatch(area -> LOW_SCORES.containsKey(area)),
            "Weak areas should match score dimensions");
    }

    // ── Strong areas identified ───────────────────────────────────────────

    @Test
    void update_highScores_populatesStrongAreas() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());

        UserProgress result = tracker.update(USER, HIGH_SCORES);

        assertFalse(result.getStrongAreas().isEmpty(),
            "Should identify strong areas when scores are high");
    }

    @Test
    void update_midScores_noWeakOrStrongAreas() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());

        UserProgress result = tracker.update(USER, MID_SCORES);

        assertTrue(result.getWeakAreas().isEmpty(),
            "Mid scores (60+) should not be weak areas");
    }

    // ── Level calculation ─────────────────────────────────────────────────

    @Test
    void update_noSessions_returnsLevel1() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());
        when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(List.of());

        UserProgress result = tracker.update(USER, MID_SCORES);

        assertEquals(1, result.getCurrentLevel());
    }

    @Test
    void update_tenHighScoringSession_returnsHighLevel() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());
        when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(nSessions(10, HIGH_SCORES));

        UserProgress result = tracker.update(USER, HIGH_SCORES);

        assertTrue(result.getCurrentLevel() >= 4,
            "Ten high-scoring sessions should yield level 4 or 5, got: " + result.getCurrentLevel());
    }

    @Test
    void update_tenLowScoringSession_returnsLevel1() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());
        when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(nSessions(10, LOW_SCORES));

        UserProgress result = tracker.update(USER, LOW_SCORES);

        assertEquals(1, result.getCurrentLevel(),
            "Ten low-scoring sessions should yield level 1");
    }

    // ── Habit detection runs every 5 sessions ────────────────────────────

    @Test
    void update_fifthSession_runsHabitDetection() {
        UserProgress existing = progressWithAverages(new HashMap<>());
        existing.setTotalSessions(4); // will become 5
        when(userProgressRepository.findById(USER)).thenReturn(Optional.of(existing));
        when(practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(USER))
            .thenReturn(nSessions(10, MID_SCORES));

        tracker.update(USER, MID_SCORES);

        // habitFlags should be set (may be empty list but not null)
        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(userProgressRepository).save(captor.capture());
        assertNotNull(captor.getValue().getHabitFlags());
    }

    @Test
    void update_nonFifthSession_skipsHabitDetection() {
        UserProgress existing = progressWithAverages(new HashMap<>());
        existing.setTotalSessions(3); // will become 4 — not a multiple of 5
        existing.setHabitFlags(List.of("existing flag"));
        when(userProgressRepository.findById(USER)).thenReturn(Optional.of(existing));

        tracker.update(USER, MID_SCORES);

        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(userProgressRepository).save(captor.capture());
        // habitFlags should NOT be overwritten on non-5th session
        assertEquals(List.of("existing flag"), captor.getValue().getHabitFlags());
    }

    // ── Missing score dimensions default to 50 ────────────────────────────

    @Test
    void update_missingDimensions_defaultsToFifty() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());

        // Only provide 2 of the 6 dimensions
        UserProgress result = tracker.update(USER, Map.of("confidence", 80, "tone", 70));

        Map<String, Double> averages = result.getAverageScores();
        assertNotNull(averages.get("persuasion"),
            "Missing dimension should still be present with default value");
    }

    // ── Saves to repository ───────────────────────────────────────────────

    @Test
    void update_alwaysSavesToRepository() {
        when(userProgressRepository.findById(USER)).thenReturn(Optional.empty());

        tracker.update(USER, MID_SCORES);

        verify(userProgressRepository, times(1)).save(any(UserProgress.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UserProgress progressWithAverages(Map<String, Double> averages) {
        UserProgress p = new UserProgress();
        p.setUserId(USER);
        p.setAverageScores(new HashMap<>(averages));
        p.setWeakAreas(new ArrayList<>());
        p.setStrongAreas(new ArrayList<>());
        p.setHabitFlags(new ArrayList<>());
        p.setTotalSessions(0);
        p.setCurrentLevel(1);
        return p;
    }

    private List<PracticeSession> nSessions(int n, Map<String, Integer> scores) {
        List<PracticeSession> sessions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PracticeSession s = new PracticeSession();
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("scores", new HashMap<>(scores));
            s.setFeedback(feedback);
            sessions.add(s);
        }
        return sessions;
    }
}
