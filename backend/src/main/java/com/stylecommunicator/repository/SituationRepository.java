package com.stylecommunicator.repository;

import com.stylecommunicator.entity.SituationEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SituationRepository extends JpaRepository<SituationEntry, UUID> {

    /**
     * Fetch candidates ordered by use_count ascending — least-used first.
     * Pageable lets callers cap how many rows they want (e.g. top 20).
     */
    @Query("SELECT s FROM SituationEntry s WHERE s.power = :power AND s.level = :level " +
           "ORDER BY s.useCount ASC, s.createdAt DESC")
    List<SituationEntry> findCandidates(@Param("power") String power,
                                        @Param("level") String level,
                                        Pageable pageable);

    long countByPowerAndLevel(String power, String level);
}
