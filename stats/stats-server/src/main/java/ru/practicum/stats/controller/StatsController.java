package ru.practicum.stats.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.stats.service.StatsService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class StatsController {

    private final StatsService service;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointHitDto saveHit(@Valid @RequestBody EndpointHitDto dto) {
        log.info("POST /hit: {}", dto);
        return service.saveHit(dto);
    }

    @GetMapping("/stats")
    public List<ViewStatsDto> getStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) List<String> uris,
            @RequestParam(defaultValue = "false") Boolean unique) {

        log.info("GET /stats: start={}, end={}, uris={}, unique={}", start, end, uris, unique);
        return service.getStats(start, end, uris, unique);
    }
}