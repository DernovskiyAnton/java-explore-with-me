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
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.dto.UpdateEventUserRequest;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventSort;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.LocationRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;

import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
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
    private final ParticipationRequestRepository participationRequestRepository;
    private final EventMapper eventMapper;
    private final StatsClient statsClient;

    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        log.info("Adding new event by user {}: {}", userId, dto.getTitle());

        // ===== ВАЛИДАЦИЯ ДАТЫ =====
        if (dto.getEventDate() != null) {
            LocalDateTime minEventDate = LocalDateTime.now().plusHours(2);
            if (dto.getEventDate().isBefore(minEventDate)) {
                throw new ValidationException(
                        "Field: eventDate. Error: должно содержать дату, которая еще не наступила. " +
                                "Value: " + dto.getEventDate()
                );
            }
        }
        // ===== КОНЕЦ ВАЛИДАЦИИ =====

        User user = getUserById(userId);
        Category category = getCategoryById(dto.getCategory());

        Location location = new Location();
        location.setLat(dto.getLocation().getLat());
        location.setLon(dto.getLocation().getLon());
        Location savedLocation = locationRepository.save(location);

        Event event = new Event();
        event.setTitle(dto.getTitle());
        event.setAnnotation(dto.getAnnotation());
        event.setDescription(dto.getDescription());
        event.setCategory(category);
        event.setEventDate(dto.getEventDate());
        event.setLocation(savedLocation);
        event.setInitiator(user);
        event.setPaid(dto.getPaid() != null ? dto.getPaid() : false);
        event.setParticipantLimit(dto.getParticipantLimit() != null ? dto.getParticipantLimit() : 0);
        event.setRequestModeration(dto.getRequestModeration() != null ? dto.getRequestModeration() : true);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        event.setViews(0L);
        event.setConfirmedRequests(0L);

        Event saved = eventRepository.save(event);
        log.info("Event created: {}", saved.getId());

        return eventMapper.toFullDto(saved);
    }

    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        log.info("Getting events for user {}", userId);

        getUserById(userId);

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable);

        return events.stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        log.info("Getting event: {} for user: {}", eventId, userId);

        Event event = getEventByIdAndInitiator(eventId, userId);

        return eventMapper.toFullDto(event);
    }

    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest dto) {
        log.info("Updating event {} by user {}", eventId, userId);

        Event event = getEventByIdAndInitiator(eventId, userId);

        // Проверка состояния - можно изменять только PENDING или CANCELED
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        // ===== ВАЛИДАЦИЯ ДАТЫ =====
        if (dto.getEventDate() != null) {
            LocalDateTime minEventDate = LocalDateTime.now().plusHours(2);
            if (dto.getEventDate().isBefore(minEventDate)) {
                throw new ValidationException(
                        "Field: eventDate. Error: должно содержать дату, которая еще не наступила. " +
                                "Value: " + dto.getEventDate()
                );
            }
            event.setEventDate(dto.getEventDate());
        }
        // ===== КОНЕЦ ВАЛИДАЦИИ =====

        // Обновление остальных полей
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

    public List<EventFullDto> getAdminEvents(List<Long> users,
                                             List<String> states,
                                             List<Long> categories,
                                             LocalDateTime rangeStart,
                                             LocalDateTime rangeEnd,
                                             Integer from,
                                             Integer size) {
        log.info("Admin getting events with filters");

        List<EventState> eventStates = null;
        if (states != null && !states.isEmpty()) {
            eventStates = states.stream()
                    .map(EventState::valueOf)
                    .collect(Collectors.toList());
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAdminEvents(
                users, eventStates, categories, rangeStart, rangeEnd, pageable
        );

        return events.stream()
                .map(event -> {
                    Long confirmedRequests = participationRequestRepository
                            .countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
                    return eventMapper.toFullDto(event, confirmedRequests);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        log.info("Admin updating event {}", eventId);

        Event event = getEventById(eventId);

        // ===== ВАЛИДАЦИЯ ДАТЫ - ПЕРЕД изменением состояния =====
        if (dto.getEventDate() != null) {
            LocalDateTime minEventDate = LocalDateTime.now().plusHours(1);
            if (dto.getEventDate().isBefore(minEventDate)) {
                throw new ValidationException(
                        "Field: eventDate. Error: Event date must be at least 1 hour from now. " +
                                "Value: " + dto.getEventDate()
                );
            }
            event.setEventDate(dto.getEventDate());
        }
        // ===== КОНЕЦ ВАЛИДАЦИИ =====

        // Обновление полей
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
                        throw new ConflictException(
                                "Cannot publish the event because it's not in the right state: " + event.getState()
                        );
                    }
                    // Проверка что до события >= 1 час
                    if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                        throw new ConflictException(
                                "Event date must be at least 1 hour from publication time"
                        );
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

    public List<EventShortDto> getPublicEvents(String text,
                                               List<Long> categories,
                                               Boolean paid,
                                               LocalDateTime rangeStart,
                                               LocalDateTime rangeEnd,
                                               Boolean onlyAvailable,
                                               String sort,
                                               Integer from,
                                               Integer size,
                                               String ip,
                                               String uri) {
        log.info("Getting public events with filters");

        // Отправляем статистику для поиска
        sendStatistics(ip, uri);

        // Если диапазон дат не указан, используем от текущего момента
        if (rangeStart == null && rangeEnd == null) {
            rangeStart = LocalDateTime.now();
        }

        // Валидация диапазона дат
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Start date must be before end date");
        }

        Pageable pageable = PageRequest.of(from / size, size);

        List<Event> events = eventRepository.findPublicEvents(
                text, categories, paid, rangeStart, rangeEnd, pageable
        );

        // Фильтр по доступности
        if (Boolean.TRUE.equals(onlyAvailable)) {
            events = events.stream()
                    .filter(event -> event.getParticipantLimit() == 0 ||
                            event.getConfirmedRequests() < event.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        // Получаем views для всех событий
        Map<Long, Long> viewsMap = getViewsForEvents(events);
        events.forEach(event -> event.setViews(viewsMap.getOrDefault(event.getId(), 0L)));

        // Сортировка
        if (sort != null) {
            EventSort sortType = EventSort.valueOf(sort);
            if (sortType == EventSort.EVENT_DATE) {
                events.sort(Comparator.comparing(Event::getEventDate));
            } else if (sortType == EventSort.VIEWS) {
                events.sort(Comparator.comparing(Event::getViews).reversed());
            }
        }

        return events.stream()
                .map(eventMapper::toShortDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventFullDto getPublishedEvent(Long eventId, String ip, String uri) {
        log.info("Getting published event {} from IP {}", eventId, ip);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        // 1. Отправляем статистику в stats-service
        sendStatistics(ip, uri);

        // 2. Небольшая задержка для обработки статистики
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. Получаем обновленные views из stats-service
        Long views = getViewsForEvent(eventId);

        // 4. Обновляем views в событии и сохраняем
        event.setViews(views);
        event = eventRepository.save(event);

        // 5. Подсчитываем подтвержденные заявки
        Long confirmedRequests = participationRequestRepository.countByEventIdAndStatus(
                eventId,
                RequestStatus.CONFIRMED
        );

        return eventMapper.toFullDto(event, confirmedRequests);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

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

    private Event getEventByIdAndInitiator(Long eventId, Long userId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private void sendStatistics(String ip, String uri) {
        try {
            EndpointHitDto hitDto = new EndpointHitDto();
            hitDto.setApp("ewm-main-service");
            hitDto.setUri(uri);
            hitDto.setIp(ip);
            hitDto.setTimestamp(LocalDateTime.now());
            statsClient.saveHit(hitDto);
            log.info("Statistics sent for uri: {}", uri);
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
            return new HashMap<>();
        }

        try {
            List<String> uris = events.stream()
                    .map(event -> "/events/" + event.getId())
                    .collect(Collectors.toList());

            List<ViewStatsDto> stats = statsClient.getStats(
                    LocalDateTime.of(2020, 1, 1, 0, 0),
                    LocalDateTime.now().plusDays(1),
                    uris,
                    true
            );

            if (stats != null && !stats.isEmpty()) {
                return stats.stream()
                        .collect(Collectors.toMap(
                                stat -> Long.parseLong(stat.getUri().substring("/events/".length())),
                                ViewStatsDto::getHits
                        ));
            }
        } catch (Exception e) {
            log.error("Failed to get views for events", e);
        }

        return new HashMap<>();
    }
}