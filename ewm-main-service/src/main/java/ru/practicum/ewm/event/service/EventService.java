package ru.practicum.ewm.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.client.StatsClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventSort;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.LocationRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.ewm.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final StatsClient statsClient;

    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        log.info("Adding new event by user: {}", userId);

        // Проверка что дата события не раньше чем через 2 часа
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
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
        log.info("Updating event {} by user {}", eventId, userId);

        Event event = getEventByIdAndInitiator(eventId, userId);

        // Можно изменять только отмененные события или события в состоянии ожидания модерации
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot update published event");
        }

        // Обновление полей (только если они не null)
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

        // ИСПРАВЛЕНО: проверка на null перед валидацией
        if (dto.getEventDate() != null) {
            if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ValidationException("Event date must be at least 2 hours from now");
            }
            event.setEventDate(dto.getEventDate());
        }

        if (dto.getLocation() != null) {
            Location location = new Location();
            location.setLat(dto.getLocation().getLat());
            location.setLon(dto.getLocation().getLon());
            Location savedLocation = locationRepository.save(location);
            event.setLocation(savedLocation);
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

        // Обработка изменения состояния
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
        log.info("Admin updating event {}: {}", eventId, dto);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        // Обновление полей (только если они не null)
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

        // ИСПРАВЛЕНО: проверка на null перед валидацией
        if (dto.getEventDate() != null) {
            // Если публикуем - проверяем что дата публикации минимум за час до события
            if (dto.getStateAction() == UpdateEventAdminRequest.StateAction.PUBLISH_EVENT) {
                if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                    throw new ConflictException("Event date must be at least 1 hour from publication time");
                }
            }
            event.setEventDate(dto.getEventDate());
        }

        if (dto.getLocation() != null) {
            Location location = new Location();
            location.setLat(dto.getLocation().getLat());
            location.setLon(dto.getLocation().getLon());
            Location savedLocation = locationRepository.save(location);
            event.setLocation(savedLocation);
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

        // Обработка изменения состояния администратором
        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case PUBLISH_EVENT:
                    // Публиковать можно только события в состоянии ожидания
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;

                case REJECT_EVENT:
                    // Отклонить можно только неопубликованные события
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

    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, EventSort sort,
                                               Integer from, Integer size,
                                               String ip, String uri) {
        log.info("Getting public events with filters");

        // Отправка статистики
        sendStatistics(ip, uri);

        // Если даты не указаны - берем события от текущего момента
        if (rangeStart == null && rangeEnd == null) {
            rangeStart = LocalDateTime.now();
        }

        // Валидация дат
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Start date must be before end date");
        }

        Pageable pageable = PageRequest.of(from / size, size);

        // Фильтр только опубликованные события
        List<Event> events = eventRepository.findPublicEvents(
                text != null ? text.toLowerCase() : null,
                categories,
                paid,
                rangeStart,
                rangeEnd,
                pageable
        );

        // Фильтр только доступные (если требуется)
        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = events.stream()
                    .filter(e -> e.getParticipantLimit() == 0 ||
                            e.getConfirmedRequests() < e.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        // Получение статистики просмотров
        Map<Long, Long> viewsMap = getViewsForEvents(events);

        // Обновление views в событиях
        events.forEach(event -> {
            Long views = viewsMap.getOrDefault(event.getId(), 0L);
            event.setViews(views);
        });

        // Сортировка
        if (sort != null) {
            switch (sort) {
                case EVENT_DATE:
                    events.sort(Comparator.comparing(Event::getEventDate));
                    break;
                case VIEWS:
                    events.sort(Comparator.comparing(Event::getViews).reversed());
                    break;
            }
        }

        return events.stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getPublishedEvent(Long eventId, String ip, String uri) {
        log.info("Getting published event: {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        // Отправка статистики
        sendStatistics(ip, uri);

        // Получение views из stats-service
        Long views = getViewsForEvent(eventId);
        event.setViews(views);

        return eventMapper.toFullDto(event);
    }

    private void sendStatistics(String ip, String uri) {
        try {
            EndpointHitDto hitDto = new EndpointHitDto();
            hitDto.setApp("ewm-main-service");
            hitDto.setUri(uri);
            hitDto.setIp(ip);
            hitDto.setTimestamp(LocalDateTime.now());

            statsClient.saveHit(hitDto);
            log.info("Sent statistics for uri: {}, ip: {}", uri, ip);
        } catch (Exception e) {
            log.error("Failed to send statistics", e);
        }
    }

    private Long getViewsForEvent(Long eventId) {
        try {
            List<ViewStatsDto> stats = statsClient.getStats(
                    LocalDateTime.of(2020, 1, 1, 0, 0),
                    LocalDateTime.now().plusDays(1),
                    List.of("/events/" + eventId),
                    true
            );

            if (stats != null && !stats.isEmpty()) {
                return stats.get(0).getHits();
            }
        } catch (Exception e) {
            log.error("Failed to get views for event {}", eventId, e);
        }
        return 0L;
    }

    private Map<Long, Long> getViewsForEvents(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        try {
            List<ViewStatsDto> stats = statsClient.getStats(
                    LocalDateTime.of(2020, 1, 1, 0, 0),
                    LocalDateTime.now().plusDays(1),
                    uris,
                    true
            );

            if (stats != null) {
                return stats.stream()
                        .collect(Collectors.toMap(
                                stat -> Long.parseLong(stat.getUri().substring("/events/".length())),
                                ViewStatsDto::getHits
                        ));
            }
        } catch (Exception e) {
            log.error("Failed to get views for events", e);
        }

        return Collections.emptyMap();
    }

    public List<EventFullDto> getAdminEvents(List<Long> users, List<String> states,
                                             List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                             Integer from, Integer size) {
        log.info("Getting admin events with filters");

        Pageable pageable = PageRequest.of(from / size, size);

        // Преобразование строк состояний в enum
        List<EventState> eventStates = null;
        if (states != null && !states.isEmpty()) {
            eventStates = states.stream()
                    .map(EventState::valueOf)
                    .collect(Collectors.toList());
        }

        List<Event> events = eventRepository.findAdminEvents(
                users,
                eventStates,
                categories,
                rangeStart,
                rangeEnd,
                pageable
        );

        return events.stream()
                .map(eventMapper::toFullDto)
                .collect(Collectors.toList());
    }

    private Event getEventByIdAndInitiator(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        return event;
    }
}