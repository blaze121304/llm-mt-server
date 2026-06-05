package com.llmmt.service;

import com.llmmt.dto.JiraBotRequest;
import com.llmmt.dto.JiraBotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraRagService {

    private static final int TOP_K = 3;
    private static final String NO_HISTORY_RESPONSE = "해당 장애에 대한 과거 해결 히스토리를 찾지 못했습니다.";

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public JiraBotResponse ask(JiraBotRequest request) {
        List<Document> documents = retrieveDocuments(request.query(), request.accessibleProjects());

        if (documents.isEmpty()) {
            log.info("No relevant documents found. query='{}', projects={}", request.query(), request.accessibleProjects());
            return new JiraBotResponse(NO_HISTORY_RESPONSE, List.of());
        }

        String context = buildContext(documents);
        List<String> sourceIssueKeys = extractIssueKeys(documents);
        String userMessage = String.format("[Jira 티켓 컨텍스트]\n%s\n\n[질문]\n%s", context, request.query());

        String answer = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        log.debug("Answer generated. query='{}', sources={}", request.query(), sourceIssueKeys);
        return new JiraBotResponse(answer, sourceIssueKeys);
    }

    /**
     * project_key 메타데이터 필터를 적용하여 접근 허가된 프로젝트의 문서만 검색한다.
     * accessibleProjects 기반 FilterExpression은 권한 없는 프로젝트 데이터를 원천 배제한다.
     */
    private List<Document> retrieveDocuments(String query, List<String> accessibleProjects) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = b.in("project_key", (Object[]) accessibleProjects.toArray(new String[0])).build();

        SearchRequest searchRequest = SearchRequest.query(query)
                .withTopK(TOP_K)
                .withFilterExpression(filter);

        return vectorStore.similaritySearch(searchRequest);
    }

    private String buildContext(List<Document> documents) {
        return documents.stream()
                .map(doc -> String.format("[%s]\n%s",
                        doc.getMetadata().getOrDefault("issue_key", "UNKNOWN"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private List<String> extractIssueKeys(List<Document> documents) {
        return documents.stream()
                .map(doc -> (String) doc.getMetadata().get("issue_key"))
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
    }
}
