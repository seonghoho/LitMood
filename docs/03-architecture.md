# 03. 시스템 아키텍처

## 전체 구성도

```
┌──────────────┐
│   Browser    │
└──────┬───────┘
       │ HTTPS
┌──────▼─────────────────────────────────────────────┐
│  Next.js 15 (apps/web)                             │
│  ├─ RSC: 서버에서 백엔드 직접 호출 (읽기 화면)      │
│  ├─ Route Handler: 인증 쿠키 프록시 (/api/auth/*)  │
│  └─ next/og: 컬렉션 OG 이미지 동적 생성            │
└──────┬─────────────────────────────────────────────┘
       │ REST (OpenAPI 3.1 계약)
┌──────▼─────────────────────────────────────────────┐
│  Spring Boot 3.4 (apps/api)                        │
│  ┌──────────────────────────────────────────────┐  │
│  │ interfaces  : Controller, DTO, ExceptionHdlr │  │
│  │ application : UseCase(Service), Tx 경계       │  │
│  │ domain      : Entity, VO, Repository(if)     │  │
│  │ infrastructure: JPA impl, Redis, 외부 API     │  │
│  └──────────────────────────────────────────────┘  │
└───┬──────────┬──────────┬──────────┬───────────────┘
    │          │          │          │
┌───▼────┐ ┌───▼───┐ ┌────▼───┐ ┌────▼──────────────┐
│Postgres│ │ Redis │ │ MinIO  │ │ 외부 Content API  │
│   16   │ │   7   │ │  (S3)  │ │ Naver/TMDB/Spotify│
└────────┘ └───────┘ └────────┘ └───────────────────┘
```

## 레이어 규칙 (백엔드)

의존 방향은 **항상 안쪽(domain)으로만** 향한다.

| 레이어           | 책임                           | 금지 사항                        |
| ---------------- | ------------------------------ | -------------------------------- |
| `interfaces`     | HTTP 매핑, 요청 검증, DTO 변환 | 비즈니스 로직 금지               |
| `application`    | 유스케이스 조합, 트랜잭션 경계 | HTTP/JPA 타입 노출 금지          |
| `domain`         | 엔티티, 값 객체, 도메인 규칙   | **다른 레이어 import 전면 금지** |
| `infrastructure` | JPA·Redis·외부 API 구현체      | 도메인 규칙 포함 금지            |

`domain`은 Repository를 **인터페이스로만** 정의하고, `infrastructure`가 구현한다.
→ 외부 API provider를 교체해도 도메인은 재컴파일조차 필요 없다.

## 핵심 흐름 1 — 통합 검색 (가상 스레드 활용)

```
GET /api/v1/contents/search?q=노르웨이의숲&types=BOOK,MOVIE,MUSIC
   │
   ├─ Redis 캐시 조회 (key: search:{type}:{q}) ── HIT ─→ 반환
   │                                              MISS
   ├─ 3개 Provider 병렬 호출 (Virtual Threads, 각 타임아웃 3s)
   │     ├─ NaverBookProvider    → Content[]
   │     ├─ TmdbMovieProvider    → Content[]
   │     └─ SpotifyMusicProvider → Content[]
   │
   ├─ 부분 실패 허용: 실패한 provider는 결과에서 제외 + 경고 로그
   ├─ 공통 Content 모델로 정규화
   └─ Redis 저장 (TTL 6h) → 반환
```

`StructuredTaskScope`로 세 호출을 묶어 **가장 느린 provider에 전체가 끌려가지 않도록** 개별 타임아웃을 건다.
결과적으로 검색 응답은 리액티브 코드 없이 `max(3s, 개별 응답)` 안에 끝난다.

## 핵심 흐름 2 — 기록 생성 (외부 콘텐츠 스냅샷)

```
POST /api/v1/records  { provider, externalId, status, rating, moods[], review }
   │
   ├─ contents 테이블에서 (provider, external_id) 조회
   │     └─ 없으면 → Provider에서 상세 조회 → contents INSERT (스냅샷)
   │
   ├─ UNIQUE(user_id, content_id) 검사 → 중복이면 409
   ├─ records INSERT
   └─ 무드 태그 정규화 (trim, 소문자, 최대 5개) → record_moods INSERT
```

**중요**: 외부 API가 죽어도 이미 스냅샷된 콘텐츠에 대한 기록은 정상 동작한다 (NFR-03).

## 프론트엔드 데이터 페칭 규칙

혼란을 막기 위해 **화면 유형별로 방식을 고정**한다.

| 화면 유형                                                  | 방식                                          | 이유                                      |
| ---------------------------------------------------------- | --------------------------------------------- | ----------------------------------------- |
| 공개 페이지 (`/@handle`, `/collections/[id]`, 콘텐츠 상세) | **RSC + fetch(revalidate)**                   | SEO 필요, 데이터 변경 빈도 낮음           |
| 내 타임라인, 피드                                          | **RSC 초기 렌더 + TanStack Query 무한스크롤** | 첫 화면은 빠르게, 이후는 클라이언트에서   |
| 검색                                                       | **클라이언트 (TanStack Query)**               | 입력에 따라 계속 바뀜, 캐시는 서버가 담당 |
| 기록 작성/수정                                             | **Server Action**                             | 폼 제출 → `revalidateTag`로 타임라인 갱신 |

**캐시 태그 규약**

- `user:{id}:records` — 기록 CRUD 시 revalidate
- `collection:{id}` — 컬렉션 변경 시 revalidate
- `content:{id}` — 콘텐츠 상세(공개 기록 목록 포함)

## 모노레포 구조

```
LitMood/
├─ apps/
│  ├─ web/                    # Next.js 15
│  │  ├─ src/app/             # App Router
│  │  ├─ src/features/        # 도메인별 기능 모듈 (record, collection, content, auth)
│  │  ├─ src/shared/          # 공용 유틸, 훅
│  │  └─ panda.config.ts
│  └─ api/                    # Spring Boot 3.4
│     └─ src/main/java/com/litmood/
│        ├─ interfaces/
│        ├─ application/
│        ├─ domain/
│        └─ infrastructure/
├─ packages/
│  ├─ ui/                     # Panda 기반 디자인 시스템
│  ├─ api-client/             # orval 생성 (OpenAPI → TS)
│  └─ config/                 # eslint / tsconfig / prettier 공유
├─ infra/
│  ├─ docker/                 # Dockerfile, compose
│  └─ k8s/                    # base/ + overlays/{dev,prod}
├─ docs/
└─ .github/workflows/
```

**`features/` 구조를 택한 이유**: `components/`, `hooks/`, `stores/`로 나누는 기술 기반 분류는
기능 하나를 고치려고 4개 디렉토리를 오가게 만든다. 도메인 기반으로 묶어 응집도를 높인다.
(현재 저장소의 `components/book`, `views/book`, `stores/book.store.js` 분산이 정확히 그 문제였다.)

## 에러 처리 규약

모든 API 에러는 **RFC 9457 Problem Details** 형식으로 통일한다.

```json
{
  "type": "https://litmood.app/errors/record-already-exists",
  "title": "이미 기록한 콘텐츠입니다",
  "status": 409,
  "detail": "content_id=42 에 대한 기록이 이미 존재합니다",
  "instance": "/api/v1/records",
  "code": "RECORD_DUPLICATE"
}
```

프론트는 `code`로 분기하고, `title`을 그대로 사용자에게 노출할 수 있도록 **한국어로 작성**한다.
