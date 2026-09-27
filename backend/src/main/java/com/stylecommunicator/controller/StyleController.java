package com.stylecommunicator.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Styles", description = "Style library and style-from-description extraction.")
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
//     frontend/src/app/library/page.tsx
//         │  on component mount:
//         ▼
// useEffect(() => { api.getStyles().then(setStyles)... }, [])
//         │
//         ▼
// frontend/src/lib/api.ts
//    getStyles: (source = "PRESET,USER_DESCRIBED") =>
//      request<StyleProfile[]>(`/api/styles/library?source=${source}`)
//         │  fetch() with credentials: "include" (JWT cookie attached automatically)
//         ▼
// StyleController.library(source)  →  styleProfileRepository.findBySourceIn(...)
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
    // not un use
    @GetMapping("/{id}")
    public StyleProfileDto getById(@PathVariable UUID id) {
        return styleProfileRepository.findById(id)
                .map(StyleProfileDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

//     frontend/src/app/library/page.tsx
//         │  user fills in createName + createDesc, submits the "create style" form
//         ▼
// handleCreateStyle(e) → api.createStyleFromDescription(createName, createDesc)
//         │
//         ▼
// frontend/src/lib/api.ts
//    createStyleFromDescription: (name, description) =>
//      request<StyleProfile>("/api/styles/from-description", {
//        method: "POST",
//        body: JSON.stringify({ name, description }),
//      })
//         │
//         ▼
// StyleController.fromDescription(body, request)  →  styleEngineService.extractFromDescription(...)
//         │
//         ▼
// response (new or deduped StyleProfile) comes back
//         │
//         ▼
// setStyles(prev => [profile, ...prev])  — new style prepended to the visible list immediately,
//                                           no page refetch needed
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
