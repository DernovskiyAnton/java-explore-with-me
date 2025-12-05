package ru.practicum.stats.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.stats.model.EndpointHit;

@Component
public class StatsMapper {

    public EndpointHit toEntity(EndpointHitDto dto) {
        EndpointHit entity = new EndpointHit();
        entity.setId(dto.getId());
        entity.setApp(dto.getApp());
        entity.setUri(dto.getUri());
        entity.setIp(dto.getIp());
        entity.setTimestamp(dto.getTimestamp());
        return entity;
    }

    public EndpointHitDto toDto(EndpointHit entity) {
        EndpointHitDto dto = new EndpointHitDto();
        dto.setId(entity.getId());
        dto.setApp(entity.getApp());
        dto.setUri(entity.getUri());
        dto.setIp(entity.getIp());
        dto.setTimestamp(entity.getTimestamp());
        return dto;
    }
}