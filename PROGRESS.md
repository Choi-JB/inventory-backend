# 진행 상황 & 컨텍스트 노트

**목적**: 다른 컴퓨터/새 세션에서 이어서 작업할 때 지금까지의 설계 결정과 트러블슈팅 이력을 빠르게 파악하기 위한 문서.
**최종 갱신**: 2026-09-28

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
6. `feature/product-crud` — Product CRUD + 검색/페이징(`Specification`) + 말단 카테고리 검증

**진행 중**: `feature/stock-transaction` (7단계). IN/OUT/CONSUME/ADJUSTMENT 4개 트랜잭션 타입 구현 + 테스트 완료. **아직 안 함**: 롤백(`POST /api/stock/transactions/{id}/rollback`), 조회 API 3종(`GET /api/stock/transactions`, `/transactions/{id}`, `/low-stock`), 손익계산(`GET /api/stock/profit-loss`). 다음 세션은 **롤백부터** 이어서 진행.

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
- **catch-all이 프레임워크 예외까지 삼켜버리는 문제**: `AuthorizationDeniedException`(403), `HttpMessageNotReadableException`(요청 바디 파싱 실패, 400), `HttpRequestMethodNotSupportedException`(잘못된 HTTP 메서드/경로, 405)이 전부 catch-all에 걸려 500으로 잘못 나갔던 문제. 셋 다 전용 핸들러 추가해서 해결 완료 — **새로운 프레임워크 예외를 마주치면 일단 catch-all(500)에 걸리는지 의심하고, 맞는 상태코드로 전용 핸들러를 추가하는 패턴을 계속 적용할 것** (stock-transaction 단계에서도 비슷한 케이스 나올 수 있음).
- **응답 DTO 패턴**: Category 생성/수정 응답을 처음엔 엔티티(`Category`) 그대로 반환했다가, 일관성을 위해 `CategoryResponse`(record, `from(Category)` 정적 팩토리) 로 리팩토링함. Product/StockTransaction도 같은 패턴(엔티티 직접 반환 금지, 응답 DTO 경유) 유지할 것.
- **JPA dirty checking vs `save()`**: 같은 트랜잭션 안에서 `findById()`로 가져온 영속 상태 엔티티는 필드만 바꿔도 트랜잭션 커밋 시 자동 UPDATE됨(`save()` 호출 불필요, 호출해도 무해함 — 이미 관리 중인 엔티티라 `merge()`가 그대로 반환만 함). 이 원칙은 detached 엔티티(다른 트랜잭션에서 가져온 경우)에는 적용 안 되니 주의.
- **역할(role) 변경 후 반드시 재로그인 필요**: role은 JWT 클레임에 박혀서 발급되므로, DB에서 role을 바꿔도 기존 토큰엔 반영 안 됨(API 명세서에 명시된 내용). ADMIN/STAFF 권한 테스트 시 재로그인해서 새 토큰 받아야 함 — 계정 2개 없어도 계정 1개로 role 토글하면서 양쪽 다 테스트 가능.
- **Windows 콘솔 한글 로그 깨짐**: `gradlew bootRun` 콘솔에서 한글 로그 메시지가 깨져 보임(인코딩 문제, cp949 vs UTF-8 추정). 기능적 문제는 아니고 가독성 문제라 우선순위 낮음 — 필요시 나중에 콘솔 인코딩 설정으로 해결.
- **`Specification.where(null)`이 이 프로젝트의 Spring Data JPA 버전에서 예전과 다르게 동작함**: 컴파일 시 `where()`가 오버로드 모호성 에러를 내고(캐스팅으로 해결), 런타임엔 아무 필터 조건도 안 붙었을 때 `Specification.where(null)`이 실제로 `null`을 반환해서 `IllegalArgumentException: Specification must not be null` 발생. **`(root, query, cb) -> cb.conjunction()`(항상 참인 조건)로 시작하는 방식으로 우회** — 버전 의존적인 동작이라 이 방식이 더 안전함. Spring Data JPA 버전이 이례적으로 최신이라(Spring Boot 4.x) 공식 문서/예제와 동작이 다를 수 있다는 점 계속 염두에 둘 것.
- **Product 생성 시 `costPrice`/`currentStock`은 요청으로 안 받고 생성자 내부에서 하드코딩(`BigDecimal.ZERO`, `0`)으로 초기화.** Category/User 때처럼 Entity에 직접 생성자 만드는 패턴 유지. `update()` 메서드는 API 명세서상 수정 가능한 4개 필드(`name`, `sellingPrice`, `minStockLevel`, `categoryId`)만 받고 `sku`/`unit`/`costPrice`/`currentStock`은 파라미터로도 안 받음(애초에 불가능하게 설계).
- **말단 카테고리 검증(`CategoryService.validateLeaf`)은 생성 시점뿐 아니라 수정(`categoryId` 변경) 시점에도 적용.** API 명세서엔 생성 시점만 명시돼 있었지만, "상품은 말단 카테고리에만 등록"이라는 불변조건은 항상 유지돼야 한다고 판단해서 update()에도 추가함 (명세서에 없는 빈틈을 자체 판단으로 메운 사례).
- **검색 결과 0건은 404가 아니라 200 + `content: []`가 정답.** 에러 상황이 아니라 정상적인 "결과 없음" 상태.
- **`lowStockOnly` 필터 테스트 시 주의**: 입고(IN) API가 아직 없어서(다음 브랜치) 모든 상품의 `currentStock`이 항상 0. `minStockLevel >= 0`인 모든 상품이 논리적으로 "재고부족" 조건(`currentStock <= minStockLevel`)을 만족하므로, 지금 단계에선 `lowStockOnly=true`가 사실상 전체 조회와 똑같이 보일 수 있음 — 버그 아님, 재고 입고 기능 생긴 뒤에 의미 있는 테스트 가능.
- **삭제 충돌은 항상 `DeleteConflictException`(409), 검증 실패는 `ValidationException`(400)** — 한 번 헷갈려서 삭제 충돌에 `ValidationException`을 썼다가 고친 적 있음. "이미 존재하는 데이터 때문에 삭제 불가" = 409, "요청 자체의 값이 비즈니스 규칙에 안 맞음" = 400으로 구분 기준 명확히 할 것.
- **`consumeType`(DISCARD/INTERNAL_USE/SAMPLE)은 API 명세서엔 있지만 DB 테이블 설계서엔 컬럼이 없던 빈틈.** `TransactionType`(IN/OUT/CONSUME/ADJUSTMENT)에 세부 타입을 합치지 않고, `stock_transactions.consume_type` 별도 컬럼(nullable, CHECK 제약)으로 추가함 — `type`은 "재고 증감 방향/손익 공식이 달라지는 진짜 분기 기준", `consumeType`은 "CONSUME 안에서의 사유 세분류"로 레벨이 다르다고 판단. `type`을 6개로 늘렸으면 재고 차감/롤백 등 기존 로직의 `type == CONSUME` 체크가 전부 3중 분기로 바뀌어야 했을 것.
- **`Product` 엔티티가 자기 자신의 재고 불변조건을 스스로 지키는 패턴 확립**: `increaseStock()`(가중평균 계산), `decreaseStock()`(재고부족 체크), `adjustStock()`(실사수량으로 직접 세팅) 전부 Product 안에서 처리. OUT과 CONSUME은 "재고 차감"이라는 동일 연산이라 `decreaseStock()`을 그대로 공유(처음엔 `consumeStock()`을 따로 만들었다가 중복이라 삭제).
- **가중평균/조정 계산 시 "필드를 먼저 바꾸면 이전 값을 잃어버리는" 패턴의 버그가 반복적으로 나옴**: `increaseStock()`, `stockAdjustment()` 둘 다 처음엔 `currentStock`을 먼저 갱신해버려서 계산식에 필요한 "갱신 전 값"이 사라지는 실수를 했음. **필드를 바꾸는 계산 로직을 짤 때는 항상 "이전 값이 이후에도 필요한가"부터 확인하고, 필요하면 변수로 먼저 저장해둘 것** — 이 프로젝트 전반에 반복 적용될 수 있는 교훈.
- **ADJUSTMENT의 `costPriceSnapshot`은 `null`** — 손익 계산 제외 대상이라 굳이 현재 `costPrice`를 스냅샷할 이유가 없음. IN/OUT/CONSUME만 의미 있는 값을 가짐.
- **`StockTransaction` 생성자가 타입 4종 전부를 하나로 처리**: `(productId, userId, quantity, unitPrice, costPriceSnapshot, reason, type, status, consumeType)` 9개 파라미터, 타입별로 안 쓰는 값은 호출부에서 `null` 전달. 생성자 자체는 "받은 값 그대로 필드에 대입"만 하고 계산/분기는 전혀 모름 — 계산은 전부 `Product`/`StockTransactionService`가 책임짐.
- **JWT의 principal(문자열 userId)은 `SecurityContextHolder.getContext().getAuthentication().getName()`으로 꺼냄.** `JwtAuthenticationFilter`가 `UsernamePasswordAuthenticationToken(userId, ...)`으로 principal을 문자열로 넣어뒀기 때문. Controller마다 반복되므로 `StockTransactionController`에 `private getCurrentUserId()` 헬퍼로 통합.
- **`@PreAuthorize`에서 여러 role 허용은 `hasAnyRole('A', 'B')`, 하나만 허용은 `hasRole('A')`.** `hasRole('A','B')`처럼 다중 인자를 넣는 건 잘못된 문법 — SpEL이라 컴파일 시점엔 안 걸리고 런타임에만 에러가 나서 주의 필요.

