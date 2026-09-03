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
- [**인계 문서**](docs/08-handoff.md) · [M5 상세 명세](docs/09-milestone-5.md)

저장소 규약과 자주 걸리는 함정은 [CLAUDE.md](CLAUDE.md)에 있습니다.

## 시작하기

필요한 것: **Node 20+, pnpm 10+, Java 21, Docker**

```bash
pnpm install
pnpm infra:up      # Postgres, Redis, MinIO
pnpm doctor        # 환경 점검 — 전부 ✅ 여야 합니다
```

외부 API 키가 없어도 전체 스택이 돌아갑니다. 터미널 3개에서:

```bash
pnpm stub          # 외부 provider 대역        → :9876
pnpm api:dev:stub  # Spring Boot               → :8080
pnpm dev           # Next.js                   → :3000
```

`http://localhost:3000` 에서 가입하고 "노르웨이"로 검색해 보세요.

실제 API 키가 있다면 `.env.example` 을 `apps/api/.env.local` 로 복사해 채운 뒤 `pnpm api:dev` 를 씁니다.
포트가 충돌하면 `infra/docker/.env.example` 을 `.env` 로 복사해 조정하세요.

```bash
pnpm verify        # typecheck + lint + format + build + 백엔드 테스트
```

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
tools/
  stub-provider/  외부 API 대역 — 키 없이 개발할 때
scripts/      dev-api.sh, doctor.sh
docs/         제품·기술 명세
```

## 현재 상태

[로드맵](docs/07-roadmap.md) 기준 **M0~M4 완료**, M5(운영 준비) 미착수.
백엔드 테스트 74건 통과.

동작하는 것: 가입 → 검색 → 기록 → 타임라인 → 공개 프로필 → 컬렉션 공유(동적 OG 이미지) →
무드 탐색 → 팔로우 → 피드 → 좋아요.

**이어서 작업한다면 [docs/08-handoff.md](docs/08-handoff.md) 를 먼저 읽으세요.**
남은 작업은 [GitHub Issues](https://github.com/seonghoho/LitMood/issues) 9건과
[M5 상세 명세](docs/09-milestone-5.md)에 정리돼 있습니다.
