package ru.practicum.ewm.event.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.service.EventService;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class PublicEventController {

    private final EventService service;

    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable Long id) {
        log.info("GET /events/{}", id);
        return service.getPublishedEvent(id);
    }
}