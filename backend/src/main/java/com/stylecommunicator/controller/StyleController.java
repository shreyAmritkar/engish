package com.stylecommunicator.controller;

import com.stylecommunicator.domain.CommunityCardStatus;
import com.stylecommunicator.domain.StyleSource;
import com.stylecommunicator.dto.*;
import com.stylecommunicator.entity.CommunityStyleCard;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.repository.CommunityStyleCardRepository;
import com.stylecommunicator.repository.StyleProfileRepository;
import com.stylecommunicator.service.StyleEngineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/styles")
public class StyleController {

    private final StyleProfileRepository styleProfileRepository;
    private final StyleEngineService styleEngineService;
    private final CommunityStyleCardRepository communityStyleCardRepository;

    public StyleController(
            StyleProfileRepository styleProfileRepository,
            StyleEngineService styleEngineService,
            CommunityStyleCardRepository communityStyleCardRepository) {
        this.styleProfileRepository = styleProfileRepository;
        this.styleEngineService = styleEngineService;
        this.communityStyleCardRepository = communityStyleCardRepository;
    }

    @GetMapping("/library")
    public List<StyleProfileDto> library(@RequestParam(defaultValue = "COMMUNITY,PRESET") String source) {
        List<StyleSource> sources = Arrays.stream(source.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .map(StyleSource::valueOf)
                .toList();
        return styleProfileRepository.findBySourceIn(sources).stream()
                .map(StyleProfileDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public StyleProfileDto getById(@PathVariable UUID id) {
        return styleProfileRepository.findById(id)
                .map(StyleProfileDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/from-description")
    public StyleProfileDto fromDescription(@Valid @RequestBody CreateStyleRequest request) {
        StyleProfile profile = styleEngineService.extractFromDescription(
                request.name(),
                request.description(),
                request.userId(),
                StyleSource.USER_DESCRIBED
        );
        return StyleProfileDto.from(profile);
    }

    @PostMapping("/community/submit")
    public CommunityCardResponse communitySubmit(@Valid @RequestBody CommunitySubmitRequest request) {
        StyleProfile profile = styleEngineService.extractFromScript(
                request.characterName(),
                request.excerpts(),
                request.userId()
        );
        CommunityStyleCard card = new CommunityStyleCard();
        card.setSubmittedBy(request.userId());
        card.setCharacterName(request.characterName());
        card.setExcerpts(request.excerpts());
        card.setAiExtractedProfileId(profile.getId());
        card.setStatus(CommunityCardStatus.PENDING);
        communityStyleCardRepository.save(card);
        return new CommunityCardResponse(card.getId(), card.getStatus().name(), StyleProfileDto.from(profile));
    }

    @PostMapping("/community/{cardId}/vote")
    public CommunityCardResponse vote(@PathVariable UUID cardId) {
        CommunityStyleCard card = communityStyleCardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        card.setVotes(card.getVotes() + 1);
        communityStyleCardRepository.save(card);
        if (card.getAiExtractedProfileId() != null) {
            styleProfileRepository.findById(card.getAiExtractedProfileId()).ifPresent(profile -> {
                profile.setCommunityVotes(profile.getCommunityVotes() + 1);
                styleProfileRepository.save(profile);
            });
        }
        return new CommunityCardResponse(card.getId(), card.getStatus().name(), null);
    }

    public record CommunityCardResponse(UUID cardId, String status, StyleProfileDto profile) {}
}
