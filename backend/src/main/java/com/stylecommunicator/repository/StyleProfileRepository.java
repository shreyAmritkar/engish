package com.stylecommunicator.repository;

import com.stylecommunicator.domain.StyleSource;
import com.stylecommunicator.entity.StyleProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StyleProfileRepository extends JpaRepository<StyleProfile, UUID> {

    @Query("SELECT s FROM StyleProfile s WHERE s.source IN :sources ORDER BY s.communityVotes DESC, s.name ASC")
    List<StyleProfile> findBySourceIn(@Param("sources") List<StyleSource> sources);

    List<StyleProfile> findAllByOrderByCreatedAtDesc();
}
