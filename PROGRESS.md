# 진행 상황 & 컨텍스트 노트

**목적**: 다른 컴퓨터/새 세션에서 이어서 작업할 때 지금까지의 설계 결정과 트러블슈팅 이력을 빠르게 파악하기 위한 문서.
**최종 갱신**: 2026-09-30

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

**다음**: PR 올려서 `dev`에 merge → 작업순서 문서 6단계(Next.js 프론트엔드) 착수 예정.

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

---

## 4. AI(Claude) 활용 방식

작업순서 문서의 기준(핵심 학습 포인트는 직접 작성, 보일러플레이트는 AI 생성) 그대로 따르는 중. Entity/Repository/Security/Service/Controller 전부 사용자가 직접 작성, Claude는 리뷰(버그/설계 이슈 지적) + 개념 설명 역할만 수행.

**중요 — 예시 코드 주는 방식**: 처음엔 "예시"라면서 완성된 실행 가능 코드를 통째로 줬는데, 이러면 그냥 복사·붙여넣기가 되어버려서 학습 효과가 없다는 피드백을 받음 (2026-09-23). 그 이후로는 **메서드 시그니처 + 주석 힌트만 주고 실제 구현 로직은 직접 채우게 하는 방식**으로 전환함 (예: `CategoryController`의 POST/PUT/DELETE는 시그니처와 힌트만 주고 본문은 직접 작성하게 함). 새 세션에서도 이 방식 유지할 것 — 완성 코드를 바로 주지 말고 뼈대만.

---

## 5. 다음 단계

`feature/stock-transaction` 브랜치 기능/테스트 전부 완료, **PR만 남음**. 이 브랜치가 merge되면 작업순서 문서의 백엔드 코어(1~5단계)가 전부 끝나는 것 — 다음 세션 목표는:

1. `feature/stock-transaction` PR 생성 → 셀프 리뷰 → `dev` merge
2. 작업순서 문서 6단계 — **Next.js 프론트엔드** 착수 (레포구성_브랜치전략 문서 기준 `inventory-frontend` 별도 레포, `feature/frontend-minimal` 브랜치부터)
3. 프론트엔드 완성 후 → 챗봇 명세서 작성 및 구현 (Gemini function calling, RAG)
