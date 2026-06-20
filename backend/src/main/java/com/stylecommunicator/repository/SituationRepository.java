package com.stylecommunicator.repository;

import com.stylecommunicator.entity.SituationEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SituationRepository extends JpaRepository<SituationEntry, UUID> {

    /** Fetch candidates for a specific context (PROFESSIONAL/CASUAL/DRAMATIC). */
    @Query("SELECT s FROM SituationEntry s WHERE s.power = :power AND s.level = :level " +
           "AND s.context = :context ORDER BY s.useCount ASC, s.createdAt DESC")
    List<SituationEntry> findCandidates(@Param("power") String power,
                                        @Param("level") String level,
                                        @Param("context") String context,
                                        Pageable pageable);

    /** Fallback — ignores context, used when context-specific pool is empty. */
    @Query("SELECT s FROM SituationEntry s WHERE s.power = :power AND s.level = :level " +
           "ORDER BY s.useCount ASC, s.createdAt DESC")
    List<SituationEntry> findCandidatesAnyContext(@Param("power") String power,
                                                  @Param("level") String level,
                                                  Pageable pageable);

    long countByPowerAndLevelAndContext(String power, String level, String context);

    long countByPowerAndLevel(String power, String level);
}
