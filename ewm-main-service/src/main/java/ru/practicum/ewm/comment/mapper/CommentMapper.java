package ru.practicum.ewm.comment.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.user.model.User;

import java.time.LocalDateTime;

@Component
public class CommentMapper {

    /**
     * Конвертация NewCommentDto в Entity
     */
    public Comment toEntity(NewCommentDto dto, Event event, User author) {
        Comment comment = new Comment();
        comment.setText(dto.getText());
        comment.setEvent(event);
        comment.setAuthor(author);
        comment.setCreated(LocalDateTime.now());
        comment.setEdited(null);
        return comment;
    }

    /**
     * Конвертация Entity в DTO
     */
    public CommentDto toDto(Comment comment) {
        CommentDto dto = new CommentDto();
        dto.setId(comment.getId());
        dto.setText(comment.getText());
        dto.setEventId(comment.getEvent().getId());
        dto.setAuthorName(comment.getAuthor().getName());
        dto.setAuthorId(comment.getAuthor().getId());
        dto.setCreated(comment.getCreated());
        dto.setEdited(comment.getEdited());
        return dto;
    }
}