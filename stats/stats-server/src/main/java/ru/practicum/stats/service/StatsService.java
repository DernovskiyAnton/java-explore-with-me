package ru.practicum.stats.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.stats.exception.ValidationException;
import ru.practicum.stats.mapper.StatsMapper;
import ru.practicum.stats.model.EndpointHit;
import ru.practicum.stats.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository repository;
    private final StatsMapper mapper;

    @Transactional
    public EndpointHitDto saveHit(EndpointHitDto dto) {
        log.info("Saving endpoint hit: {}", dto);
        EndpointHit entity = mapper.toEntity(dto);
        EndpointHit saved = repository.save(entity);
        return mapper.toDto(saved);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start,
                                       LocalDateTime end,
                                       List<String> uris,
                                       Boolean unique) {
        if (start.isAfter(end)) {
            throw new ValidationException("Start date must be before end date");
        }

        log.info("Getting stats from {} to {}, uris: {}, unique: {}", start, end, uris, unique);

        if (Boolean.TRUE.equals(unique)) {
            return repository.getUniqueStats(start, end, uris);
        }
        return repository.getStats(start, end, uris);
    }
}