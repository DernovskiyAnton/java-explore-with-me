package ru.practicum.ewm.comment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.service.CommentService;

import java.util.List;

@RestController
@RequestMapping("/events/{eventId}/comments")
@RequiredArgsConstructor
@Slf4j
public class PublicCommentController {

    private final CommentService commentService;

    /**
     * GET /events/{eventId}/comments
     * Получить комментарии к событию (публичный доступ)
     */
    @GetMapping
    public List<CommentDto> getComments(@PathVariable Long eventId,
                                        @RequestParam(defaultValue = "0") Integer from,
                                        @RequestParam(defaultValue = "10") Integer size) {
        log.info("GET /events/{}/comments: from={}, size={}", eventId, from, size);
        return commentService.getCommentsByEvent(eventId, from, size);
    }
}