package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.mapper.ParticipationRequestMapper;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ParticipationRequestMapper mapper;

    @Transactional
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        log.info("Adding participation request: user={}, event={}", userId, eventId);

        User user = getUserById(userId);
        Event event = getEventById(eventId);

        // Проверки
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Request already exists");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Event initiator cannot request participation in their own event");
        }

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        if (event.getParticipantLimit() > 0 &&
                event.getConfirmedRequests() >= event.getParticipantLimit()) {
            throw new ConflictException("Participant limit reached");
        }

        ParticipationRequest request = new ParticipationRequest();
        request.setEvent(event);
        request.setRequester(user);
        request.setCreated(LocalDateTime.now());

        // Если пре-модерация отключена или лимит = 0, то сразу подтверждаем
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            request.setStatus(RequestStatus.CONFIRMED);
            event.setConfirmedRequests(event.getConfirmedRequests() + 1);
            eventRepository.save(event);
        } else {
            request.setStatus(RequestStatus.PENDING);
        }

        ParticipationRequest saved = requestRepository.save(request);
        log.info("Request added with id: {}", saved.getId());

        return mapper.toDto(saved);
    }

    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Getting requests for user: {}", userId);

        getUserById(userId); // Проверка существования

        return requestRepository.findByRequesterId(userId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Canceling request: user={}, request={}", userId, requestId);

        ParticipationRequest request = getRequestById(requestId);

        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Request with id=" + requestId + " was not found");
        }

        request.setStatus(RequestStatus.CANCELED);

        // Если запрос был подтвержден, уменьшаем счетчик
        if (request.getStatus().equals(RequestStatus.CONFIRMED)) {
            Event event = request.getEvent();
            event.setConfirmedRequests(event.getConfirmedRequests() - 1);
            eventRepository.save(event);
        }

        ParticipationRequest updated = requestRepository.save(request);
        log.info("Request canceled: {}", requestId);

        return mapper.toDto(updated);
    }

    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Getting requests for event: {} by user: {}", eventId, userId);

        Event event = getEventById(eventId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        return requestRepository.findByEventId(eventId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest dto) {
        log.info("Updating request status: user={}, event={}, status={}",
                userId, eventId, dto.getStatus());

        Event event = getEventById(eventId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        // Проверка лимита
        if (event.getParticipantLimit() > 0 &&
                event.getConfirmedRequests() >= event.getParticipantLimit() &&
                dto.getStatus().equals(RequestStatus.CONFIRMED)) {
            throw new ConflictException("Participant limit reached");
        }

        List<ParticipationRequest> requests = requestRepository.findByIdIn(dto.getRequestIds());
        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        for (ParticipationRequest request : requests) {
            if (!request.getStatus().equals(RequestStatus.PENDING)) {
                throw new ConflictException("Request must have status PENDING");
            }

            if (dto.getStatus().equals(RequestStatus.CONFIRMED)) {
                // Проверка лимита перед подтверждением
                if (event.getParticipantLimit() > 0 &&
                        event.getConfirmedRequests() >= event.getParticipantLimit()) {
                    request.setStatus(RequestStatus.REJECTED);
                    rejected.add(mapper.toDto(request));
                } else {
                    request.setStatus(RequestStatus.CONFIRMED);
                    event.setConfirmedRequests(event.getConfirmedRequests() + 1);
                    confirmed.add(mapper.toDto(request));
                }
            } else {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(mapper.toDto(request));
            }

            requestRepository.save(request);
        }

        // Если лимит достигнут, отклоняем остальные ожидающие запросы
        if (event.getParticipantLimit() > 0 &&
                event.getConfirmedRequests() >= event.getParticipantLimit()) {
            List<ParticipationRequest> pendingRequests = requestRepository
                    .findByEventId(eventId).stream()
                    .filter(r -> r.getStatus().equals(RequestStatus.PENDING))
                    .collect(Collectors.toList());

            for (ParticipationRequest pending : pendingRequests) {
                pending.setStatus(RequestStatus.REJECTED);
                requestRepository.save(pending);
                rejected.add(mapper.toDto(pending));
            }
        }

        eventRepository.save(event);

        log.info("Request status updated: confirmed={}, rejected={}",
                confirmed.size(), rejected.size());

        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Event getEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private ParticipationRequest getRequestById(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id=" + requestId + " was not found"));
    }
}