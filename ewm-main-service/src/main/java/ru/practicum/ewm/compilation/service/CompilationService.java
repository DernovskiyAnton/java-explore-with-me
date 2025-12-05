package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.mapper.CompilationMapper;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper mapper;

    @Transactional
    public CompilationDto addCompilation(NewCompilationDto dto) {
        log.info("Adding new compilation: {}", dto.getTitle());

        Set<Event> events = new HashSet<>();
        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            events = new HashSet<>(eventRepository.findAllById(dto.getEvents()));
        }

        Compilation compilation = mapper.toEntity(dto, events);
        Compilation saved = compilationRepository.save(compilation);

        log.info("Compilation added with id: {}", saved.getId());
        return mapper.toDto(saved);
    }

    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Deleting compilation id: {}", compId);

        Compilation compilation = getCompilationById(compId);
        compilationRepository.delete(compilation);

        log.info("Compilation deleted: {}", compId);
    }

    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest dto) {
        log.info("Updating compilation id: {}", compId);

        Compilation compilation = getCompilationById(compId);

        if (dto.getEvents() != null) {
            Set<Event> events = new HashSet<>(eventRepository.findAllById(dto.getEvents()));
            compilation.setEvents(events);
        }

        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }

        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }

        Compilation updated = compilationRepository.save(compilation);
        log.info("Compilation updated: {}", updated.getId());

        return mapper.toDto(updated);
    }

    public List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size) {
        log.info("Getting compilations: pinned={}, from={}, size={}", pinned, from, size);

        Pageable pageable = PageRequest.of(from / size, size);

        if (pinned != null) {
            return compilationRepository.findByPinned(pinned, pageable).stream()
                    .map(mapper::toDto)
                    .collect(Collectors.toList());
        }

        return compilationRepository.findAll(pageable).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public CompilationDto getCompilation(Long compId) {
        log.info("Getting compilation id: {}", compId);

        Compilation compilation = getCompilationById(compId);
        return mapper.toDto(compilation);
    }

    private Compilation getCompilationById(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }
}