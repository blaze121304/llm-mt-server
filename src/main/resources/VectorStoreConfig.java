package com.llmmt.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    /**
     * 정중하고 신뢰감 있는 문어체 비즈니스 톤앤매너.
     * 제공된 컨텍스트 외 내용을 생성(Hallucination)하는 행위를 원천 차단한다.
     */
    private static final String SYSTEM_PROMPT = """
            당신은 Jira 이슈 분석 및 장애 해결 이력 전문 어시스턴트입니다.
            반드시 제공된 [Jira 티켓 컨텍스트]에 기반하여서만 답변하여 주십시오.
            문맥 내에 명확한 해결 이력이 없다면, 내용을 왜곡하거나 지어내지 마십시오.
            해결 히스토리를 찾지 못한 경우, 반드시 다음 문구로만 응답하십시오:
            "해당 장애에 대한 과거 해결 히스토리를 찾지 못했습니다."
            """;

    /**
     * PgVectorStore 빈을 명시적으로 구성한다.
     * EmbeddingModel은 spring-ai-openai-spring-boot-starter가 application.yml의
     * spring.ai.openai.embedding.options.model(text-embedding-3-small) 설정으로 자동 구성한다.
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(false)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
