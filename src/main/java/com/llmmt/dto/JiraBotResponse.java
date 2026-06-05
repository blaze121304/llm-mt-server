package com.llmmt.dto;

import java.util.List;

public record JiraBotResponse(
        String answer,
        List<String> sourceIssueKeys
) {}
