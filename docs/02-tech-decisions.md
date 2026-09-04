# 02. 기술 선택 결정 기록 (ADR)

각 항목은 **선택 / 대안 / 근거 / 트레이드오프** 순으로 기록한다.
"요즘 뜨니까"가 아니라 **이 서비스의 요구사항에서 도출된 이유**만 근거로 인정한다.

---

## ADR-001. 프론트엔드 프레임워크 → **Next.js 15 (App Router) + TypeScript**

**대안**: 기존 Vue 3 + Vite (SPA), Nuxt 3, Remix

**근거**

1. **SEO가 기능 요구사항이다.** 공개 기록·컬렉션·프로필(`/@handle`)이 검색 유입 경로다(NFR-06).
   현재 코드는 Vite SPA라 크롤러에게 빈 `<div id="app">`만 보인다. 서버 렌더링이 선택이 아니라 필수다.
2. **공유가 핵심 기능이다.** 컬렉션 공유 시 카카오톡/트위터 미리보기 카드가 곧 유입 채널이다.
   Next의 `generateMetadata` + `next/og`(ImageResponse)로 **컬렉션별 동적 OG 이미지**를 런타임 생성할 수 있다. 별도 이미지 서버가 불필요하다.
3. **RSC로 데이터 페칭 계층이 줄어든다.** 타임라인·상세처럼 읽기 위주 화면은 서버 컴포넌트에서 백엔드를 직접 호출 → 클라이언트 번들과 워터폴이 감소한다.
4. Nuxt도 1~3을 만족하지만, **RSC 성숙도와 생태계(특히 Panda·Radix·TanStack 조합)에서 React 진영이 앞선다.** Vue를 유지할 이유는 "기존 코드 재사용"뿐인데, 기존 코드는 스텁 수준이라 보존 가치가 없다.

**트레이드오프**

- 기존 Vue 코드 전량 폐기 → 실제 손실은 라우터/네비바 배선 정도로 미미
- App Router의 캐싱 모델은 학습 곡선이 있음 → `docs/03-architecture.md`에 캐싱 규칙을 명문화하여 완화

---

## ADR-002. 스타일링 → **Panda CSS**

**대안**: Emotion, Tailwind CSS(현행), vanilla-extract, CSS Modules

**근거**

1. **Emotion은 App Router와 상성이 나쁘다.** 런타임 CSS-in-JS라 스타일이 붙은 모든 컴포넌트에 `"use client"`가 전염된다. RSC를 쓰려고 Next를 골랐는데 RSC를 못 쓰게 되는 자기모순.
2. **Panda는 빌드 타임에 CSS를 추출한다(zero-runtime).** 서버 컴포넌트에서 그대로 쓰이며 런타임 스타일 계산 비용이 0이다.
3. **무드 = 색.** 이 서비스는 무드마다 색 정체성을 갖는다(예: 새벽=deep indigo, 몽글=warm pink).
   Panda의 **토큰 + semantic token + recipe** 체계가 이걸 타입 안전하게 표현한다.
   Tailwind는 임의값(`bg-[#123456]`) 남발로 흐트러지기 쉽다.
4. Tailwind 대비 **타입 안전성**: 오타 난 토큰은 컴파일 단계에서 잡힌다.

**트레이드오프**

- Tailwind보다 커뮤니티/예제가 적음 → 디자인 시스템을 `packages/ui`로 선(先)구축해 팀 내 표준을 만든다
- 코드젠 단계(`panda codegen`) 필요 → `postinstall`과 `dev` 스크립트에 편입

---

## ADR-003. 백엔드 → **Spring Boot 3.4 / Java 21**

**대안**: NestJS(Node 단일 언어), Kotlin + Spring, Go

**근거**

1. **도메인이 트랜잭션 중심이다.** 기록·컬렉션·팔로우는 관계형 정합성이 중요하다.
   Spring Data JPA + `@Transactional`의 성숙도가 이 영역에서 가장 높다.
