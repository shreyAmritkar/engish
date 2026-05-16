package com.stylecommunicator.repository;

import com.stylecommunicator.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    List<PracticeSession> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    List<PracticeSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
