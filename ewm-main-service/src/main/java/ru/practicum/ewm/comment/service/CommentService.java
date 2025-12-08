package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.mapper.CommentMapper;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    /**
     * Добавить комментарий к событию
     */
    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto dto) {
        log.info("Adding comment to event {} by user {}", eventId, userId);

        User user = getUserById(userId);
        Event event = getEventById(eventId);

        // Проверка: можно комментировать только опубликованные события
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Cannot comment on unpublished event");
        }

        Comment comment = commentMapper.toEntity(dto, event, user);
        Comment saved = commentRepository.save(comment);

        log.info("Comment added with id: {}", saved.getId());
        return commentMapper.toDto(saved);
    }

    /**
     * Редактировать свой комментарий
     */
    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto dto) {
        log.info("Updating comment {} by user {}", commentId, userId);

        getUserById(userId); // Проверка существования пользователя

        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Comment with id=" + commentId + " was not found or you are not the author"
                ));

        comment.setText(dto.getText());
        comment.setEdited(LocalDateTime.now());

        Comment updated = commentRepository.save(comment);
        log.info("Comment {} updated", commentId);

        return commentMapper.toDto(updated);
    }

    /**
     * Удалить свой комментарий (для обычных пользователей)
     */
    @Transactional
    public void deleteCommentByUser(Long userId, Long commentId) {
        log.info("Deleting comment {} by user {}", commentId, userId);

        getUserById(userId); // Проверка существования пользователя

        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Comment with id=" + commentId + " was not found or you are not the author"
                ));

        commentRepository.delete(comment);
        log.info("Comment {} deleted by user {}", commentId, userId);
    }

    /**
     * Удалить любой комментарий (для админа)
     */
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        log.info("Admin deleting comment {}", commentId);

        Comment comment = getCommentById(commentId);
        commentRepository.delete(comment);

        log.info("Comment {} deleted by admin", commentId);
    }

    /**
     * Получить комментарии к событию (публичный доступ)
     */
    public List<CommentDto> getCommentsByEvent(Long eventId, Integer from, Integer size) {
        log.info("Getting comments for event {}: from={}, size={}", eventId, from, size);

        // Проверяем, что событие существует и опубликовано
        Event event = getEventById(eventId);
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Comment> comments = commentRepository.findByEventIdOrderByCreatedDesc(eventId, pageable);

        log.info("Found {} comments for event {}", comments.size(), eventId);

        return comments.stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));
    }
}