2. **Java 21의 가상 스레드(Virtual Threads)가 이 서비스의 병목에 정확히 맞는다.**
   검색 요청 1건이 네이버·TMDB·Spotify **3개 외부 API를 동시 호출**하는 I/O 바운드 구조다.
   `spring.threads.virtual.enabled=true` 한 줄로 리액티브 코드 없이 높은 동시성을 얻는다.
   WebFlux를 쓰면 코드 전체가 리액티브로 오염되지만, 가상 스레드는 명령형 코드를 그대로 둔다.
3. **NestJS를 고르지 않은 이유**: 프론트와 언어를 통일하는 이점은 있으나,
   본 프로젝트는 "풀스택 역량 구축"이 목표이며 JPA/Spring Security 수준의 성숙한 백엔드 스택 경험이 더 가치 있다.
4. **Kotlin 대신 Java 21**: record, sealed interface, pattern matching으로 Kotlin과의 표현력 격차가 크게 줄었다.
   국내 채용/협업 저변이 넓어 유지보수 리스크가 낮다.

**트레이드오프**

- 프론트/백 언어 이원화 → **OpenAPI 스펙에서 TS 타입을 자동 생성**하여 계약 불일치를 차단(ADR-008)

---

## ADR-004. 데이터베이스 → **PostgreSQL 16**

**대안**: MySQL 8, MongoDB

**근거**

1. **`jsonb`**: 책/영화/음악은 고유 필드가 제각각이다(ISBN·페이지수 / 러닝타임·감독 / 앨범·재생시간).
   공통 필드는 컬럼으로, 타입별 고유 필드는 `metadata jsonb`로 → 타입 추가 시 스키마 마이그레이션이 불필요하다.
2. **배열 + GIN 인덱스**: 무드 태그 검색(`tags @> ARRAY['새벽']`)이 핵심 탐색 경로(F-07-01)다. PG에서 인덱스로 해결된다.
3. **부분 인덱스**: `WHERE deleted_at IS NULL`, `WHERE visibility = 'PUBLIC'` 같은 조건이 대부분의 쿼리에 붙는다. 인덱스 크기를 크게 줄인다.
4. MongoDB 미채택: 팔로우/좋아요/컬렉션은 명백한 관계형 데이터다. 스키마 유연성은 `jsonb`로 충분하다.

---

## ADR-005. 캐시 → **Redis 7**

**용도 3가지** (그 외 용도로 확대하지 않는다)

1. **외부 API 응답 캐시** (F-02-04) — TTL 6h. rate limit 회피와 p95 개선
2. **Refresh 토큰 화이트리스트** — 즉시 강제 로그아웃과 토큰 회전 감지에 필요
3. **인기 랭킹** (F-07-02) — Sorted Set. RDB의 무거운 집계 쿼리 대체

---

## ADR-006. 패키지 매니저 → **pnpm** + **Turborepo**

**근거**

1. 프론트를 `apps/web` / `packages/ui` / `packages/api-client`로 분리 → **워크스페이스가 필요**하다
2. pnpm의 **엄격한 node_modules 격리**가 phantom dependency(선언 안 한 패키지가 우연히 import되는 문제)를 차단한다
3. 콘텐츠 주소 기반 저장소로 디스크·설치 시간 절감 → CI 시간 직결
4. Turborepo: 빌드/린트/타입체크의 **원격 캐싱**으로 CI 반복 비용 제거

**참고**: 백엔드는 Gradle(Kotlin DSL) 별도 관리. Node 툴체인에 억지로 편입하지 않는다.

---

## ADR-007. 컨테이너 & 오케스트레이션 → **Docker Compose (로컬) / Kubernetes + Kustomize (배포)**

**근거**

1. **로컬은 Compose로 충분하다.** PG·Redis·MinIO를 개발자가 직접 설치하게 두면 온보딩이 깨진다. `docker compose up` 한 번으로 인프라가 뜬다.
2. **배포는 K8s.** 검색 API는 외부 의존 때문에 트래픽 스파이크에 취약하므로 HPA로 백엔드만 독립 스케일링한다.
3. **Helm 대신 Kustomize**: 환경이 dev/prod 둘뿐이고 차이가 replica 수·리소스·이미지 태그 정도다.
   Helm 템플릿의 복잡도가 이득을 넘어선다. `base/` + `overlays/{dev,prod}`로 충분하다.

