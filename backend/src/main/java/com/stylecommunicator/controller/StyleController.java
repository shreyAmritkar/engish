package com.stylecommunicator.controller;

import com.stylecommunicator.domain.StyleSource;
import com.stylecommunicator.dto.CreateStyleRequest;
import com.stylecommunicator.dto.StyleProfileDto;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.repository.StyleProfileRepository;
import com.stylecommunicator.service.StyleEngineService;
import jakarta.servlet.http.HttpServletRequest;
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

    public StyleController(StyleProfileRepository styleProfileRepository,
                           StyleEngineService styleEngineService) {
        this.styleProfileRepository = styleProfileRepository;
        this.styleEngineService = styleEngineService;
    }

    @GetMapping("/library")
    public List<StyleProfileDto> library(@RequestParam(defaultValue = "PRESET,USER_DESCRIBED") String source) {
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
    public StyleProfileDto fromDescription(@Valid @RequestBody CreateStyleRequest body,
                                           HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("authenticatedUserId");
        StyleProfile profile = styleEngineService.extractFromDescription(
                body.name(),
                body.description(),
                userId,
                StyleSource.USER_DESCRIBED
        );
        return StyleProfileDto.from(profile);
    }
}
