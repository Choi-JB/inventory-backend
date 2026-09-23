# 진행 상황 & 컨텍스트 노트

**목적**: 다른 컴퓨터/새 세션에서 이어서 작업할 때 지금까지의 설계 결정과 트러블슈팅 이력을 빠르게 파악하기 위한 문서.
**최종 갱신**: 2026-09-23

---

## 1. 환경 구성

- **DB**: Supabase (PostgreSQL 17 + pgvector). 로컬 Docker 안 씀 — 개발 단계부터 Supabase 클라우드 DB를 바로 사용.
  - **주의**: Direct connection(`db.<ref>.supabase.co:5432`)은 이 환경에서 `UnknownHostException` 발생 (IPv6 전용 이슈로 추정). 반드시 **Session Pooler** 접속 정보 사용.
    ```
    host=aws-0-ap-northeast-2.pooler.supabase.com
    port=5432
    database=postgres
    user=postgres.<project-ref>   ← 프로젝트 ref가 유저명에 포함됨
    ```
- **JDK**: 21 (Eclipse Temurin) — 시스템에 JDK 25가 깔려 있어도 Gradle 툴체인이 21을 찾아서 씀. Gradle 버전(9.7.1)과 foojay-resolver 플러그인 조합이 안 맞아서 auto-provisioning은 실패했음 → JDK 21을 직접 설치하는 방식으로 해결.
- **Spring Boot 4.0.8** 사용 중 (Spring Initializr 기준 최신). 이 버전은 **Jackson 3.x**를 씀 — 패키지가 `com.fasterxml.jackson.databind`가 아니라 **`tools.jackson.databind`**로 이동했음. `ObjectMapper` 등을 직접 import할 때 이 점 주의.
- **JWT 라이브러리**: `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6`
- `spring-boot-starter-webmvc`만으로는 Jackson이 안 딸려옴 → `spring-boot-starter-json` 별도 추가 필요했음.

### 로컬 설정 파일 (git에 안 올라감, 각자 새로 만들어야 함)
`src/main/resources/application-local.yaml` — `.gitignore`에 등록됨. 아래 내용을 채워야 함:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres
    username: postgres.<project-ref>
    password: <Supabase DB 비밀번호>
    driver-class-name: org.postgresql.Driver
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: <구글 OAuth 클라이언트 ID>
            client-secret: <구글 OAuth 클라이언트 시크릿>
            redirect-uri: "{baseUrl}/api/auth/google/callback"
            scope:
              - email
              - profile

jwt:
  secret: <32자 이상 랜덤 문자열>
  expiration: 86400000
```
`jwt.secret`은 `openssl rand -base64 64` (Git Bash) 또는 PowerShell `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))`로 생성.

**Google Cloud Console**에도 승인된 리디렉션 URI로 `http://localhost:8080/api/auth/google/callback` 등록 필요.

---

## 2. 브랜치 전략 (레포구성_브랜치전략 문서 기준)

```
main - dev - feature/*
```
- `main` 직push 금지, `feature/*` → `dev` PR 후 squash merge
- 작업순서 문서 순서대로 feature 브랜치 하나씩 진행 (동시에 여러 개 열지 않음)
- 브랜치명은 문서상 `develop`이지만 실제로는 `dev`로 사용 중 (의도적 결정, 문서와 다름)

**완료된 브랜치** (dev에 merge됨):
1. DB 스키마 (Supabase SQL Editor에서 DDL 직접 실행 — DB테이블설계서 그대로)
2. `feature/spring-init` — Spring Boot 프로젝트 세팅, DB 연결 확인 (develop 브랜치로 나누지 않고 초기 커밋으로 처리)
3. `feature/entity-repository` — Entity 4종, enum 4종, Repository 4종
4. `feature/auth-jwt-oauth` — JWT + 구글 OAuth2
5. `feature/category-crud` — Category 트리 CRUD + 공통 예외 처리(`GlobalExceptionHandler`) 최초 도입

**다음 진행 예정**: `feature/product-crud` (6단계)

---

## 3. 주요 설계 결정 (문서에 없던, 진행하면서 정한 것들)

