# 서비스 단위 테스트 정리

**작성일**: 2026-10-16  
**대상**: `CategoryService`, `ProductService`, `StockTransactionService`  
**방식**: Mockito 기반 순수 단위 테스트 (`@ExtendWith(MockitoExtension.class)`)  
**DB 영향**: 없음 (Repository는 `@Mock` — 실제 INSERT/UPDATE/DELETE 없음)

---

## 1. 실행 결과 요약

| 테스트 클래스 | 케이스 수 | 실패 | 오류 | 스킵 | 소요 시간 |
|---------------|-----------|------|------|------|-----------|
| `CategoryServiceTest` | 10 | 0 | 0 | 0 | ~1.4s |
| `ProductServiceTest` | 8 | 0 | 0 | 0 | ~0.2s |
| `StockTransactionServiceTest` | 14 | 0 | 0 | 0 | ~0.2s |
| **합계** | **32** | **0** | **0** | **0** | — |

**결과**: `BUILD SUCCESSFUL` — 전체 통과

### 재실행 명령

```powershell
.\gradlew.bat test --tests "com.example.inventory.service.CategoryServiceTest" `
  --tests "com.example.inventory.service.ProductServiceTest" `
  --tests "com.example.inventory.service.StockTransactionServiceTest"
```

HTML 리포트: `build/reports/tests/test/index.html`  
JUnit XML: `build/test-results/test/TEST-*.xml`

---

## 2. 테스트 범위 (무엇을 검증했는지)

### 2.1 CategoryServiceTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `getTree_부모자식구조를트리로조립한다` | 루트·자식 트리 조립 | PASS |
| `create_부모없이생성한다` | 최상위 카테고리 생성 | PASS |
| `create_부모카테고리가없으면NotFound` | 잘못된 parentId → `NotFoundException` | PASS |
| `update_이름을수정한다` | 이름·설명 수정 | PASS |
| `update_없으면NotFound` | 없는 id → `NotFoundException` | PASS |
| `delete_하위카테고리가있으면충돌` | 하위 존재 → `DeleteConflictException` | PASS |
| `delete_소속상품이있으면충돌` | 소속 상품 → `DeleteConflictException` | PASS |
| `delete_성공하면repository에서삭제한다` | 조건 충족 시 delete 호출 | PASS |
| `validateExists_없으면NotFound` | 존재 검증 실패 | PASS |
| `getDescendantCategoryIds_자신과하위id를모두반환한다` | 자신 + 하위 id 재귀 수집 | PASS |

### 2.2 ProductServiceTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `getById_있으면응답을반환한다` | 단건 조회 성공 | PASS |
| `getById_없으면NotFound` | 없는 상품 → `NotFoundException` | PASS |
| `create_성공하면저장한다` | 카테고리 검증 후 저장 | PASS |
| `create_sku중복이면Conflict` | SKU 중복 → `ConflictException` | PASS |
| `update_상품정보를수정한다` | 이름·가격·카테고리·최소재고 수정 | PASS |
| `delete_거래이력이있으면충돌` | 거래 존재 → `DeleteConflictException` | PASS |
| `delete_거래이력이없으면삭제한다` | 삭제 성공 | PASS |
| `search_필터결과를페이지로반환한다` | keyword + categoryId + lowStock 검색 | PASS |

### 2.3 StockTransactionServiceTest

| 메서드 | 검증 내용 | 결과 |
|--------|-----------|------|
| `stockIn_재고를늘리고거래를저장한다` | 입고 시 재고·가중평균·응답 | PASS |
| `stockIn_상품없으면NotFound` | 상품 없음 → `NotFoundException` | PASS |
| `stockOut_단가가없으면판매가를사용한다` | unitPrice null → sellingPrice | PASS |
| `stockOut_재고부족이면예외` | `InsufficientStockException` | PASS |
| `stockConsume_소비거래를생성한다` | CONSUME + consumeType | PASS |
| `stockAdjustment_실사수량으로조정한다` | 조정량(실사−기존) 계산 | PASS |
| `rollback_ADJUSTMENT는거부한다` | `RollbackNotAllowedException` | PASS |
| `rollback_이미취소된거래는거부한다` | `AlreadyCanceledException` | PASS |
| `rollback_상쇄거래는거부한다` | reversalOfId 있는 거래 거부 | PASS |
| `rollback_입고롤백시재고부족이면충돌` | `RollbackConflictException` | PASS |
| `rollback_출고를복구한다` | 재고 복구 + 원본 CANCELED | PASS |
| `searchById_등록자와취소자이름을채운다` | userName / canceledByName | PASS |
| `search_페이지의유저상품을배치조회한다` | 목록 N+1 방지 매핑 | PASS |
| `getProfitLoss_매출원가소비손실을집계한다` | revenue/cost/profit/consumeLoss/net | PASS |

---

## 3. 테스트 방식 설명

```
테스트 코드
   │
   ├─ @Mock CategoryRepository / ProductRepository / ...
   │     → 실제 DB 호출 없음, when(...).thenReturn(...) 로 가짜 응답
   │
   └─ @InjectMocks XxxService
         → 서비스 비즈니스 로직만 실행·검증
```

- **단위 테스트**: 서비스 계층의 분기·예외·계산 로직
- **비범위**: Controller HTTP, Security, 실제 JPA/SQL, Spring 컨텍스트 기동
- 엔티티 `id` 등은 `ReflectionTestUtils.setField`로 주입

서비스의 `@Transactional`은 mock 환경에서 실제 DB 트랜잭션을 열지 않습니다.

---

## 4. 파일 위치

```
src/test/
├── TEST_REPORT.md                          ← 이 문서
└── java/com/example/inventory/service/
    ├── CategoryServiceTest.java
    ├── ProductServiceTest.java
    └── StockTransactionServiceTest.java
```
