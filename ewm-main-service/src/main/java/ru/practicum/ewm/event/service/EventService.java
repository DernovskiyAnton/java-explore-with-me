package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.LocationRepository;
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
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final EventMapper eventMapper;

    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        log.info("Adding new event by user: {}", userId);

        // Проверка что дата события не раньше чем через 2 часа
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException("Event date must be at least 2 hours from now");
        }

        User user = getUserById(userId);
        Category category = getCategoryById(dto.getCategory());
        Location location = locationRepository.save(eventMapper.toLocationEntity(dto.getLocation()));

        Event event = eventMapper.toEntity(dto, category, user, location);
        Event saved = eventRepository.save(event);

        log.info("Event added with id: {}", saved.getId());
        return eventMapper.toFullDto(saved);
    }

    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        log.info("Getting events for user: {}, from: {}, size: {}", userId, from, size);

        getUserById(userId); // Проверка что пользователь существует

        Pageable pageable = PageRequest.of(from / size, size);

        return eventRepository.findByInitiatorId(userId, pageable).stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        log.info("Getting event: {} for user: {}", eventId, userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        return eventMapper.toFullDto(event);
    }

    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        log.info("Updating event: {} by user: {}", eventId, userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        // Можно изменять только отмененные события или события в состоянии ожидания модерации
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        // Проверка даты события
        if (dto.getEventDate() != null && dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ConflictException("Event date must be at least 2 hours from now");
        }

        updateEventFields(event, dto);

        // Обработка stateAction
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case SEND_TO_REVIEW:
                    event.setState(EventState.PENDING);
                    break;
                case CANCEL_REVIEW:
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        Event updated = eventRepository.save(event);
        log.info("Event updated: {}", updated.getId());

        return eventMapper.toFullDto(updated);
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        log.info("Admin updating event: {}", eventId);

        Event event = getEventById(eventId);

        // Проверка даты публикации (должна быть не раньше чем за час до начала)
        if (dto.getEventDate() != null &&
                event.getPublishedOn() != null &&
                dto.getEventDate().isBefore(event.getPublishedOn().plusHours(1))) {
            throw new ConflictException("Event date must be at least 1 hour after publication");
        }

        updateEventFields(event, dto);

        // Обработка stateAction
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case PUBLISH_EVENT:
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case REJECT_EVENT:
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Cannot reject the event because it's already published");
                    }
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        Event updated = eventRepository.save(event);
        log.info("Event updated by admin: {}", updated.getId());

        return eventMapper.toFullDto(updated);
    }

    public EventFullDto getPublishedEvent(Long eventId) {
        log.info("Getting published event: {}", eventId);

        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        // TODO: Увеличить счетчик просмотров
        // TODO: Отправить запрос в stats-service

        return eventMapper.toFullDto(event);
    }

    private void updateEventFields(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            Category category = getCategoryById(dto.getCategory());
            event.setCategory(category);
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            Location location = locationRepository.save(eventMapper.toLocationEntity(dto.getLocation()));
            event.setLocation(location);
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getCategory() != null) {
            Category category = getCategoryById(dto.getCategory());
            event.setCategory(category);
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getLocation() != null) {
            Location location = locationRepository.save(eventMapper.toLocationEntity(dto.getLocation()));
            event.setLocation(location);
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Category getCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + categoryId + " was not found"));
    }

    private Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }
}