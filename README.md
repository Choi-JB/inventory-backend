# inventory (재고관리 + 챗봇 백엔드)

개인 학습/포트폴리오용 **재고관리 REST API**와 **Gemini 기반 챗봇(Function Calling + RAG)** 을 하나의 Spring Boot 서버에 통합한 백엔드입니다.

프론트엔드(Next.js)는 별도 레포 `inventory-frontend`에서 관리합니다.

---

## 개요

| 항목 | 내용 |
|------|------|
| 목적 | Spring Boot 백엔드 구현 + DB/RAG 라우팅 챗봇 경험 |
| 인증 | Google OAuth2 로그인 → 자체 JWT 발급 (HttpOnly 쿠키) |
| 도메인 | User · Category · Product · StockTransaction |
| 챗봇 | DB 조회 tool + 업무 매뉴얼 벡터 검색(pgvector) |

설계·작업 기준 문서는 상위 폴더 `C:\Project\stock_manager\문서\` 에 있습니다.  
진행 중 결정·트러블슈팅은 [`PROGRESS.md`](./PROGRESS.md) 에 정리되어 있습니다.

---

## 기술 스택

| 영역 | 선택 |
|------|------|
| Language / Build | Java 21, Gradle 9 |
| Framework | Spring Boot **4.0.8** |
| Security | Spring Security, OAuth2 Client, JJWT 0.12.6 |
| Persistence | Spring Data JPA, PostgreSQL 17 (Supabase) |
| Vector / AI | pgvector, Spring AI 2.x, Google Gemini |
| API Docs | springdoc-openapi 3.x (Swagger UI) |
| Validation | Bean Validation |

> Spring Boot 4는 Jackson 3.x를 사용합니다. `ObjectMapper` import는 `tools.jackson.databind` 입니다.

---

## 주요 기능

### 재고관리
- **인증**: Google OAuth2 → JWT, `GET /api/auth/me`, `POST /api/auth/logout`
- **카테고리**: 계층(self-referencing) 트리 CRUD
- **상품**: CRUD, 검색·페이징, 재고부족 조회
- **재고 거래**: IN / OUT / CONSUME / ADJUSTMENT, 롤백(상쇄 트랜잭션)
- **손익**: 기간·상품별 집계 (`netProfit` 포함)
- **권한**: `ADMIN` / `STAFF` (`@PreAuthorize`)

### 챗봇
- `POST /api/chat` — Gemini Function Calling
- DB 조회 tool: 상품·카테고리·손익·거래내역
- RAG: `resources/manuals` 매뉴얼 → pgvector (`manual_embeddings`)
- 관리: `POST /api/admin/manuals/reindex` (매뉴얼 재적재)

---

## 아키텍처

계층형(Layer) 패키지 구조입니다.

```
Client (Next.js / Swagger)
        │
        ▼
 Security (JWT Filter, OAuth2)
        │
        ▼
 Controller → Service → Repository → PostgreSQL (+ pgvector)
                    ↘
                 ChatService → Spring AI (Gemini) + Tools
```

패키지 개요:

```
com.example.inventory
├── controller / service / repository / entity
├── dto (request · response)
├── security · exception · enums
├── tool · config          # 챗봇 tool, ChatClient 설정
└── InventoryApplication
```

---

## 로컬 실행

### 사전 준비

1. **JDK 21** (Eclipse Temurin 권장)
2. **Supabase** PostgreSQL (Session Pooler 접속 — Direct connection은 IPv6 이슈로 실패할 수 있음)
3. **Google Cloud Console** OAuth 클라이언트  
   - Redirect URI: `http://localhost:8080/api/auth/google/callback`
4. **Gemini API 키** (Google AI Studio)

### 설정 파일

`src/main/resources/application-local.yaml` 은 git에 포함되지 않습니다. 직접 생성하세요.

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
  ai:
    google:
      genai:
        api-key: <Gemini API 키>
        embedding:
          api-key: ${spring.ai.google.genai.api-key}

jwt:
  secret: <32자 이상 랜덤 문자열>
  expiration: 86400000
```

- `embedding:` 은 반드시 `genai:` **안쪽**에 둘 것
- DB 스키마는 Supabase SQL Editor에서 DDL 실행 (`ddl-auto: validate`)
- 기본 프로필: `local` (`application.yaml`)

### 실행

```bash
# Windows
.\gradlew.bat bootRun

# 또는
.\gradlew.bat build
```

- API 서버: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

로그인: 브라우저에서 `/oauth2/authorization/google` → 성공 시 `accessToken` 쿠키 + 프론트(`frontend.url`, 기본 `http://localhost:3000`)로 리다이렉트

---

## API 요약

| 영역 | Base path |
|------|-----------|
| 인증 | `/api/auth` |
| 카테고리 | `/api/categories` |
| 상품 | `/api/products` |
| 재고 | `/api/stock` |
| 챗봇 | `/api/chat` |
| 매뉴얼 관리 | `/api/admin/manuals` |

상세 요청/응답은 `문서/재고관리_챗봇_API명세서.md` 및 Swagger를 참고하세요.

---

## Git / 브랜치

```
main ← dev ← feature/*
```

- `main` 직push 금지, `feature/*` → `dev` PR 후 squash merge
- 실제 통합 브랜치명은 `dev` (문서의 `develop`과 동일 역할)

관련 레포:

| 레포 | 역할 |
|------|------|
| `inventory` (본 레포) | Spring Boot API + 챗봇 |
| `inventory-frontend` | Next.js UI |

---

## 참고 문서

경로: `C:\Project\stock_manager\문서\`

| 문서 | 내용 |
|------|------|
| 재고관리_챗봇_기능명세서.md | 도메인·재고 정책·우선순위 |
| 재고관리_챗봇_DB테이블설계서.md | ERD / DDL |
| 재고관리_챗봇_API명세서.md | REST API 계약 |
| 재고관리_챗봇_패키지구조설계서.md | 백엔드 패키지·계층 원칙 |
| 재고관리_챗봇_레포구성_브랜치전략.md | 레포/브랜치 운용 |
| 재고관리_챗봇_작업순서.md | 구현 순서·AI 활용 기준 |
| 재고관리_챗봇_프론트엔드설계서.md | 화면·프론트 규칙 |
| 재고관리_챗봇_챗봇명세서.md | 챗봇 라우팅·tool·RAG |

레포 내부:

| 파일 | 내용 |
|------|------|
| [`PROGRESS.md`](./PROGRESS.md) | 진행 상황, 설계 결정, 트러블슈팅 |
| `src/main/resources/manuals/` | 챗봇 RAG용 업무 매뉴얼 |

---

## 라이선스

개인 학습/포트폴리오 프로젝트입니다.
