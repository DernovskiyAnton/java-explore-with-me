package ru.practicum.ewm.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LocationDto {

    @NotNull(message = "Latitude cannot be null")
    private Float lat;

    @NotNull(message = "Longitude cannot be null")
    private Float lon;
}