# 진행 상황 & 컨텍스트 노트

**목적**: 다른 컴퓨터/새 세션에서 이어서 작업할 때 지금까지의 설계 결정과 트러블슈팅 이력을 빠르게 파악하기 위한 문서.
**최종 갱신**: 2026-10-07 (프론트엔드 화면 9개 완료 → 작업순서 6단계 완료, 다음 단계·백로그 정리)

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
7. `feature/stock-transaction` — IN/OUT/CONSUME/ADJUSTMENT/롤백, 조회 3종, 손익계산까지 전부 구현+테스트 완료. **백엔드 코어(작업순서 문서 1~5단계) 전부 완료.**
8. `feature/frontend-integration` (PR #6) — 프론트엔드 연결 전 백엔드 선작업 1차. CORS 설정, 구글 로그인 콜백을 JSON 응답 대신 HttpOnly 쿠키+리다이렉트 방식으로 전환, `JwtAuthenticationFilter`가 쿠키에서도 토큰을 읽도록 수정.
9. `feature/auth-me-logout` (PR #7) — 프론트엔드 선작업 2차. `AuthController`(`GET /api/auth/me`, `POST /api/auth/logout`) 신설, 인증 안 된 API 요청에 401 JSON 응답(커스텀 `AuthenticationEntryPoint`), **세션 기반 인증 정보 자동 복원 차단**(`RequestAttributeSecurityContextRepository`, 아래 3번 참고).
10. `feature/springdoc` (PR #8) — springdoc 3.x(Swagger UI, `/v3/api-docs`) 도입, 컨트롤러 4개에 `@Tag`/`@Operation` 추가, prod 프로필에서 문서 비활성화(`application-prod.yaml`).

11. `feature/page-response` (PR #9) — 스펙 확인 중 발견한 페이징 응답 형식 불일치 수정. `PageResponse<T>` 도입(명세서 1장 형식), 페이징 엔드포인트 3곳이 이를 반환, `Pageable`에 `@ParameterObject` 추가.
12. `feature/remove-leaf-rule` (PR #10) — "상품은 말단 카테고리에만 등록" 규칙 제거. `validateLeaf()` → `validateExists()`(존재 확인 404만 유지). 결정 배경은 아래 3번 참고.

13. `feature/input-validation` (PR #11) — 입력값이 DB 제약을 넘거나 중복일 때 500이 나던 문제를 4xx로 처리. `@Size`(카테고리/상품 name, sku), `@Digits`(금액), SKU 중복 409, 범용 `ConflictException` 추가.
14. `feature/adjustment-reason-notblank` (PR #12) — `StockAdjustmentRequest.reason`의 `@NotNull`을 `@NotBlank`로 변경(빈 문자열/공백만 있는 사유 차단, 명세서 "reason 필수"와 일치).

15. `feature/transaction-product-info` (PR #13) — 프론트 거래 목록 화면에서 필요해진 `productName`, `productUnit`을 모든 거래 응답(`StockTransactionResponse`)에 추가. `from(StockTransaction, Product)`로 시그니처 변경, `search()`는 상품을 `findAllById`로 한 번에 조회해 N+1 방지.
16. `feature/profit-loss-net-profit` (PR #14) — 손익 응답에 `netProfit`(= `totalProfit − consumeLoss`)과 상품별 `net`(= `profit − loss`) 추가, `byProduct`를 `net` 내림차순(동률은 `productId` 오름차순)으로 정렬, `getProfitLoss()`의 상품 조회 N+1을 `findAllById`로 수정. 잘못된 쿼리 파라미터 타입(날짜/enum/숫자)이 500으로 나가던 문제를 400으로 처리(`MethodArgumentTypeMismatchException` 핸들러). 결정 배경은 아래 3번 참고.

**진행 중**: 없음.

**프론트엔드**: 2026-10-07 기준 설계서 5장의 화면 9개 전부 완료(`inventory-frontend` PR #1~#11) → **작업순서 문서 6단계 완료**. 프론트 쪽 결정·교훈은 `문서/재고관리_챗봇_프론트엔드설계서.md` 11장에 정리.

**다음**: 작업순서 7단계 — 챗봇 명세서 작성부터 (아래 5장).

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
  - **→ 2026-10-05 말단 전용 규칙 자체를 제거.** 프론트 카테고리 화면 작업 중 두 가지를 발견:
    1. 빈틈: `CategoryService.create()`가 부모 존재 여부만 확인해서, 상품이 있는 말단 아래에 하위를 추가하면 그 상품들이 말단이 아닌 카테고리에 남음 (불변조건이 카테고리 쪽에서 우회됨)
    2. 빈틈을 막으면(409 거절) 반대로 너무 경직됨 — 상품이 있는 카테고리를 하위로 나누려면 상품을 임시 카테고리로 옮겼다가 되돌려야 함
  - 대안 비교: ① 409 거절(경직) ② 첫 하위 추가 시 기존 상품을 새 하위로 자동 이동 ③ 규칙 제거 → **③ 채택**. 상위 카테고리 조회 시 하위 상품까지 `IN`으로 포함하는 재귀 조회(`getDescendantCategoryIds`)가 이미 있어 조회 기능 손실이 없고, 이 프로젝트 규모에선 "분류가 흐트러질 수 있다"는 단점보다 단순함·유연함이 더 크다고 판단.
  - 변경: `validateLeaf()`에서 "하위 카테고리 존재 시 400" 부분만 제거하고, **카테고리 존재 확인(없으면 404)은 남김** — 이 메서드가 존재 확인까지 겸하고 있어서 통째로 지우면 없는 `categoryId`가 FK 위반(DB 에러 → 500)으로 새어 나감. 역할이 바뀌므로 메서드 이름도 존재 확인에 맞게 변경. 카테고리 **삭제** 규칙(하위 또는 소속 상품 있으면 409)은 유지. 관련 문서(API 명세서 3·4·6장, 프론트엔드설계서 6.5) 갱신 완료.
  - 교훈: 불변조건을 하나 추가하면 그 조건을 깨뜨릴 수 있는 **다른 경로**(여기선 카테고리 생성)까지 같이 막아야 하고, 막았을 때 운영이 가능한지도 같이 따져봐야 함.
- **검색 결과 0건은 404가 아니라 200 + `content: []`가 정답.** 에러 상황이 아니라 정상적인 "결과 없음" 상태.
- **`lowStockOnly` 필터 테스트 시 주의**: 입고(IN) API가 아직 없어서(다음 브랜치) 모든 상품의 `currentStock`이 항상 0. `minStockLevel >= 0`인 모든 상품이 논리적으로 "재고부족" 조건(`currentStock <= minStockLevel`)을 만족하므로, 지금 단계에선 `lowStockOnly=true`가 사실상 전체 조회와 똑같이 보일 수 있음 — 버그 아님, 재고 입고 기능 생긴 뒤에 의미 있는 테스트 가능.
- **삭제 충돌은 항상 `DeleteConflictException`(409), 검증 실패는 `ValidationException`(400)** — 한 번 헷갈려서 삭제 충돌에 `ValidationException`을 썼다가 고친 적 있음. "이미 존재하는 데이터 때문에 삭제 불가" = 409, "요청 자체의 값이 비즈니스 규칙에 안 맞음" = 400으로 구분 기준 명확히 할 것.
- **`consumeType`(DISCARD/INTERNAL_USE/SAMPLE)은 API 명세서엔 있지만 DB 테이블 설계서엔 컬럼이 없던 빈틈.** `TransactionType`(IN/OUT/CONSUME/ADJUSTMENT)에 세부 타입을 합치지 않고, `stock_transactions.consume_type` 별도 컬럼(nullable, CHECK 제약)으로 추가함 — `type`은 "재고 증감 방향/손익 공식이 달라지는 진짜 분기 기준", `consumeType`은 "CONSUME 안에서의 사유 세분류"로 레벨이 다르다고 판단. `type`을 6개로 늘렸으면 재고 차감/롤백 등 기존 로직의 `type == CONSUME` 체크가 전부 3중 분기로 바뀌어야 했을 것.
- **`Product` 엔티티가 자기 자신의 재고 불변조건을 스스로 지키는 패턴 확립**: `increaseStock()`(가중평균 계산), `decreaseStock()`(재고부족 체크), `adjustStock()`(실사수량으로 직접 세팅) 전부 Product 안에서 처리. OUT과 CONSUME은 "재고 차감"이라는 동일 연산이라 `decreaseStock()`을 그대로 공유(처음엔 `consumeStock()`을 따로 만들었다가 중복이라 삭제).
- **가중평균/조정 계산 시 "필드를 먼저 바꾸면 이전 값을 잃어버리는" 패턴의 버그가 반복적으로 나옴**: `increaseStock()`, `stockAdjustment()` 둘 다 처음엔 `currentStock`을 먼저 갱신해버려서 계산식에 필요한 "갱신 전 값"이 사라지는 실수를 했음. **필드를 바꾸는 계산 로직을 짤 때는 항상 "이전 값이 이후에도 필요한가"부터 확인하고, 필요하면 변수로 먼저 저장해둘 것** — 이 프로젝트 전반에 반복 적용될 수 있는 교훈.
- **ADJUSTMENT의 `costPriceSnapshot`은 `null`** — 손익 계산 제외 대상이라 굳이 현재 `costPrice`를 스냅샷할 이유가 없음. IN/OUT/CONSUME만 의미 있는 값을 가짐.
- **`StockTransaction` 생성자가 타입 4종 + 롤백까지 전부 하나로 처리**: `(productId, userId, quantity, unitPrice, costPriceSnapshot, reason, type, status, consumeType, reversalOfId)` 10개 파라미터, 안 쓰는 값은 호출부에서 `null`. `reversalOfId`가 필요해졌을 때도 별도 생성자를 새로 만들지 않고 기존 생성자에 파라미터를 추가하는 방식 유지(`consumeType` 추가 때와 동일 패턴) — "StockTransaction을 만드는 방법은 항상 하나"를 지킴.
- **롤백 시 `Product.costPrice` 완벽 복원은 불가능, 근사 역산으로 타협**: `decreaseStock()`만 쓰면 재고 수량은 돌아와도 매입단가는 입고 반영된 채로 안 돌아옴. `reverseIncreaseStock()`을 새로 만들어 가중평균 공식을 역산(`(현재재고×현재단가 − 입고수량×입고단가) / (현재재고−입고수량)`)해서 근사 복원. **이 역산은 그 입고 이후 다른 입고가 없었을 때만 정확** — 완벽하게 하려면 입고 lot별 추적(FIFO/LIFO)이 필요한데 범위 밖으로 판단. 재고가 정확히 0이 되는 롤백은 0-나누기 위험 있어 별도 분기 처리함.
- **"롤백의 롤백" 방지**: 롤백으로 생성된 상쇄 트랜잭션(`reversalOfId != null`)은 다시 롤백 불가 처리. 이 체크를 처음엔 타입 분기(`if type==IN ... else ...`) 안쪽에 넣는 실수를 했는데, 그러면 IN 타입 분기가 이 체크를 아예 건너뛰어서 절반만 막히는 버그가 됐음 — **"이 거래가 롤백 가능한 상태인가"를 따지는 검증은 전부 1단계(조회 직후) 한곳에 모아야 함**, 타입별 처리 로직과 섞으면 이런 누락이 생기기 쉬움.
- **`StockTransactionRepository`에 `findByIdForUpdate`를 이름만 따라 만들었다가 서버 기동 실패할 뻔함**: `@Lock`/`@Query` 없이 이름만 지으면 Spring Data가 "IdForUpdate"라는 존재하지 않는 필드로 해석하려다 실패 — 컴파일은 통과하고 **런타임(기동 시)에만 에러가 남**. `StockTransaction` 자체는 잠글 필요가 없어서(재고를 바꾸는 건 `Product`뿐) 평범한 `findById`로 대체.
- **롤백 API의 거래 id는 URL 경로(`/api/stock/transactions/{id}/rollback`)로만 받고 요청 바디엔 `reason`만.** 처음엔 `RollbackRequest`에 `id` 필드를 넣고 `@PathVariable`도 안 썼다가, "URL에 이미 있는 정보를 바디에 중복 요구"하는 설계 오류로 지적받고 수정함.
- **JWT의 principal(문자열 userId)은 `SecurityContextHolder.getContext().getAuthentication().getName()`으로 꺼냄.** `JwtAuthenticationFilter`가 `UsernamePasswordAuthenticationToken(userId, ...)`으로 principal을 문자열로 넣어뒀기 때문. Controller마다 반복되므로 `StockTransactionController`에 `private getCurrentUserId()` 헬퍼로 통합.
- **`@PreAuthorize`에서 여러 role 허용은 `hasAnyRole('A', 'B')`, 하나만 허용은 `hasRole('A')`.** `hasRole('A','B')`처럼 다중 인자를 넣는 건 잘못된 문법 — SpEL이라 컴파일 시점엔 안 걸리고 런타임에만 에러가 나서 주의 필요.
- **`reversalOfId IS NULL` 필터(`excludeReversal()`)는 손익계산 전용 쿼리에만 적용, 일반 거래 목록 조회(`GET /api/stock/transactions`)엔 적용 안 함.** 처음에 공용 `search()` 메서드 안에 넣었다가, 그러면 일반 이력 조회에서도 롤백 트랜잭션이 안 보이게 되어버려서(이력 추적 API의 목적과 어긋남) 분리함 — "손익 집계"와 "이력 조회"는 같은 테이블을 보지만 요구사항이 다르다는 걸 놓치기 쉬움.
- **프레임워크 예외 처리 패턴이 정착됨**: 테스트하다 500 뜨면 → 콘솔 로그에서 실제 예외 클래스 확인 → `GlobalExceptionHandler`에 `@ExceptionHandler(그예외.class)`로 전용 핸들러 추가 → 적절한 상태코드/메시지 매핑. 이번 브랜치에서 `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException`, `MissingServletRequestParameterException` 세 개를 이 패턴으로 추가함. 예외 객체가 자체적으로 갖고 있는 정보(`e.getParameterName()` 등)를 활용하면 `e.getMessage()`(스프링 기본 영문 메시지)보다 깔끔한 에러 메시지를 만들 수 있음.
- **테스트 중 이상한 데이터를 발견하면, 먼저 "지금 코드로 재현되는 버그인지 vs 예전 테스트 데이터의 잔재인지"부터 구분할 것.** 손익계산에서 `costPriceSnapshot`이 null인 원본 OUT 거래 때문에 NPE가 났는데, 알고 보니 그 레코드는 몇 번의 버그 수정 이전(반복 테스트 중)에 만들어진 것이었음 — 현재 코드로는 재현 불가능한, DB에 남은 낡은 데이터였음. 장시간 반복 디버깅 세션 후엔 테스트 데이터 정리도 고려할 것.
- **`byProduct`(상품별 손익) 집계는 `Collectors.groupingBy(분류기준, Collectors.reducing(초기값, 변환함수, 합산함수))` 패턴 사용.** 단순 `groupingBy`는 `Map<Key, List<Entity>>`를 주지만, 여기선 `Map<Long, BigDecimal>`(그룹별 합계 하나)이 필요해서 downstream collector로 `reducing`을 조합함 — 이 프로젝트에서 가장 복잡한 스트림 사용 사례.
- **인증 토큰 전달 방식: HttpOnly 쿠키로 결정 (localStorage+Authorization 헤더 대신).** 이유는 XSS 공격 시 `localStorage`는 자바스크립트로 읽혀서 토큰이 털리지만, `HttpOnly` 쿠키는 JS가 아예 접근 불가. 대신 `JwtAuthenticationFilter`가 쿠키에서도 토큰을 읽도록 수정 필요했음(기존엔 `Authorization` 헤더만 봤음) — Authorization 헤더 체크를 우선시하고 없으면 쿠키로 폴백하는 구조라 Postman 테스트(헤더 방식)도 계속 호환됨.
- **`SameSite=None`은 반드시 `Secure=true`와 같이 써야 함 — 아니면 브라우저가 쿠키 저장 자체를 거부.** 로컬 개발(http)에선 `Secure=true`를 못 쓰므로, 대신 `SameSite=Lax`를 사용함 — `localhost:3000`과 `localhost:8080`은 포트만 다르고 도메인이 같아 브라우저의 "same-site" 판정상 같은 사이트로 취급되므로 `Lax`로 충분함. **배포해서 프론트/백엔드가 완전히 다른 도메인이 되면 그때 `None`+`Secure(true)`+HTTPS 조합으로 바꿔야 함.**
- **CORS는 cross-origin `fetch`/XHR에만 적용되고, 전체 페이지 리다이렉트(브라우저 주소창 이동, OAuth 리다이렉트 등)에는 적용 안 됨.** 그래서 CORS 설정 자체는 실제 Next.js 프론트엔드가 `fetch(..., {credentials:'include'})`로 호출해봐야 제대로 검증됨 — 지금은 설정만 해두고 실전 검증은 프론트엔드 붙인 뒤로 미룸.
- **~~구글 로그인 시 생기는 `JSESSIONID` 쿠키는 우리 앱의 로그인 상태와 무관~~ → 이 판단은 틀렸었음, 아래 항목으로 정정.**
- **🔴 중요 버그: 로그아웃해도 로그인 상태가 풀리지 않았던 문제 (`JSESSIONID`가 실제로 로그인 상태를 유지시키고 있었음).**
  - **증상**: `/api/auth/logout`으로 `accessToken` 쿠키를 지웠는데도, 이후 API 요청이 계속 인증된 것처럼 성공함. 브라우저 쿠키엔 `JSESSIONID`만 남아있었고 `accessToken`은 확실히 없었음.
  - **원인**: `SessionCreationPolicy.IF_REQUIRED`(OAuth2 핸드셰이크 때문에 필요했던 설정) 상태에서는, 스프링 시큐리티가 **기본적으로 로그인 성공 시 `SecurityContext`(인증 정보)를 HTTP 세션에도 자동 저장**해버림. 그 뒤로는 `JSESSIONID`만 있어도 서버가 세션에서 인증 정보를 복원해버려서, 우리가 만든 `JwtAuthenticationFilter`(쿠키/헤더의 JWT 검사)를 사실상 우회하는 셈이 됐음. 즉 인증 체크가 "JWT 필터"와 "스프링 기본 세션 복원" 두 갈래로 몰래 동작하고 있었음.
  - **해결**: `SecurityConfig`에 `.securityContext(sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))` 추가 — "인증 정보를 세션에 저장/복원하지 말고, 매 요청마다 새로 검증해라"고 명시. OAuth2 로그인 핸드셰이크용 세션 저장(`HttpSessionOAuth2AuthorizationRequestRepository`)은 완전히 별개의 메커니즘이라 이 변경과 충돌 없음.
  - **교훈**: `SessionCreationPolicy`를 `STATELESS`가 아닌 걸로 설정하면, 의도치 않게 스프링의 "세션 기반 인증 정보 자동 영속화" 기본 동작이 같이 켜진다는 걸 몰랐음. JWT만으로 완전히 stateless한 인증을 하려면 `SecurityContextRepository`도 명시적으로 신경 써야 함 — 세션 생성 정책(`SessionCreationPolicy`)과 인증정보 영속화 방식(`SecurityContextRepository`)은 별개의 설정이라는 걸 기억할 것.
- **`frontend.url`처럼 환경마다 달라지는(비밀은 아닌) 값은 `application.yaml`에 기본값을 두고 배포 시 환경변수로 덮어쓰는 방식 채택.** `application-local.yaml`은 "비밀값 전용"이 아니라 "환경별 설정 전용"이라는 더 넓은 개념이지만, 이 프로젝트처럼 혼자 하는 경우 매번 새로 설정하는 번거로움을 줄이는 쪽을 택함.
- **YAML 들여쓰기 실수가 이번엔 반대 방향으로 또 발생**: `frontend:`를 `spring:` **안에** 잘못 넣어서 실제 경로가 `spring.frontend.url`이 되어버림 (`@Value("${frontend.url}")`는 최상위 경로를 찾아서 플레이스홀더 에러). 예전엔 반대로 `security:`를 `spring:` **밖에** 둬서 문제였음 — 둘 다 같은 원인(YAML 들여쓰기 레벨)이니 설정 추가할 때마다 들여쓰기를 한 번 더 확인하는 습관이 필요함.
- **springdoc 버전: Spring Boot 4.x는 springdoc 3.x 라인 필요** (`springdoc-openapi-starter-webmvc-ui:3.0.x`). Boot 3.x용 2.x를 쓰면 기동 에러나 Swagger UI 404. `@Tag`의 import는 `io.swagger.v3.oas.annotations.tags.Tag`(중간에 `tags`), `@Operation`은 `io.swagger.v3.oas.annotations.Operation`.
- **Swagger UI 접근 규칙**: `SecurityConfig`의 `authorizeHttpRequests`에 `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`를 `permitAll()`로 추가해야 함(없으면 401 JSON). `anyRequest()`보다 위에 둘 것. "Try it out"은 인증이 HttpOnly 쿠키라서, 같은 브라우저에서 먼저 구글 로그인해두면 같은 출처라 쿠키가 자동으로 실려서 동작함.
- **운영 배포 시 Swagger 비활성화**: `application-prod.yaml`에 `springdoc.api-docs.enabled=false` + `springdoc.swagger-ui.enabled=false` **둘 다**(swagger-ui만 끄면 `/v3/api-docs` JSON이 노출됨). 운영 서버에 `SPRING_PROFILES_ACTIVE=prod` 환경변수 설정(환경변수가 `application.yaml`의 `active: local`보다 우선). 비활성화되면 `permitAll` 규칙이 남아도 핸들러가 없어 404. 운영에선 `application-local.yaml`이 없으므로 DB/OAuth/JWT 비밀값은 환경변수로 따로 주입해야 함.
- **🔴 페이징 응답 형식이 API 명세서와 달랐던 문제**: 컨트롤러가 `Page<T>`를 그대로 반환해서 Spring 내부 구현(`PageImpl`)이 JSON으로 나감 → 현재 페이지가 명세의 `page`가 아니라 `number`로 나오고, `first/last/empty/numberOfElements/sort/pageable`이 같이 내려감(서버 로그에 `Serializing PageImpl instances as-is is not supported` 경고, Spring도 이 형태의 안정성을 보장 안 함). springdoc 스펙을 열어보고서야 발견했는데, 그 전에 응답을 눈으로 보고도 명세서와 대조하지 않고 넘어갔었음 — **새 API를 만들면 응답 JSON을 명세서 예시와 직접 대조하는 습관 필요.**
  - **해결**: `PageResponse<T>`(`content, page, size, totalElements, totalPages`) record + `from(Page<T>)` 정적 팩토리. 서비스는 `Page`를 그대로 반환하고 **컨트롤러에서 변환**(수정 범위를 컨트롤러로 한정). `Page.getNumber()` → `page`로 매핑.
  - 대안이었던 `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`는 공식 지원 형식이지만 `page`가 `{size, number, totalElements, totalPages}`로 **중첩**되어 명세서의 평평한 형식과 달라서 채택 안 함.
- **🔴 DB 제약을 넘는 입력이 전부 500으로 나가던 문제 → 요청 단계에서 4xx로 차단.** DB 제약 위반은 `DataIntegrityViolationException`으로 올라와 `GlobalExceptionHandler`의 catch-all(`Exception.class`)에서 500이 됨. 사용자 입력 실수인데 서버 장애처럼 보이고 프론트가 원인을 알 수 없음. 프론트에서 카테고리 이름 101자를 보내다 발견했고, 점검해보니 요청 DTO 전체에 길이 검증이 하나도 없었음.
  - **방침: DB 컬럼 제약(길이·정밀도·UNIQUE)에 걸릴 수 있는 입력은 요청 DTO나 서비스에서 먼저 막는다.** 새 DTO/필드를 만들 때 DB 설계서의 컬럼 타입을 같이 확인할 것.
  - 길이: `@Size` — 카테고리 `name` 100, 상품 `name` 200, `sku` 50. `description`/`reason`은 `TEXT`라 제한 없음.
  - 금액: `@Digits(integer = 10, fraction = 2)` — `NUMERIC(12,2)` 기준. 상품 `sellingPrice`, 입고/출고 `unitPrice`. `message`를 반드시 한글로 지정할 것 — 안 쓰면 영문 기본 문구가 `GlobalExceptionHandler`를 통해 응답에 그대로 나감. 소수 셋째 자리 이상은 이전엔 DB가 조용히 반올림했으나 이제 400.
  - SKU 중복: `ProductRepository.existsBySku` + `ProductService.create()`에서 저장 전 확인 → 409. **한계**: 확인~저장 사이 동시 등록은 DB 유니크 제약에 걸려 여전히 500. 필요해지면 `DataIntegrityViolationException` 핸들러(409)를 안전망으로 추가.
- **`ConflictException`(409, 코드 `CONFLICT`) 사용 기준**: "요청 형식은 유효하나 현재 데이터와 충돌"할 때 범용으로 사용 (SKU 중복 등). API 명세서의 400/409 구분(400=요청 자체가 잘못, 409=상태 충돌)을 따름. `ValidationException`(400)은 값 자체가 규칙에 안 맞을 때, `DeleteConflictException`은 삭제 조건 충돌 전용(코드가 `DELETE_CONFLICT`라 다른 상황에 쓰면 의미가 틀림).
- **거래 응답에 상품명/단위 포함 (`productName`, `productUnit`) — 프론트가 목록 행마다 상품 조회 API를 또 부르는 구조를 피하려고 백엔드 응답에 합침.** `StockTransaction`은 `productId`(순수 FK)만 갖고 있어서 응답 DTO 변환 시 `Product`가 필요해짐 → `StockTransactionResponse.from(StockTransaction, Product)`로 변경.
  - **`search()`의 N+1 방지**: 거래 N건마다 `productRepository.findById`를 부르면 쿼리가 N+1번 나감. 페이지의 `productId`를 `Set`으로 모아 `findAllById(ids)` 한 번(`WHERE id IN (...)`)으로 조회 → `Map<Long, Product>`로 만들어 변환 시 `productMap.get(tx.getProductId())`로 꺼냄. 쿼리는 페이지 조회 + 상품 조회 + count로 고정.
  - **정확한 표현**: N+1은 "다른 테이블의 연관 데이터를 건건이 조회"할 때 생기는 일반적인 문제이고, 순수 FK 매핑이 원인이 아님(`@ManyToOne(LAZY)`여도 똑같이 생김). 순수 FK에서는 fetch join을 쓸 수 없어서 **해결책이 수동 배치 로딩(`findAllById` → `Map`)으로 달라질 뿐**.
  - 단건 조회(`searchById`)와 입고/출고/소비/조정/롤백은 이미 락을 잡아 가져온 `product`를 그대로 넘기므로 추가 쿼리 없음(`searchById`만 `findById` 1회 추가). 응답 DTO 필드가 늘었으니 API 명세서 5장 예시와 프론트 TS 타입도 같이 갱신.
- **손익 응답의 `totalProfit`은 "판매 이익"이고 소비 손실이 빠진 값 → 최종 이익은 서버가 `netProfit`으로 따로 내려줌.** 프론트 손익 화면 작업 중, `totalProfit`을 "총이익"으로 보여주면 폐기·샘플 같은 소비 손실이 커도 이익이 그대로인 것처럼 보인다는 점이 문제로 나옴.
  - 대안 비교: ① 프론트에서 `totalProfit − consumeLoss` 계산 ② `totalProfit` 의미 자체를 변경 ③ **`netProfit`/`net` 필드 추가 → ③ 채택.** ①은 프론트엔드설계서 6.3("금액 계산은 프론트에서 하지 않음")에 어긋나고, 챗봇이 같은 서비스 메서드로 답할 때 수치가 갈릴 위험이 있음(손익 공식은 서버 한 곳에만). ②는 API 명세서의 기존 정의(`totalProfit = 매출 − 원가`)와 하위 호환이 깨짐.
  - 기존 필드는 그대로 두고 필드만 추가해서 하위 호환 유지. `totalProfit` 라벨은 "판매 이익", `netProfit`은 "최종 이익"으로 프론트에 안내. DTO 주석도 "수익 − 손실"이라는 틀린 설명을 "판매 이익(소비 손실 제외)"으로 정정함.
  - `byProduct`는 `HashSet` 순회라 응답 순서가 매번 달라질 수 있었음 → `.sorted(Comparator.comparing(ByProduct::net).reversed().thenComparing(ByProduct::productId))`로 고정. `.reversed()`를 쓸 때는 람다가 아니라 **메서드 참조로 써야 타입 추론이 됨**. 정렬 기준은 API 명세서 5장에 명시.
  - `getProfitLoss()`도 거래 상품마다 `findById`를 부르던 N+1을 `findAllById` → `Map<Long, Product>`로 수정(위 "거래 응답에 상품명/단위" 항목과 같은 패턴). **고치는 중 `productMap`만 만들어 놓고 `.map` 안에서 `findById`를 그대로 두는 실수**가 있었음 — 쿼리가 오히려 하나 늘어난 채로 N+1이 남으므로, 배치 로딩을 적용한 뒤엔 SQL 로그로 실제 쿼리 수를 확인할 것.
- **🔴 잘못된 쿼리 파라미터 타입이 500으로 나가던 문제 → `MethodArgumentTypeMismatchException` 전용 핸들러(400 `VALIDATION_ERROR`) 추가.** 손익 API를 `startDate=2026-09-01`(날짜만)로 호출했더니 500. 이 예외는 `type=ABC`(enum), `productId=abc`(숫자)처럼 쿼리 파라미터 변환이 실패할 때 전부 발생하는데 전용 핸들러가 없어 catch-all(500)로 빠지고 있었음 (위 "프레임워크 예외 처리 패턴"을 또 적용한 사례). 메시지는 `e.getPropertyName()`으로 파라미터 이름을 넣음 — Spring 7.0.9에서 `getName()`과 같은 값을 반환함을 직접 확인.
  - **날짜 파라미터 형식 결정: `yyyy-MM-ddTHH:mm:ss`로 고정 (A안).** 컨트롤러의 `LocalDateTime` 파라미터는 `@DateTimeFormat` 없이도 ISO 날짜시간만 받음. 대안은 B) `@DateTimeFormat(iso = DATE_TIME)` 명시(동작 동일, springdoc에 `date-time`으로 더 분명히 표기) C) 날짜만 허용하고 종료일을 서버가 `23:59:59`로 보정(규칙이 늘어남). 프론트엔드설계서 6.4가 이미 "시작 `00:00:00`, 종료 `23:59:59`로 직접 조립해 전송"으로 정해 둬서 서버가 날짜만 받아줄 필요가 적다고 보고 A 채택. API 명세서 5장에 형식 명시, 1장 400 설명에 "타입/형식 오류" 추가.
  - 수동 테스트 시 날짜는 `startDate=2026-09-01T00:00:00&endDate=2026-09-30T23:59:59`처럼 시각까지 넣을 것. 로그인 안 된 상태에서는 400이 아니라 401이 나오는 게 정상.
- **`Pageable` 파라미터에는 `@ParameterObject`(`org.springdoc.core.annotations.ParameterObject`)**: 없으면 springdoc이 `pageable`이라는 필수 객체 파라미터 하나로 문서화해서, 스펙 기반 TS 타입이 `?pageable=...`을 보내는 것처럼 생성됨. 실제 API 동작은 원래 `page/size/sort`로 정상이고 **문서에만** 영향. 붙이면 `page`, `size`, `sort` 선택 파라미터 3개로 펼쳐짐.
- **springdoc 스펙의 알려진 한계 (TS 타입 생성 시 다룰 것)**: ① 응답 DTO(`ProductResponse` 등)는 `@NotNull` 같은 게 없어 `required` 목록이 비어서 전부 optional로 잡힘 → `openapi-typescript --properties-required-by-default`로 일괄 required 처리하되, 실제로 null일 수 있는 필드(`description`, `unitPrice`, `reversalOfId`, `canceledBy`, `canceledAt`, `consumeType` 등)는 타입에서 `| null`로 보정 필요. 백엔드에서 풀려면 non-null 필드마다 `@Schema(requiredMode = REQUIRED)`를 붙여야 해서 비용이 큼. ② `ResponseEntity`의 상태 코드는 springdoc이 몰라서 POST(201)/DELETE(204)도 스펙엔 200으로만 나옴 → 필요하면 `@Operation(responses = @ApiResponse(responseCode = "201"))`로 문서화. ③ 에러 응답(400/409 등)은 미문서화인데 공통 포맷(`ErrorResponse`) 하나라 프론트에서 타입 하나로 처리 가능.

---

## 4. AI(Claude) 활용 방식

작업순서 문서의 기준(핵심 학습 포인트는 직접 작성, 보일러플레이트는 AI 생성) 그대로 따르는 중. Entity/Repository/Security/Service/Controller 전부 사용자가 직접 작성, Claude는 리뷰(버그/설계 이슈 지적) + 개념 설명 역할만 수행.

**중요 — 예시 코드 주는 방식**: 처음엔 "예시"라면서 완성된 실행 가능 코드를 통째로 줬는데, 이러면 그냥 복사·붙여넣기가 되어버려서 학습 효과가 없다는 피드백을 받음 (2026-09-23). 그 이후로는 **메서드 시그니처 + 주석 힌트만 주고 실제 구현 로직은 직접 채우게 하는 방식**으로 전환함 (예: `CategoryController`의 POST/PUT/DELETE는 시그니처와 힌트만 주고 본문은 직접 작성하게 함). 새 세션에서도 이 방식 유지할 것 — 완성 코드를 바로 주지 말고 뼈대만.

---

## 5. 다음 단계

백엔드 `dev`는 PR #15까지, 프론트엔드 `dev`는 PR #11까지 merge 완료. **작업순서 1~6단계(백엔드 코어 + 프론트엔드) 전부 완료.**

### 5.1 다음 목표 — 작업순서 7단계: 챗봇
1. **챗봇 명세서 작성** (`문서/` 폴더, 다른 설계서와 같은 형식) — 코드보다 먼저
   - 라우팅 구조: 질문을 DB 조회(정형 데이터)로 보낼지, 매뉴얼 RAG(pgvector)로 보낼지
   - Gemini function calling에 노출할 tool 목록 — 기존 Service 메서드 재사용이 전제(패키지구조설계서 2.5, 레포구성 1장: 챗봇은 같은 Spring 서버, HTTP 왕복 없이 Service 직접 호출)
   - 권한: tool 실행도 로그인 사용자 role 기준(ADMIN 전용 기능을 챗봇으로 우회하지 않게)
   - 기술 선택: Spring AI vs LangChain4j, `manual_embeddings`(vector 768) 차원과 임베딩 모델
2. 백엔드 `feature/chatbot-routing` 구현
3. 프론트 오른쪽 챗봇 패널(`components/layout/chat-panel.tsx`, 현재 자리만 있음)에 대화 UI 연결

### 5.2 백로그 (프론트엔드 작업 중 발견, 우선순위 낮음)
- **409 메시지를 사용자용 문구로 정리** — 상품 id·단위 없는 숫자가 섞여 있음. 예: 롤백 `"재고 수량이 부족합니다: 9 (복구 수량: 2000, 재고 수량: 1300)"`, `"ADUSTMENT 거래내역은…"`(오타 포함), `"이미 취소된 거래내역입니다: 12"`. 명세서 1장 원칙: `message`는 그대로 사용자에게 보여줄 문구. 챗봇도 같은 메시지를 쓰게 되므로 7단계 전에 정리하면 좋음
- `ProductCreateRequest`/`ProductUpdateRequest.minStockLevel`에 `@PositiveOrZero` (프론트 zod가 막고 있지만 백엔드에도 필요)
- SKU 동시 등록 시 `existsBySku` 통과 후 UNIQUE 위반 → `DataIntegrityViolationException`이 catch-all 500 → 전용 409 핸들러
- `ConflictException` 코드를 `"CONFLICT"` 대신 구체화(예: `DUPLICATE_SKU`) — 프론트가 `error.code`로 필드별 에러 위치를 잡을 수 있게
- 재고조정 차이가 0이어도 거래가 기록됨 (프론트는 버튼 비활성화로 막는 중) → 백엔드에서도 거절할지 결정
- 테스트 데이터 정리: `TEST-`로 시작하는 상품과 그 거래(거래 이력이 있어 화면에서 삭제 불가 → DB에서 정리)
