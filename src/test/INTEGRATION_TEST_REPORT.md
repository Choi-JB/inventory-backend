# 통합 테스트 정리

**작성일**: 2026-10-20
**대상**: 인증 API, 카테고리·상품·재고 API
**방식**: `@SpringBootTest` + `MockMvc`. 프로필 `test`. DB는 H2(PostgreSQL 호환 모드), 테이블은 테스트마다 비움
**제외**: 구글 로그인, Gemini 답변 문장 비교. 벡터 스토어는 빈 구현으로 대체하고 pgvector 자동 설정은 끔

이 PC에는 Docker가 없어 Testcontainers PostgreSQL은 실행하지 못했다. HTTP, 보안, 서비스, JPA, 트랜잭션 롤백까지는 이 구성으로 확인했다.

---

## 1. 실행 결과 요약

| 테스트 클래스 | 케이스 수 | 실패 | 오류 | 스킵 | 소요 시간 |
|---------------|-----------|------|------|------|-----------|
| `AuthIntegrationTest` | 6 | 0 | 0 | 0 | 9.6s |
| `StockFlowIntegrationTest` | 5 | 0 | 0 | 0 | 1.3s |
| `TokenSecurityIntegrationTest` | 2 | 0 | 0 | 0 | 0.7s |
| `StockOutAtomicityIntegrationTest` | 1 | 0 | 0 | 0 | 2.8s |
| `ChatToolIntegrationTest` | 2 | 0 | 0 | 0 | 10.2s |
| **합계** | **16** | **0** | **0** | **0** | — |

**결과**: `BUILD SUCCESSFUL` — 전체 통과

### 재실행 명령

```powershell
.\gradlew.bat test --tests "com.example.inventory.integration.*"
```

HTML 리포트: `build/reports/tests/test/index.html`

---

## 2. 테스트 범위

### 2.1 AuthIntegrationTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `회원가입후_로그인하면_HttpOnly쿠키가_내려오고_me가_STAFF를_반환한다` | 가입은 STAFF. 로그인 `Set-Cookie`에 access/refresh와 HttpOnly. 그 쿠키로 `/api/auth/me` 200 | PASS |
| `쿠키없이_me와_상품목록은_401이다` | `/api/auth/me`, `/api/products` → 401 `UNAUTHORIZED` | PASS |
| `비밀번호가_틀리면_400이고_없는_이메일은_404이다` | 틀린 비밀번호 400 `VALIDATION_ERROR`. 없는 이메일 404 `NOT_FOUND` | PASS |
| `로그아웃_응답은_쿠키를_만료시키고_쿠키가_없으면_me는_401이다` | 로그아웃 `Set-Cookie`의 `Max-Age=0`. 쿠키를 보내지 않으면 `/me` 401 | PASS |
| `refresh쿠키로_토큰을_다시_받으면_me가_된다` | `POST /api/auth/refresh`가 쿠키를 다시 주고, 그 쿠키로 `/me` 200 | PASS |
| `짧은_비밀번호_회원가입은_400이다` | 비밀번호 2자 → 400 `VALIDATION_ERROR` | PASS |

### 2.2 StockFlowIntegrationTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `STAFF는_상품등록이_403이고_입고는_가능하다` | STAFF의 상품 등록 403 `FORBIDDEN`. 같은 사용자의 입고 201, 재고 3 | PASS |
| `STAFF는_재고조정과_롤백이_403이다` | 조정·롤백은 ADMIN만 가능 | PASS |
| `카테고리와_상품을_만들면_조회되고_같은_SKU는_409이다` | 카테고리 트리에 이름 표시. 신규 상품 재고·매입단가 0. 같은 SKU 409 `CONFLICT` | PASS |
| `입고_후_초과출고는_409이고_출고롤백하면_재고가_돌아온다` | 입고 10. 11개 출고 409 `INSUFFICIENT_STOCK`. 4개 출고 후 재고 6. 롤백 후 재고 10, 원본 거래 `CANCELED` | PASS |
| `없는_상품은_404이고_하위가_있는_카테고리와_거래가_있는_상품_삭제는_409이다` | 없는 상품 404 `NOT_FOUND`. 하위 카테고리가 있으면 삭제 409 `DELETE_CONFLICT`. 입고 이력이 있는 상품 삭제도 409 `DELETE_CONFLICT` | PASS |

ADMIN 계정은 가입 API가 STAFF만 만들므로 테스트가 DB에 직접 넣고 로그인했다.

### 2.3 TokenSecurityIntegrationTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `만료된_access토큰은_401이다` | 서명은 맞지만 만료 시각이 과거인 access 쿠키로 `/api/auth/me` → 401 `UNAUTHORIZED` | PASS |
| `서명을_바꾼_access토큰은_401이다` | 로그인으로 받은 access 토큰의 서명 마지막 글자를 바꾼 뒤 `/me` → 401 `UNAUTHORIZED` | PASS |

### 2.4 StockOutAtomicityIntegrationTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `출고_이력_저장이_실패하면_재고_감소도_롤백된다` | 재고 10에서 4개 출고 중 이력 `save`가 예외를 던지면, 재고는 10이고 거래 이력은 0건 | PASS |

이력 저장 실패는 정상 요청으로 나지 않아, 테스트에서 `StockTransactionRepository.save`만 예외가 나게 했다. `stockOut`의 `@Transactional`이 재고 감소와 이력 저장을 함께 롤백한다.

### 2.5 ChatToolIntegrationTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `재고_조회_도구는_DB_수량을_반환하고_데이터를_바꾸지_않는다` | `searchProducts("원두")`의 재고 문자열이 DB `currentStock`을 같은 규칙으로 표시한 값과 같다. 호출 뒤 재고 10, 거래 0건 | PASS |
| `역할을_ADMIN이라고_적은_채팅_요청도_토큰이_없으면_401이고_재고는_유지된다` | 본문에 역할 상승과 재고 변경을 적어도 쿠키가 없으면 401. 재고 10, 거래 0건 | PASS |

Gemini 답변 문장은 비교하지 않았다. 재고 숫자는 조회 도구가 DB에서 읽은 값이고, 권한은 본문 문장이 아니라 토큰으로 결정된다.

---

## 3. 코드와 맞춘 점

틀린 비밀번호는 401이 아니다. `AuthService`가 `ValidationException`을 던지고, 상태 코드는 400이다. 없는 이메일은 404다.

로그아웃은 브라우저에 쿠키 삭제를 지시할 뿐, 이미 발급된 access JWT를 무효화하지는 않는다. 그래서 테스트는 응답의 `Max-Age=0`과, 쿠키를 빼고 호출한 `/me`의 401을 확인한다.
