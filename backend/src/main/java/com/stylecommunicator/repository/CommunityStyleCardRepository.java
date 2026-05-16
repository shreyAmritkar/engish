package com.stylecommunicator.repository;

import com.stylecommunicator.domain.CommunityCardStatus;
import com.stylecommunicator.entity.CommunityStyleCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommunityStyleCardRepository extends JpaRepository<CommunityStyleCard, UUID> {

    List<CommunityStyleCard> findByStatusOrderByVotesDesc(CommunityCardStatus status);
}
