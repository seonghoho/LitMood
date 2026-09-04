# CLAUDE.md

이 저장소에서 작업할 때 알아야 할 것들. 처음이라면 [docs/08-handoff.md](docs/08-handoff.md)를 먼저 읽으세요.

## 이 프로젝트가 무엇인가

책·영화·음악을 **무드(그때의 기분)** 와 함께 기록하고 나누는 서비스.
무드는 부가 기능이 아니라 **1급 개념**입니다 — 별점이 아니라 맥락으로 기록하는 것이 차별점입니다.
제품 정의는 [docs/00-product-overview.md](docs/00-product-overview.md)에 있고, 기능 요구사항은
`F-XX-XX` 번호로 [docs/01-requirements.md](docs/01-requirements.md)에 정리돼 있습니다.

## 명령

```bash
pnpm doctor           # 개발 환경 점검 — 새 PC 에서 제일 먼저
pnpm infra:up         # Postgres, Redis, MinIO (Docker)
pnpm stub             # 외부 API 키 없이 개발할 때 (별도 터미널)
pnpm api:dev:stub     # 백엔드를 스텁에 물려 기동 → :8080
pnpm dev              # 프론트엔드 → :3000
pnpm verify           # typecheck + lint + format + build + 백엔드 테스트
```

실제 API 키가 있으면 `apps/api/.env.local`에 넣고 `pnpm api:dev`를 씁니다.

## 구조

```
apps/api    Spring Boot 3.4 / Java 21 — interfaces → application → domain ← infrastructure
apps/web    Next.js 15 App Router / Panda CSS — src/features/{도메인}/ 단위로 묶음
packages    ui(무드 토큰) · api-client(orval) · config(공유 설정)
infra       docker(로컬) · k8s(base + dev/prod overlay)
docs        제품·기술 명세. 번호 순서대로 읽으면 됩니다
tools       stub-provider — 외부 API 대역
```

## 지켜야 할 규칙

**레이어 방향** ([docs/03-architecture.md](docs/03-architecture.md))
`domain`은 다른 레이어를 import하지 않습니다. Repository는 `domain`이 인터페이스로 정의하고
`infrastructure`가 구현합니다. 새 provider나 저장소를 붙여도 도메인은 건드리지 않습니다.

**도메인 규칙은 엔티티 안에** ([docs/04-domain-model.md](docs/04-domain-model.md) 불변식)
"WANT 상태에는 별점을 남길 수 없다" 같은 규칙은 서비스가 아니라 `Record`가 지킵니다.
DB 제약으로도 이중 방어합니다. 규칙을 추가할 때 두 곳 모두 갱신하세요.

**에러는 RFC 9457 Problem Details로만**
`ErrorCode`에 코드를 추가하고 `LitmoodException`을 던집니다. `title`은 그대로 사용자에게
노출되므로 한국어로 씁니다. 프론트는 `code`로 분기합니다.

**공개 범위를 벗어난 대상은 403이 아니라 404**
403은 "존재하지만 볼 수 없다"를 알려줘 존재 자체를 노출합니다.

**시크릿은 코드에 두지 않습니다**
외부 API 키는 백엔드 환경변수로만 존재하고 프론트 번들에 절대 포함되지 않습니다.
검색은 백엔드가 프록시합니다.

**OpenAPI가 API 계약의 진실 원천** (ADR-008)
컨트롤러를 바꾸면 `packages/api-client/openapi.yaml`을 갱신하세요:
`curl -sf http://localhost:8080/v3/api-docs.yaml -o packages/api-client/openapi.yaml`

## 자주 걸리는 함정

| 증상                      | 원인                                                                                                |
| ------------------------- | --------------------------------------------------------------------------------------------------- |
| 한글 slug 라우트가 404    | Next App Router는 동적 세그먼트를 **디코딩하지 않고** 넘깁니다. `decodeRouteParam()`을 쓰세요       |
| 무한 렌더 (React #185)    | zustand v5는 셀렉터를 참조 비교합니다. 객체를 반환하지 말고 필드별로 구독하세요                     |
| Panda 색이 안 먹음        | 빌드 타임 정적 추출기입니다. 동적 값은 `token()`으로 조회해 인라인 스타일로 넘기세요                |
| 타임라인 500              | `SELECT DISTINCT` + `ORDER BY`는 Postgres에서 무효입니다. 컬렉션 조인 대신 EXISTS 서브쿼리를 쓰세요 |
| 테스트가 남의 데이터를 봄 | 테스트들이 Postgres 컨테이너 하나를 공유합니다. 콘텐츠는 테스트마다 고유 ISBN을 쓰세요              |
| 응답에 필드가 없음        | Jackson은 `always` 로 설정돼 있습니다. `non_null`로 되돌리지 마세요 — OpenAPI 계약과 어긋납니다     |

## 테스트

백엔드는 **Testcontainers로 실제 Postgres·Redis**를 씁니다. H2로는 `jsonb`/`text[]`/부분 인덱스가
재현되지 않습니다. 외부 API는 WireMock으로 대역화하고, 정상뿐 아니라 **장애·지연**도 검증합니다.

도메인 불변식은 스프링 컨텍스트 없이 단위 테스트로 검증합니다 (`RecordDomainTest`, `CollectionDomainTest`).

## 커밋

- 한국어로 작성하고, **무엇을** 바꿨는지보다 **왜** 그렇게 했는지를 남깁니다
- 버그를 고쳤다면 어떤 상황에서 터졌는지 적습니다
- `master`는 기본 브랜치입니다. 작업은 브랜치를 파서 합니다
