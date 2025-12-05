package ru.practicum.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class StatsClient {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final String serverUrl;

    public StatsClient(@Value("${stats-server.url:http://localhost:9090}") String serverUrl) {
        this.restTemplate = new RestTemplate();
        this.serverUrl = serverUrl;
    }

    public void saveHit(EndpointHitDto dto) {
        log.info("Sending hit to stats server: {}", dto);
        String url = serverUrl + "/hit";
        restTemplate.postForEntity(url, dto, Void.class);
    }

    public List<ViewStatsDto> getStats(LocalDateTime start,
                                       LocalDateTime end,
                                       List<String> uris,
                                       Boolean unique) {
        log.info("Getting stats from stats server");

        String encodedStart = URLEncoder.encode(start.format(FORMATTER), StandardCharsets.UTF_8);
        String encodedEnd = URLEncoder.encode(end.format(FORMATTER), StandardCharsets.UTF_8);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverUrl + "/stats")
                .queryParam("start", encodedStart)
                .queryParam("end", encodedEnd);

        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", String.join(",", uris));
        }

        if (unique != null) {
            builder.queryParam("unique", unique);
        }

        ResponseEntity<List<ViewStatsDto>> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );

        return response.getBody();
    }
}