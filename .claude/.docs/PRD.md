너는 자바 엔지니어이자 테크 아키텍트이다. Spring Boot 3.x 환경에서 Spring AI 표준 라이브러리를 활용하여, Vector DB를 탐색하고 LLM 답변을 서빙하는 RAG 비즈니스 로직 및 컨트롤러 코드를 작성하라. 다음 아키텍처 설계를 엄격히 준수해야 한다.

1. 의존성 및 설정 정보
- Spring AI OpenAI 및 Pgvector 확장 스타터를 활용한 설정 클래스(`VectorStoreConfig`)를 정의하라.
- 데이터 적재 시 사용된 OpenAI `text-embedding-3-small` 모델 설정을 프로퍼티 파일과 매핑하라.

2. 비즈니스 서비스 클래스 구현 (`JiraRagService.java`)
- `VectorStore` 인터페이스를 주입받아 사용자의 질문 문자열로 유사도 검색(`similaritySearch`)을 수행하는 로직을 작성하라. 최적의 컨텍스트 제공을 위해 Top-K 값을 3으로 설정하라.
- 메타데이터 권한 제어: 검색 요청 시 특정 `project_key`에만 매칭되도록 Spring AI의 `FilterExpression`을 구성하라. 권한이 없는 프로젝트의 데이터는 검색 결과에서 원천 배제되어야 한다.

3. 프롬프트 오케스트레이션 및 LLM 통신 (`ChatClient`)
- `ChatClient`를 활용하여 시스템 프롬프트를 조립하라. 시스템 지시문은 정중하고 신뢰감 있는 문어체 비즈니스 톤앤매너로 설정되어야 한다.
- 프롬프트 제약 조건 제어: "제공된 [Jira 티켓 컨텍스트]에 기반해서만 답변하고, 만약 문맥 내에 명확한 해결 이력이 없다면 왜곡(Hallucination)하여 지어내지 말고 '해당 장애에 대한 과거 해결 히스토리를 찾지 못했습니다.'라고 응답을 제한하라."

4. REST 엔드포인트 정의 (`JiraBotController.java`)
- POST `/api/v1/jira-bot/ask` 엔드포인트를 개방하라.
- 요청 바디 DTO 구조: `{ "query": String, "userGroup": String, "accessibleProjects": List<String> }`
- 응답 구조: 최종 생성된 가이드 답변 스트링과 참고한 Jira Issue Key 리스트(출처 정보)를 함께 반환하는 구조로 DTO를 설계하라.

모든 코드는 가독성이 높고 예외 처리가 안정적으로 이루어진 자바 표준 스타일 프로덕션 코드로 작성하라.