---

## 4. AI(Claude) 활용 방식

작업순서 문서의 기준(핵심 학습 포인트는 직접 작성, 보일러플레이트는 AI 생성) 그대로 따르는 중. Entity/Repository/Security/Service/Controller 전부 사용자가 직접 작성, Claude는 리뷰(버그/설계 이슈 지적) + 개념 설명 역할만 수행.

**중요 — 예시 코드 주는 방식**: 처음엔 "예시"라면서 완성된 실행 가능 코드를 통째로 줬는데, 이러면 그냥 복사·붙여넣기가 되어버려서 학습 효과가 없다는 피드백을 받음 (2026-09-23). 그 이후로는 **메서드 시그니처 + 주석 힌트만 주고 실제 구현 로직은 직접 채우게 하는 방식**으로 전환함 (예: `CategoryController`의 POST/PUT/DELETE는 시그니처와 힌트만 주고 본문은 직접 작성하게 함). 새 세션에서도 이 방식 유지할 것 — 완성 코드를 바로 주지 말고 뼈대만.

---

## 5. 다음 단계

`feature/stock-transaction` 브랜치 계속 진행 중 (아직 PR 안 올림). IN/OUT/CONSUME/ADJUSTMENT는 구현+테스트 완료. **다음 세션에서 이어서 할 것, 순서대로**:

