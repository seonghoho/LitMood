# LitMood

> 읽고, 보고, 들은 것을 **그때의 기분**과 함께 기록하고 나누는 서비스.

책·영화·음악을 하나의 타임라인에 기록하고, 무드(mood) 태그로 묶어 큐레이션 컬렉션을 공유합니다.

## 왜

콘텐츠 종류마다 기록 서비스가 흩어져 있습니다(책=북적북적, 영화=왓챠, 음악=라스트에프엠).
취향은 하나인데 기록은 세 곳에 분산됩니다. 그리고 기존 서비스의 기록 단위는 대부분 별점과 리뷰지만,
우리가 어떤 콘텐츠를 다시 떠올리는 계기는 평점이 아니라 **맥락과 감정**입니다.

LitMood는 **통합 타임라인**과 **무드**를 1급 개념으로 다룹니다.

## 스택

| 레이어     | 기술                                                                   |
| ---------- | ---------------------------------------------------------------------- |
| 프론트엔드 | Next.js 15 (App Router), TypeScript, Panda CSS, TanStack Query         |
| 백엔드     | Spring Boot 3.4, Java 21 (Virtual Threads), Spring Data JPA / Security |
| 데이터     | PostgreSQL 16, Redis 7, MinIO (S3 호환)                                |
| 인프라     | Docker Compose (로컬), Kubernetes + Kustomize (배포), GitHub Actions   |
| 계약       | OpenAPI 3.1 (springdoc) → orval / Postman                              |

선택 근거는 [docs/02-tech-decisions.md](docs/02-tech-decisions.md)에 ADR 형식으로 정리되어 있습니다.

## 문서

전체 명세는 [`docs/`](docs/README.md)에 있습니다.

- [제품 개요](docs/00-product-overview.md) · [기능 명세](docs/01-requirements.md) · [기술 선택](docs/02-tech-decisions.md)
- [아키텍처](docs/03-architecture.md) · [도메인 모델](docs/04-domain-model.md) · [API 명세](docs/05-api-spec.md)
- [인프라](docs/06-infra.md) · [로드맵](docs/07-roadmap.md)

## 개발 환경

```bash
pnpm infra:up      # Postgres, Redis, MinIO (Docker)
pnpm install
pnpm api:dev       # Spring Boot  → http://localhost:8080
pnpm dev           # Next.js      → http://localhost:3000
```

동작 확인:

```bash
curl http://localhost:8080/api/v1/ping
```

`http://localhost:3000/health` 는 서버 컴포넌트가 백엔드를 직접 호출해 배선을 확인하는 페이지입니다.

포트가 이미 사용 중이라면 `infra/docker/.env.example` 을 `.env` 로 복사해 조정하세요.

## 구조

```
apps/
  web/        Next.js 15 (App Router, Panda CSS)
  api/        Spring Boot 3.4 (Java 21)
packages/
  ui/         디자인 시스템 — 무드 토큰
  api-client/ OpenAPI 에서 생성되는 타입·훅
  config/     공유 tsconfig / eslint
infra/
  docker/     로컬 개발 인프라
  k8s/        base + overlays(dev, prod)
docs/         제품·기술 명세
```

## 현재 상태

[로드맵](docs/07-roadmap.md) 기준 **M0 완료**. 다음은 M1 (인증 + 콘텐츠 검색)입니다.
