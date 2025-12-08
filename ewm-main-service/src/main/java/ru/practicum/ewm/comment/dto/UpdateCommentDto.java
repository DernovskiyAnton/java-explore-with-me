package ru.practicum.ewm.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCommentDto {

    @NotBlank(message = "Comment text cannot be blank")
    @Size(min = 1, max = 5000, message = "Comment text must be between 1 and 5000 characters")
    private String text;
}