1. **롤백** `POST /api/stock/transactions/{id}/rollback` — 상쇄 트랜잭션 생성, 원본 `status=CANCELED` 마킹. 체크할 것 3가지: 원본이 `ADJUSTMENT`면 `RollbackNotAllowedException`, 이미 `CANCELED`면 `AlreadyCanceledException`, 롤백 적용 시 재고가 음수 되면 `RollbackConflictException`. 이 예외 3개는 아직 안 만듦 — `BusinessException` 상속 패턴 그대로.
2. **조회 3종**: `GET /api/stock/transactions`(productId/type/status/날짜 필터 + 페이징), `GET /api/stock/transactions/{id}`, `GET /api/stock/low-stock`(이미 `ProductService.search`에 `lowStockOnly` 있으니 재사용 검토)
3. **손익계산** `GET /api/stock/profit-loss` — `status='ACTIVE'`인 거래만 집계(CANCELED 원본·롤백 트랜잭션 자체는 제외), OUT은 `(unitPrice-costPriceSnapshot)×quantity`, CONSUME은 `costPriceSnapshot×quantity` 전액 손실, ADJUSTMENT는 집계 제외

**남겨진 테스트**: Product 삭제 409(DELETE_CONFLICT) — 이제 `stock_transactions`에 데이터가 있으니 다음 세션에서 확인 가능.
