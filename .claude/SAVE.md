# llm-mt-server 프로젝트 분석 보고서
> 최초 작성: 2026-05-29 | 최종 업데이트: 2026-05-29 | 버전: v1.0

---

## 1. 프로젝트 개요

**llm-mt-server**는 **Spring Boot 3.x + Spring AI 1.0.0** 기반의 **Jira RAG(Retrieval-Augmented Generation) 봇 서버**이다.

- 언어: Java 21
- 프레임워크: Spring Boot 3.3.5, Spring AI 1.0.0
- Vector DB: PostgreSQL + pgvector (`spring-ai-pgvector-store-spring-boot-starter`)
- LLM: OpenAI GPT-4o (ChatClient), Embedding: `text-embedding-3-small`
- 빌드: Maven (pom.xml)
- 목적: Jira 이슈 데이터를 Vector DB에서 유사도 검색 후 LLM으로 장애 해결 가이드 생성

---

## 2. 현재 상태 (2026-05-29)

### Git 상태
- 브랜치: `master`
- 초기 구현 완료 (v1.0)

### 구현 완료 목록
- [x] `pom.xml` — 의존성 구성 (Spring AI BOM, OpenAI/PgVector 스타터, Lombok, Validation)
- [x] `VectorStoreConfig.java` — PgVectorStore 빈 + ChatClient 빈 + 시스템 프롬프트 정의
- [x] `JiraRagService.java` — RAG 비즈니스 로직 (유사도 검색 Top-K=3, project_key 권한 필터)
- [x] `JiraBotController.java` — POST `/api/v1/jira-bot/ask` REST 엔드포인트
- [x] `JiraBotRequest.java` — 요청 DTO (record)
- [x] `JiraBotResponse.java` — 응답 DTO (record)
- [x] `GlobalExceptionHandler.java` — Validation 오류 + 전역 예외 처리 (`ProblemDetail`)
- [x] `application.yml` — OpenAI API 키, DB, 임베딩 모델 프로퍼티 매핑

### 다음 할 일
- [ ] Jira 이슈 데이터 적재 파이프라인 구현 (Vector DB ingestion)
- [ ] 단위/통합 테스트 작성
- [ ] Docker Compose (PostgreSQL + pgvector 로컬 개발 환경)

---

## 3. 디렉토리 구조

```
llm-mt-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/llmmt/
│       │       ├── LlmMtServerApplication.java   # 진입점
│       │       ├── config/
│       │       │   └── VectorStoreConfig.java    # PgVectorStore + ChatClient 빈
│       │       ├── controller/
│       │       │   └── JiraBotController.java    # POST /api/v1/jira-bot/ask
│       │       ├── dto/
│       │       │   ├── JiraBotRequest.java       # 요청 record DTO
│       │       │   └── JiraBotResponse.java      # 응답 record DTO
│       │       ├── exception/
│       │       │   └── GlobalExceptionHandler.java # @RestControllerAdvice
│       │       └── service/
│       │           └── JiraRagService.java       # RAG 핵심 로직
│       └── resources/
│           └── application.yml                   # 프로퍼티 설정
├── .claude/
│   ├── CLAUDE.md
│   ├── SAVE.md                                   # 이 파일
│   └── .docs/
│       └── PRD.md
└── pom.xml
```

---

## 4. 아키텍처

### 레이어 구조

```
┌─────────────────────────────────────────────────┐
│  POST /api/v1/jira-bot/ask                      │
│  JiraBotController (@RestController)            │
└────────────────┬────────────────────────────────┘
                 │ JiraBotRequest(query, userGroup, accessibleProjects)
┌────────────────▼────────────────────────────────┐
│  JiraRagService (@Service)                      │
│  1. FilterExpression(project_key IN [...])       │
│  2. VectorStore.similaritySearch(TopK=3)         │
│  3. Context 조립 → ChatClient.prompt().call()   │
└────┬──────────────────────────────┬─────────────┘
     │                              │
┌────▼──────────┐       ┌──────────▼──────────────┐
│ PgVectorStore │       │ OpenAI ChatClient        │
│ (pgvector)    │       │ (GPT-4o)                 │
│ text-embed-   │       │ system prompt: 문어체    │
│ 3-small       │       │ 비즈니스 톤앤매너        │
└───────────────┘       └─────────────────────────┘
```

### RAG 플로우

```
1. 요청 수신: { query, userGroup, accessibleProjects }
2. 권한 필터 구성: FilterExpression → project_key IN [accessibleProjects]
3. 유사도 검색: vectorStore.similaritySearch(query, topK=3, filter)
4. 결과 없음 → "해당 장애에 대한 과거 해결 히스토리를 찾지 못했습니다." 즉시 반환
5. 컨텍스트 조립: [ISSUE-KEY]\n본문\n---\n[ISSUE-KEY2]\n...
6. LLM 호출: ChatClient.prompt().user(context + query).call().content()
7. 응답 반환: { answer, sourceIssueKeys }
```

