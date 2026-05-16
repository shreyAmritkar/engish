package com.stylecommunicator.repository;

import com.stylecommunicator.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
}
