package com.llmmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record JiraBotRequest(
        @NotBlank String query,
        String userGroup,
        @NotEmpty List<String> accessibleProjects
) {}
