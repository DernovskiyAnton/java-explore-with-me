package ru.practicum.ewm.comment.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.practicum.ewm.comment.model.Comment;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Получить все комментарии к событию с пагинацией
     */
    List<Comment> findByEventIdOrderByCreatedDesc(Long eventId, Pageable pageable);

    /**
     * Получить комментарий по ID и автору (для проверки прав)
     */
    Optional<Comment> findByIdAndAuthorId(Long id, Long authorId);

    /**
     * Подсчет комментариев к событию
     */
    Long countByEventId(Long eventId);
}