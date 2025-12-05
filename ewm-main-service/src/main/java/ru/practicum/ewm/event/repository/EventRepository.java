package ru.practicum.ewm.event.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByInitiatorId(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiatorId(Long eventId, Long initiatorId);

        @Query("SELECT e FROM Event e WHERE e.id = :id AND e.state = :state")
        Optional<Event> findByIdAndState(@Param("id") Long id,
                                         @Param("state") EventState state);

    boolean existsByCategoryId(Long categoryId);
}