---

## 5. 주요 모듈 상세

### 5-1. `VectorStoreConfig.java`

| 빈 | 타입 | 설정 |
|----|------|------|
| `vectorStore` | `PgVectorStore` | dimensions=1536, COSINE_DISTANCE, initializeSchema=false |
| `chatClient` | `ChatClient` | defaultSystem=문어체 비즈니스 프롬프트 (Hallucination 차단 지시) |

- `EmbeddingModel` 자동 구성: `spring-ai-openai-spring-boot-starter` → `application.yml`의 `text-embedding-3-small` 설정 매핑
- `VectorStore` 빈 직접 정의 시 auto-configuration은 `@ConditionalOnMissingBean`에 의해 비활성화됨

### 5-2. `JiraRagService.java`

| 메서드 | 역할 |
|--------|------|
| `ask(JiraBotRequest)` | RAG 오케스트레이션 진입점 |
| `retrieveDocuments(query, projects)` | FilterExpression 구성 + VectorStore 유사도 검색 |
| `buildContext(documents)` | 검색 결과 → LLM 입력 컨텍스트 문자열 변환 |
| `extractIssueKeys(documents)` | 문서 메타데이터 `issue_key` 추출 (출처 정보) |

- **권한 제어**: `FilterExpressionBuilder.in("project_key", ...)` → 허가된 프로젝트만 검색
- **Top-K**: 상수 `TOP_K = 3`
- **문서 없음 처리**: LLM 호출 없이 고정 응답 즉시 반환

### 5-3. `JiraBotController.java`

```
POST /api/v1/jira-bot/ask
Content-Type: application/json

Request:
{
  "query": "OOM 에러 해결 방법",
  "userGroup": "backend-team",
  "accessibleProjects": ["PROJ-A", "PROJ-B"]
}

Response 200:
{
  "answer": "PROJ-A-1234 티켓에서 ...",
  "sourceIssueKeys": ["PROJ-A-1234", "PROJ-A-5678"]
}

Response 400 (Validation):
{
  "title": "Validation Failed",
  "detail": "query: must not be blank",
  "status": 400
}
```

### 5-4. `GlobalExceptionHandler.java`

| 예외 | HTTP 상태 | 처리 |
|------|----------|------|
| `MethodArgumentNotValidException` | 400 | `@Valid` 위반 필드 명세 반환 |
| `Exception` (전체) | 500 | 로그 기록 + 한국어 오류 메시지 반환 |

- Spring 6 표준 `ProblemDetail` (RFC 7807) 사용

---

## 6. 의존성

```
Spring Boot 3.3.5
Spring AI 1.0.0 (BOM)
  ├── spring-ai-openai-spring-boot-starter     # ChatClient + EmbeddingModel (text-embedding-3-small)
  └── spring-ai-pgvector-store-spring-boot-starter  # PgVectorStore
spring-boot-starter-web                        # REST API
spring-boot-starter-validation                 # @Valid, @NotBlank, @NotEmpty
spring-boot-starter-jdbc                       # JdbcTemplate (PgVectorStore 의존)
postgresql                                     # JDBC 드라이버 (runtime)
lombok                                         # @Slf4j, @RequiredArgsConstructor
```

---

## 7. 설정 스키마 (`application.yml`)

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/jiradb}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}          # 필수: 환경변수
      chat.options.model: gpt-4o
      embedding.options.model: text-embedding-3-small
    vectorstore.pgvector:
      initialize-schema: false
      dimensions: 1536
      distance-type: COSINE_DISTANCE
```

- API 키는 환경변수 `OPENAI_API_KEY`로만 주입 (하드코딩 금지)
- DB 접속 정보도 환경변수 기반 (기본값은 로컬 개발용)

---

## 8. Vector DB 메타데이터 스키마 (전제)

데이터 적재 시 다음 메타데이터가 각 Document에 포함되어야 한다:

| 메타데이터 키 | 타입 | 역할 |
|--------------|------|------|
| `issue_key` | String | Jira 이슈 키 (예: `PROJ-1234`) — 출처 반환에 사용 |
| `project_key` | String | Jira 프로젝트 키 (예: `PROJ`) — 권한 필터 기준 |

---

## 요약

llm-mt-server는 Spring AI 표준 스택(PgVector + OpenAI)을 활용하여 Jira 이슈 기반 장애 해결 이력을 RAG 방식으로 서빙하는 서버이다. `project_key` 메타데이터 기반 접근 제어로 권한 없는 데이터의 노출을 원천 차단하며, Hallucination 방지 시스템 프롬프트를 통해 신뢰도 높은 비즈니스 응답을 제공한다.