- **Category.parentId, StockTransaction.reversalOfId는 `@ManyToOne` 대신 순수 FK(`Long`)로 매핑.** 트리 조립/롤백 조회는 JPQL·네이티브 쿼리로 직접 처리하는 방향. (API 명세서가 "재귀 쿼리 또는 애플리케이션 레벨 조립"이라고 한 부분과 일치)
- **`@ManyToOne`을 실제로 쓸 경우 `fetch = FetchType.LAZY` 필수** (기본값 EAGER는 지양) — 이번 프로젝트에선 위 결정 때문에 아직 실사용 안 함.
- **인증 흐름**: `SecurityConfig`에서 `SessionCreationPolicy`를 `STATELESS`로 하면 OAuth2 로그인 자체가 무한 리다이렉트에 빠짐 (state 값을 저장할 세션이 없어서). **`IF_REQUIRED`로 설정** — OAuth 핸드셰이크 때만 세션 사용, 이후 API 인증은 JWT로 완전히 stateless.
- **`redirect-uri`를 커스텀 경로로 바꿀 때는 `SecurityConfig`의 `.oauth2Login(...)` 안에 `.redirectionEndpoint(redirection -> redirection.baseUri(...))`도 같이 설정해야 함.** `application-local.yaml`의 `redirect-uri` 속성만 바꾸는 걸로는 실제 콜백 처리 필터의 매칭 경로가 안 바뀜 (기본값 `/login/oauth2/code/*`로 남아있어서 인증이 안 됨).
- **비즈니스 로직은 Service 계층에 캡슐화** (패키지구조설계서 2.5 원칙). 초기에 `OAuth2LoginSuccessHandler`에 유저 조회/생성+JWT 발급 로직을 직접 넣었다가, `AuthService.loginWithGoogle()`로 리팩토링 완료. Handler는 이제 요청/응답 변환만 담당.
- **`ddl-auto: validate`** 사용 — Entity가 Supabase에 이미 만든 테이블과 정확히 일치해야 서버가 뜸 (컬럼명, enum `@Enumerated(STRING)` 여부 등 주의).
- **예외 처리 구조**: `BusinessException`(추상 클래스, `getCode()`/`getStatus()` 선언) → `NotFoundException`(404), `DeleteConflictException`(409)이 상속. `GlobalExceptionHandler`가 `BusinessException` 하나만 잡으면 되므로, 새 예외(`InsufficientStockException` 등, stock-transaction 단계에서 추가 예정)가 생겨도 핸들러 코드를 안 건드려도 됨.
- **`GlobalExceptionHandler`의 catch-all(`Exception.class`) 핸들러는 반드시 로그를 남겨야 함.** 안 남기면 클라이언트도 서버 콘솔도 원인을 알 수 없음 — Spring이 `@ExceptionHandler`가 처리한 예외는 "처리됨"으로 보고 자동 에러 로그를 안 남기기 때문. `log.error("...", e)`를 꼭 추가할 것.
- **catch-all이 프레임워크 예외까지 삼켜버리는 문제 발견**: `AuthorizationDeniedException`(`@PreAuthorize` 실패, 원래 403이어야 함), `HttpRequestMethodNotSupportedException`(잘못된 HTTP 메서드/경로, 원래 405여야 함)이 전부 catch-all에 걸려 500으로 잘못 나갔음. `AuthorizationDeniedException`은 전용 핸들러 추가해서 403으로 고침. `HttpRequestMethodNotSupportedException`(405)은 **아직 안 고침 — product-crud 단계에서 처리할 것**.
- **응답 DTO 패턴**: Category 생성/수정 응답을 처음엔 엔티티(`Category`) 그대로 반환했다가, 일관성을 위해 `CategoryResponse`(record, `from(Category)` 정적 팩토리) 로 리팩토링함. Product/StockTransaction도 같은 패턴(엔티티 직접 반환 금지, 응답 DTO 경유) 유지할 것.
- **JPA dirty checking vs `save()`**: 같은 트랜잭션 안에서 `findById()`로 가져온 영속 상태 엔티티는 필드만 바꿔도 트랜잭션 커밋 시 자동 UPDATE됨(`save()` 호출 불필요, 호출해도 무해함 — 이미 관리 중인 엔티티라 `merge()`가 그대로 반환만 함). 이 원칙은 detached 엔티티(다른 트랜잭션에서 가져온 경우)에는 적용 안 되니 주의.
- **역할(role) 변경 후 반드시 재로그인 필요**: role은 JWT 클레임에 박혀서 발급되므로, DB에서 role을 바꿔도 기존 토큰엔 반영 안 됨(API 명세서에 명시된 내용). ADMIN/STAFF 권한 테스트 시 재로그인해서 새 토큰 받아야 함 — 계정 2개 없어도 계정 1개로 role 토글하면서 양쪽 다 테스트 가능.
- **Windows 콘솔 한글 로그 깨짐**: `gradlew bootRun` 콘솔에서 한글 로그 메시지가 깨져 보임(인코딩 문제, cp949 vs UTF-8 추정). 기능적 문제는 아니고 가독성 문제라 우선순위 낮음 — 필요시 나중에 콘솔 인코딩 설정으로 해결.

---

## 4. AI(Claude) 활용 방식

작업순서 문서의 기준(핵심 학습 포인트는 직접 작성, 보일러플레이트는 AI 생성) 그대로 따르는 중. Entity/Repository/Security/Service/Controller 전부 사용자가 직접 작성, Claude는 리뷰(버그/설계 이슈 지적) + 개념 설명 역할만 수행.

**중요 — 예시 코드 주는 방식**: 처음엔 "예시"라면서 완성된 실행 가능 코드를 통째로 줬는데, 이러면 그냥 복사·붙여넣기가 되어버려서 학습 효과가 없다는 피드백을 받음 (2026-09-23). 그 이후로는 **메서드 시그니처 + 주석 힌트만 주고 실제 구현 로직은 직접 채우게 하는 방식**으로 전환함 (예: `CategoryController`의 POST/PUT/DELETE는 시그니처와 힌트만 주고 본문은 직접 작성하게 함). 새 세션에서도 이 방식 유지할 것 — 완성 코드를 바로 주지 말고 뼈대만.

---

## 5. 다음 단계

`feature/product-crud` (6단계) — Product CRUD. API 명세서 4번 참고: 말단 카테고리에만 등록 가능(카테고리가 하위를 가지면 400), `costPrice`/`currentStock`은 요청으로 안 받음(입고 트랜잭션으로만 증가), `sku`/`costPrice`/`currentStock`은 수정 불가.

**이번 단계에서 같이 처리하면 좋을 것**: `GlobalExceptionHandler`의 `HttpRequestMethodNotSupportedException`(405) 미처리 문제 (섹션 3 참고).
