package com.llmmt.controller;

import com.llmmt.dto.JiraBotRequest;
import com.llmmt.dto.JiraBotResponse;
import com.llmmt.service.JiraRagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jira-bot")
@RequiredArgsConstructor
public class JiraBotController {

    private final JiraRagService jiraRagService;

    @PostMapping("/ask")
    public ResponseEntity<JiraBotResponse> ask(@RequestBody @Valid JiraBotRequest request) {
        return ResponseEntity.ok(jiraRagService.ask(request));
    }
}
