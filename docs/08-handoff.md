# 08. 인계 문서

> **다음 세션 · 다른 PC 에서 이어받는 사람을 위한 문서입니다.**
> 이 문서 하나로 지금까지의 상태를 파악하고 작업을 이어갈 수 있어야 합니다.

최종 갱신: **2026-09-06** (이슈 #17~#20 · #22 완료 시점)

---

## 1. 지금 어디까지 왔나

[로드맵](07-roadmap.md) 기준 **M0~M4 완료, M5 미착수**.

| 마일스톤              | 상태 | 커밋                                       |
| --------------------- | ---- | ------------------------------------------ |
| M0 기반 구축          | ✅   | `9257a01`                                  |
| M1 인증 + 콘텐츠 검색 | ✅   | `909e347`                                  |
| M2 기록 (핵심 경로)   | ✅   | `3e45154`                                  |
| M3 컬렉션 + 공유      | ✅   | `c28977d`                                  |
| M4 소셜               | ✅   | `fc5a4e2`                                  |
| M5 운영 준비          | ⬜   | — ([09-milestone-5.md](09-milestone-5.md)) |

**동작하는 것**: 랜딩(인기 콘텐츠) → 가입 → 검색 → 기록 → 타임라인(무드·별점·기간 필터) →
공개 프로필 → 컬렉션 공유(OG 이미지) → 무드 탐색 → 팔로우 → 피드 → 좋아요(기록·컬렉션) →
차단 → 신고.

**M4 소셜의 미배선분을 모두 붙였습니다** — 차단([#17](https://github.com/seonghoho/LitMood/issues/17)),
신고([#18](https://github.com/seonghoho/LitMood/issues/18)),
컬렉션 좋아요([#19](https://github.com/seonghoho/LitMood/issues/19)),
랜딩·인기 콘텐츠([#20](https://github.com/seonghoho/LitMood/issues/20)).
넷 다 화면만으로 끝나지 않았고 응답 계약을 함께 고쳤습니다. 자세한 것은 3절에 있습니다.

**P0·P1 중 코드로 할 수 있는 일은 소진했습니다.** 남은 P0·P1 넷은 전부 코드 밖 결정이나
외부 발급이 선행입니다 — 아래 3절을 보세요.

**API 계약 파이프라인이 이어졌습니다** (ADR-008, [#4](https://github.com/seonghoho/LitMood/issues/4)).
프론트의 모델 타입은 이제 백엔드 DTO 에서 생성됩니다 — `features/*/types.ts` 는 재수출일 뿐이니
손으로 고치지 마세요. 호출 코드까지 옮기는 일은 [#15](https://github.com/seonghoho/LitMood/issues/15)
로 남아 있습니다.

**미완**: [GitHub Issues](https://github.com/seonghoho/LitMood/issues) 10건에 상세히 적어 두었습니다.
아래 3절을 보세요.

---

## 2. 새 PC 에서 시작하기

### 2-1. 필요한 것

|        | 버전            | 확인                         |
| ------ | --------------- | ---------------------------- |
| Node   | 20 이상         | `node -v`                    |
| pnpm   | 10 이상         | `corepack enable && pnpm -v` |
| Java   | 21 (없어도 됨)  | `java -version`              |
| Docker | Desktop 실행 중 | `docker info`                |

> Java 21 이 없으면 Gradle 이 `~/.gradle/jdks` 로 직접 내려받습니다. 시스템에는 설치하지 않으므로
> 다른 프로젝트가 쓰는 JDK 와 충돌하지 않습니다.

### 2-2. 순서

```bash
git clone https://github.com/seonghoho/LitMood.git
cd LitMood

pnpm install
pnpm infra:up          # Postgres, Redis, MinIO
pnpm doctor            # 여기서 전부 ✅ 가 나와야 합니다
```

**포트가 이미 사용 중이라면** `infra/docker/.env.example`을 `infra/docker/.env`로 복사해 조정하세요.
(이 파일은 gitignore 대상이라 PC 마다 다를 수 있습니다.)

### 2-3. 실행 — 외부 API 키 없이

키가 없어도 전체 스택이 돌아갑니다. 터미널 3개:

```bash
pnpm stub              # ① 외부 provider 대역 (:9876)
pnpm api:dev:stub      # ② 백엔드 (:8080)
pnpm dev               # ③ 프론트엔드 (:3000)
```

`http://localhost:3000` 에서 가입하고 "노르웨이"로 검색하면 책·영화·음악이 나옵니다.

**장애 상황을 재현**하려면:

```bash
FAIL=movie pnpm stub     # 영화 provider 만 죽임 → 부분 실패 UI 확인
DELAY=4000 pnpm stub     # 4초 지연 → 타임아웃 격리 확인
```

### 2-4. 실행 — 실제 API 키로

1. `.env.example`을 `apps/api/.env.local`로 복사
2. 키를 채웁니다 (발급처는 [02-tech-decisions.md](02-tech-decisions.md) ADR-010)
3. `pnpm api:dev` (스텁 없이)

> ⚠️ **네이버 키는 재발급이 필요합니다.** 이전 키가 git 히스토리에 노출됐습니다 —
> [이슈 #1](https://github.com/seonghoho/LitMood/issues/1)

### 2-5. 검증

```bash
pnpm verify            # typecheck + lint + format + build + 백엔드 테스트 74건
```

백엔드 테스트는 Testcontainers 로 실제 Postgres·Redis 컨테이너를 띄웁니다.
첫 실행은 이미지 내려받느라 몇 분 걸릴 수 있습니다.

---

## 3. 다음에 할 일

### 우선순위 제안

**① 코드 밖 결정·발급이 선행인 것** — 지금 막혀 있는 것은 전부 여기입니다

| 이슈                                                                               | 먼저 정하거나 받아야 하는 것                                             |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| [#1 네이버 키 재발급](https://github.com/seonghoho/LitMood/issues/1)               | 코드 작업이 아닙니다. **개발자센터에서 재발급**                          |
| [#2 OAuth2 소셜 로그인](https://github.com/seonghoho/LitMood/issues/2)             | 카카오·구글 앱 키. 그리고 같은 이메일의 소셜/이메일 계정을 연결할지 말지 |
| [#8 회원 탈퇴](https://github.com/seonghoho/LitMood/issues/8)                      | 탈퇴자의 공개 컬렉션 처리 정책. 30일 배치를 돌릴 스케줄러도 없습니다     |
| [#28 신고 처리 화면 + 관리자 권한](https://github.com/seonghoho/LitMood/issues/28) | **관리자를 어떻게 임명할 것인가**. 지금 전원 `ROLE_USER` 입니다          |

**② P2 — 착수 순서에 이유가 있습니다**

`#2` 가 들어오면 로그인 경로가 하나 더 생깁니다. `#9`(E2E)와 `#15`(호출 코드 이관)는 인증
흐름을 정면으로 건드리므로 **`#2` 뒤에 하는 편이 다시 손대는 일을 줄입니다.**

| 이슈                                                                          | 메모                                                                             |
| ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| [#23 리스트 ↔ 그리드 뷰](https://github.com/seonghoho/LitMood/issues/23)      | F-04-03. **순수 프론트, 다른 작업과 안 겹칩니다** — 가벼운 시작점                |
| [#24 아바타 삭제](https://github.com/seonghoho/LitMood/issues/24)             | 지울 수 없는 것보다 **교체할 때마다 이전 파일이 남는 쪽**이 큽니다               |
| [#15 생성 호출 코드·훅 도입](https://github.com/seonghoho/LitMood/issues/15)  | `#4` 의 남은 절반. operationId 정리 → react-query 버전 정합 순서로 막혀 있습니다 |
| [#9 무한 스크롤 + E2E](https://github.com/seonghoho/LitMood/issues/9)         | `#15` 뒤에 하면 `useInfiniteQuery` 가 생겨 일이 줄어듭니다                       |
| [#7 컬렉션 편집 보강](https://github.com/seonghoho/LitMood/issues/7)          | 노트 **수정** API 가 없습니다 (도메인 `changeNote()` 는 이미 있음)               |
| [#11 검색에서 기존 기록 열기](https://github.com/seonghoho/LitMood/issues/11) | `GET /records/me/by-content` 를 새로 만들어야 합니다                             |

**③ M5 운영 준비** — [09-milestone-5.md](09-milestone-5.md)에 상세 명세

### 이번 라운드에 끝난 것

[#22 타임라인 필터](https://github.com/seonghoho/LitMood/issues/22) — 무드·별점 필터가 없어
**P0 인 F-04-02 가 반쯤 비어 있었습니다.** 쿼리는 처음부터 여섯 가지를 다 받고 있었고 화면만
셋을 보내고 있었습니다. 쿼리 조립은 순수 함수로 떼어 테스트했습니다(`timeline-filter.ts`) —
무드는 정규화된 `name` 으로 대조되므로 `displayName` 을 보내면 **400 도 아니고 조용히 0건**이
됩니다.

[#19 컬렉션 좋아요](https://github.com/seonghoho/LitMood/issues/19) — 엔티티에는 `like_count` 가
있는데 응답에 없었습니다. `CollectionSummary` 에는 `likeCount` 만 넣고 `likedByMe` 는 넣지
않았습니다 — 그 목록이 나가는 공개 프로필은 인증 없이 캐시되므로 조회자별 값을 실으면 샙니다.

[#17 차단](https://github.com/seonghoho/LitMood/issues/17) — `blockedByMe` 는 **단방향**입니다.
가림은 양방향이어야 하지만 상대가 나를 차단한 것을 내가 풀 수는 없습니다. 그리고 백엔드는
이미 404 를 주고 있었는데 **프론트가 그것을 삼키고 있었습니다** — 목록을 올려주기만 하고
내려주지는 않아, 차단해도 상대 기록이 계속 보였습니다.

[#18 신고](https://github.com/seonghoho/LitMood/issues/18) — 대상을 경로가 가리키게 바꿨습니다
(`POST /records/{id}/report`, `/collections/{slug}/report`, `/users/@{handle}/report`).
`targetId` 가 `Long` 이라 화면이 아는 slug·handle 로는 지정할 수 없었습니다. 접수된 신고를
**보는** 화면은 [#28](https://github.com/seonghoho/LitMood/issues/28) 로 뗐고, 그때까지 DB 로
확인하는 절차를 [09-milestone-5.md](09-milestone-5.md) M5-6 에 적었습니다.

[#20 랜딩·인기 콘텐츠](https://github.com/seonghoho/LitMood/issues/20) — Redis 는 점수를 이미
주고 있었는데 `top()` 이 버리고 있었습니다. 무드 탐색의 `RankedContent` 와 합치지 않은 이유는
그쪽만 평균 별점을 낼 수 있어서입니다 — 묶으면 영구히 null 인 필드가 계약에 남습니다.
대신 화면 컴포넌트(`RankedContentList`)를 공유합니다.

앞선 라운드에서 [#3 프로필 편집](https://github.com/seonghoho/LitMood/issues/3),
[#5 기록 수정·삭제](https://github.com/seonghoho/LitMood/issues/5),
[#6 소비 맥락·스포일러](https://github.com/seonghoho/LitMood/issues/6)를 끝냈습니다. 그때
**PATCH 가 넣지 않은 필드를 지우던 버그**를 고쳐 규칙이 하나가 됐습니다 — 넣지 않은 필드는
변경되지 않고, 지우려면 문자열은 빈 문자열, 별점은 `clearRating`, 날짜는 `clearStartedAt`·
`clearFinishedAt` 을 씁니다. 아바타만 이 규칙에서 빠져 있습니다
([#24](https://github.com/seonghoho/LitMood/issues/24)).

### 판단이 필요한 열린 질문

작업을 이어받는 사람이 결정해야 할 것들입니다. 각 이슈에 배경을 적어 두었습니다.
이번 라운드에 닫힌 질문(차단 범위, 신고 대상 주소 지정, 신고 열람 경로, 별점 필터의 처리)은
결정한 내용과 함께 각 PR 에 남겼습니다.

1. **소셜 계정과 이메일 계정의 연결 정책** ([#2](https://github.com/seonghoho/LitMood/issues/2)) — 자동 연결은 편하지만 계정 탈취 경로가 될 수 있습니다
2. **관리자를 어떻게 임명할 것인가** ([#28](https://github.com/seonghoho/LitMood/issues/28)) — `users.role` 컬럼 / 환경변수 목록 / 별도 테이블. 운영자가 한둘인 동안은 환경변수가 무난해 보입니다
3. **탈퇴자의 공개 컬렉션 처리** ([#8](https://github.com/seonghoho/LitMood/issues/8)) — 정책대로면 링크가 404 가 됩니다
4. **Postgres·Redis 를 클러스터 안에 둘 것인가** ([#20](https://github.com/seonghoho/LitMood/issues/20), M5-2) — `infra/k8s/base/` 에는 **둘 다 없습니다.** 배포하면 인기 섹션이 빈 채로 보입니다
5. **react-query 를 올릴 것인가, orval 을 올릴 것인가** ([#15](https://github.com/seonghoho/LitMood/issues/15)) — 훅 생성이 이 버전 불일치에 막혀 있습니다
6. **기간 필터의 기준이 무엇인가** ([#22](https://github.com/seonghoho/LitMood/issues/22)) — 지금은 `created_at` 이라 라벨을 "기록한 날"로 정직하게 썼습니다. `startedAt`·`finishedAt` 기준으로 옮길지는 정해지지 않았습니다
7. **아바타 고아 객체를 어떻게 치울 것인가** ([#24](https://github.com/seonghoho/LitMood/issues/24)) — 동기 삭제(실패는 로그만) / 주기적 청소. 후자는 스케줄러가 필요한데 `@Scheduled` 사용처가 0건입니다
8. **핸들 변경 허용 여부** ([#3](https://github.com/seonghoho/LitMood/issues/3)) — 공개 URL 이 바뀌면 공유된 링크가 깨집니다

---

## 4. 알아 두면 좋은 맥락

### 왜 이렇게 만들었나

기술 선택의 근거는 [02-tech-decisions.md](02-tech-decisions.md)에 ADR 10건으로 정리돼 있습니다.
**"요즘 뜨니까"가 아니라 이 서비스의 요구사항에서 도출된 이유만** 적었습니다.
바꾸고 싶다면 해당 ADR 의 근거가 여전히 유효한지 먼저 확인하세요.

특히 아래 셋은 실제로 값을 했습니다:

- **Next.js** — 공개 프로필·컬렉션의 SSR 과 `next/og` 동적 OG 이미지. 별도 이미지 서버가 없습니다
- **Panda CSS** — Emotion 이었다면 `"use client"` 가 전염돼 RSC 를 못 썼을 겁니다
- **Java 21 가상 스레드** — 외부 API 3개 병렬 호출을 리액티브 오염 없이 평범한 명령형 코드로 씁니다

### API 가 있다고 화면이 있는 것은 아니다

M4 에서 소셜 API 를 한 번에 만들고 화면은 일부만 붙였는데, 그 사실이 로드맵에는
"M4 ✅" 로만 남아 **미배선분이 드러나지 않았습니다**. 마일스톤 체크가 아니라 호출부를
직접 세는 편이 정확합니다:

```bash
# 백엔드가 노출하는 엔드포인트
grep -rn "@\(Get\|Post\|Put\|Patch\|Delete\)Mapping" apps/api/src/main/java/com/litmood/interfaces/controller/

# 프론트가 실제로 부르는 것
grep -rn "api/v1" apps/web/src --include="*.ts" --include="*.tsx" | grep -v generated
```

처음 세었을 때 엔드포인트 28개 중 **8개가 미배선**이었고, 파라미터 단위로도 어긋나 있었습니다 —
`GET /records/me` 는 `moods`·`minRating` 을 받는데 화면이 안 보내 F-04-02(P0)가 반쯤 비어
있었습니다. 지금은 모두 이어졌지만, **다음에 API 를 먼저 만들면 같은 일이 또 생깁니다.**
마일스톤 체크는 "만들었나"만 말합니다.

### 이미 밟은 지뢰

같은 함정을 다시 밟지 않도록 [CLAUDE.md](../CLAUDE.md)의 "자주 걸리는 함정" 표를 보세요.
전부 실제로 터졌던 것들입니다.

### 스펙이 계약을 다 말하지 않으면 코드젠은 손해다

#4 에서 배운 것입니다. springdoc 은 애노테이션이 없으면 **모든 프로퍼티를 optional 로** 뽑습니다.
그 스펙으로 타입을 생성하면 `rating: number | null` 이 `rating?: number` 가 되어, 손으로 쓴
타입이 이미 갖고 있던 정보를 오히려 잃습니다. 그래서 응답 DTO 에 `required`/`nullable` 을 먼저
명시했습니다 — 구체적인 규칙은 [CLAUDE.md](../CLAUDE.md)에 있습니다.

두 가지가 특히 시간을 먹었습니다.

- **`@Schema(nullable = true)` 는 OpenAPI 3.1 에서 아무 일도 하지 않습니다.** 유효한 키워드가
  아니라 springdoc 이 조용히 버립니다. 3.1 표기는 `types = {"string", "null"}` 처럼 `type` 을
  배열로 주는 것입니다. **애노테이션을 대량으로 붙이기 전에 한두 필드로 먼저 확인하세요** —
  100개를 붙이고 나서 안 먹는 걸 알면 되돌리는 비용이 큽니다.
- **`ModelResolver` 빈을 직접 만들면 스키마가 망가집니다.** enum 을 공유 스키마로 내보내려고
  빈을 대체했더니 springdoc 이 자기 ObjectMapper 로 구성해 둔 리졸버가 밀려나 `type: object` 가
  31개에서 4개로 줄었습니다. `OpenApiConfig` 처럼 `@PostConstruct` 로 플래그만 켜세요.

### 캐싱과 개인화의 경계

공개 페이지(`/@handle`, `/collections/[slug]`)는 SEO 를 위해 캐시되고, **그 서버 렌더링은 인증 없이**
수행됩니다. 따라서 조회자별로 달라지는 것(팔로우 상태, FOLLOWERS 기록)은 서버가 아니라
**클라이언트가 자신의 세션으로 다시 조회**합니다. M4 에서 이 경계를 잘못 잡아 버그가 났었습니다.
새 화면을 만들 때 이 규칙을 [03-architecture.md](03-architecture.md)의 화면 유형별 표에서 확인하세요.

---

## 5. 이 세션에서 하지 않은 것

정직하게 남깁니다.

- **실제 외부 API 로는 검증하지 않았습니다.** 네이버·TMDB·Spotify 모두 스텁으로만 확인했습니다.
  응답 형태는 문서를 보고 맞췄지만, 실제 키를 넣었을 때 예외가 나올 수 있습니다
- **K8s 매니페스트는 `kustomize build` 로 문법만 검증했습니다.** 실제 클러스터에 배포한 적 없습니다
- **프론트엔드 테스트는 순수 로직만 있습니다.** jsdom·testing-library 가 없어 컴포넌트 렌더링은
  검증하지 못하고 브라우저로 수동 확인합니다 ([이슈 #9](https://github.com/seonghoho/LitMood/issues/9))
- **아바타 업로드는 로컬 MinIO 로만 검증했습니다.** 실제 S3 나 프로덕션 버킷 정책은 확인하지 않았습니다
- **성능·부하 테스트를 하지 않았습니다.** NFR-01(p95 < 300ms)은 측정되지 않은 목표입니다
- **`infra/k8s/base/` 에 Postgres 도 Redis 도 없습니다.** api·web 의 Deployment·Service·Ingress·HPA·
  ConfigMap 뿐입니다. 지금 상태로 배포하면 검색 캐시·리프레시 토큰·인기 랭킹이 갈 곳이 없습니다
- **Rate limit 구현이 0건입니다.** [05-api-spec.md](05-api-spec.md)에 정책(인증 600/min, 비인증
  60/min, 검색 30/min)만 있습니다. 신고·좋아요처럼 악용되기 쉬운 엔드포인트가 그대로 열려 있습니다
- **커스텀 메트릭이 0건입니다.** `MeterRegistry`·`@Timed`·`Counter` 사용처가 없어 기본 메트릭만
  나옵니다. 특히 `PopularityRanking` 은 실패를 조용히 삼키도록 설계돼 있어(의도) **Redis 가 죽어도
  아무도 모릅니다**. 로그도 아직 평문입니다
- **관리자 권한 개념이 없습니다.** 전원 `ROLE_USER` 라 `reports` 에 쌓인 신고를 볼 수단이
  없습니다 ([#28](https://github.com/seonghoho/LitMood/issues/28)). 그때까지의 절차는
  [09-milestone-5.md](09-milestone-5.md) M5-6 에 적어 뒀습니다
- **인기 랭킹은 Redis 를 재시작하면 사라집니다.** 인메모리이고 기록 생성 시점에만 쌓이므로,
  다시 채워질 때까지 랜딩의 인기 섹션이 비어 있습니다. 배포 직후에도 마찬가지입니다