---

## ADR-008. API 계약 → **OpenAPI 3.1을 단일 진실 원천(SSOT)으로**

**흐름**

```
Spring 컨트롤러 (springdoc-openapi)
        ↓ 빌드 시 자동 생성
   openapi.json
        ├─→ orval → packages/api-client (TS 모델 타입 + fetch 함수)
        └─→ Postman collection import (수동 관리 폐기)
```

**근거**

1. **Postman 컬렉션을 손으로 관리하면 반드시 낡는다.** OpenAPI에서 import하면 항상 최신이다.
2. 프론트의 요청/응답 타입이 백엔드 코드에서 자동 파생 → **계약 불일치가 컴파일 에러로 드러난다.** ADR-003의 언어 이원화 리스크를 여기서 상쇄한다.
3. CI에서 `openapi.json` diff를 검사해 **의도치 않은 breaking change를 PR 단계에서 차단**한다.

---

## ADR-009. 인증 → **JWT (Access 메모리 + Refresh HttpOnly 쿠키)**

**근거**

- Access 토큰을 `localStorage`에 두지 않는다 → XSS 탈취 방지
- Refresh는 `HttpOnly; Secure; SameSite=Lax` 쿠키 → JS 접근 불가
- Refresh 회전 + Redis 화이트리스트 → 탈취 토큰 재사용 감지 시 전 세션 무효화
- 서버 세션(Stateless 포기) 대신 JWT: K8s에서 백엔드 파드가 수평 확장되므로 무상태가 유리

---

## ADR-010. 외부 콘텐츠 provider

| 타입 | Provider               | 선정 이유                                                   |
| ---- | ---------------------- | ----------------------------------------------------------- |
| 책   | **네이버 책 검색 API** | 국내 도서 커버리지 최상. 기존 프로젝트에서 이미 사용        |
| 영화 | **TMDB**               | 무료, 한국어 메타데이터·포스터 품질 우수, rate limit 관대   |
| 음악 | **Spotify Web API**    | Client Credentials로 서버 간 인증 가능. 앨범/트랙 메타 풍부 |

**공통 설계**: `ContentProvider` 인터페이스로 추상화 → 각 provider는 구현체.
provider 교체·추가 시 도메인 계층은 변경되지 않는다.

> ⚠️ **보안 조치**: 현재 저장소의 `src/stores/book.store.js`에 네이버 API 키가 **평문 커밋**되어 원격에 올라가 있다.
> 해당 키는 **폐기 후 재발급**하고, 신규 키는 백엔드 환경변수로만 주입한다. (`docs/06-infra.md` 참조)

---

## 최종 스택 요약

| 레이어               | 선택                                                       |
| -------------------- | ---------------------------------------------------------- |
| 프론트엔드           | Next.js 15 (App Router), TypeScript 5.6, React 19          |
| 스타일               | Panda CSS + Radix UI Primitives (접근성, NFR-05)           |
| 상태/데이터          | TanStack Query 5 (서버 상태), Zustand (클라이언트 UI 상태) |
| 폼/검증              | React Hook Form + Zod                                      |
| 백엔드               | Spring Boot 3.4, Java 21, Spring Web / Data JPA / Security |
| 마이그레이션         | Flyway                                                     |
| DB / 캐시 / 스토리지 | PostgreSQL 16 / Redis 7 / MinIO (S3 호환)                  |
| API 문서             | springdoc-openapi → OpenAPI 3.1 → orval + Postman          |
| 테스트               | JUnit 5 + Testcontainers (BE) / Vitest + Playwright (FE)   |
| 패키지               | pnpm + Turborepo (FE), Gradle Kotlin DSL (BE)              |
| 인프라               | Docker Compose (로컬), Kubernetes + Kustomize (배포)       |
| CI/CD                | GitHub Actions                                             |
