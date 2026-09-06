# 08. 인계 문서

> **다음 세션 · 다른 PC 에서 이어받는 사람을 위한 문서입니다.**
> 이 문서 하나로 지금까지의 상태를 파악하고 작업을 이어갈 수 있어야 합니다.

최종 갱신: **2026-09-06** (M4 미배선분 점검, 이슈 #17~#20 등록 시점)

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

**동작하는 것**: 가입 → 검색 → 기록 → 타임라인 → 공개 프로필 → 컬렉션 공유(OG 이미지) →
무드 탐색 → 팔로우 → 피드 → 기록 좋아요.

**M4 소셜의 절반은 화면이 없습니다.** API 는 `fc5a4e2` 에서 다 만들었는데 프론트 호출부가
0건입니다 — 차단([#17](https://github.com/seonghoho/LitMood/issues/17)),
신고([#18](https://github.com/seonghoho/LitMood/issues/18)),
컬렉션 좋아요([#19](https://github.com/seonghoho/LitMood/issues/19)),
인기 콘텐츠([#20](https://github.com/seonghoho/LitMood/issues/20)).
넷 다 **프론트만으로는 끝나지 않습니다** — 응답 DTO 가 조회자별 상태를 말하지 않거나
(`blockedByMe`, 컬렉션 `likeCount`/`likedByMe`), 신고 대상을 주소로 지정할 수 없거나
(`targetId` 가 `Long` 인데 컬렉션은 slug·사용자는 handle), 순위 점수를 버리고 있습니다.
자세한 것은 각 이슈에 적었습니다.

랜딩 페이지(`app/page.tsx`)는 아직 **M0 스켈레톤**입니다. 비로그인 유입 경로인데 볼 것이 없습니다.

**API 계약 파이프라인이 이어졌습니다** (ADR-008, [#4](https://github.com/seonghoho/LitMood/issues/4)).
프론트의 모델 타입은 이제 백엔드 DTO 에서 생성됩니다 — `features/*/types.ts` 는 재수출일 뿐이니
손으로 고치지 마세요. 호출 코드까지 옮기는 일은 [#15](https://github.com/seonghoho/LitMood/issues/15)
로 남아 있습니다.

**미완**: [GitHub Issues](https://github.com/seonghoho/LitMood/issues) 14건에 상세히 적어 두었습니다.
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

**① 먼저 처리해야 하는 것**

| 이슈                                                                          | 이유                                                                                       |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| [#1 네이버 키 재발급](https://github.com/seonghoho/LitMood/issues/1)          | 보안. 코드 작업이 아니라 **개발자센터에서 재발급**하는 일입니다                            |
| [#22 타임라인 무드·별점 필터](https://github.com/seonghoho/LitMood/issues/22) | P0 인데 반쯤 비어 있습니다. 쿼리는 완성돼 있어 **백엔드 선행 작업이 없는 유일한 건**입니다 |

[#3 프로필 편집](https://github.com/seonghoho/LitMood/issues/3)은 완료했습니다 —
`/settings` 화면, `PATCH /users/me`, presigned 아바타 업로드. 핸들 변경은 여전히 제외입니다
(공개 URL 이 바뀌면 공유된 링크가 깨집니다). 아바타 **삭제**는 아직 없습니다.

[#5 기록 수정·삭제](https://github.com/seonghoho/LitMood/issues/5)도 완료했습니다 — 타임라인
카드의 수정·삭제 진입점, 겸용이 된 `RecordDialog`, 삭제 확인. 다만 검색 결과에서 이미 기록한
콘텐츠를 눌렀을 때 수정 모달을 여는 항목은 백엔드가 필요해
[#11](https://github.com/seonghoho/LitMood/issues/11)로 분리했습니다.

[#6 소비 맥락·스포일러](https://github.com/seonghoho/LitMood/issues/6)도 완료했습니다 —
기록 다이얼로그의 접이식 "자세히" 섹션(언제·어디서·다시 본 횟수·스포일러), 스포일러 블러,
타임라인 기간 필터.

이 과정에서 **PATCH 가 넣지 않은 필드를 지우던 버그**를 고쳤습니다. 이제 규칙은 하나입니다 —
넣지 않은 필드는 변경되지 않고, 지우려면 문자열은 빈 문자열, 별점은 `clearRating`,
날짜는 `clearStartedAt`·`clearFinishedAt` 을 씁니다.

또 DB 에만 있고 도메인에는 없던 규칙(`started_at <= finished_at`)을 엔티티에도 넣었습니다.
날짜 입력 화면이 생기면서 사용자가 순서를 뒤집으면 제약 위반이 그대로 500 이 될 수 있었습니다.

**② M4 의 미배선분** — 만들어 둔 API 에 화면을 붙이는 일. 넷 다 백엔드 선행 작업이 있습니다

| 이슈                                                                     | 선행 작업 (백엔드)                                                                              |
| ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------- |
| [#19 컬렉션 좋아요](https://github.com/seonghoho/LitMood/issues/19)      | `CollectionResponse` 에 `likeCount`·`likedByMe` 싣기. **가장 얇으니 여기서 시작하세요**         |
| [#17 차단 UI](https://github.com/seonghoho/LitMood/issues/17)            | `PublicProfile` 에 `blockedByMe`. 차단이 공개 프로필·공개 기록 목록에는 적용되지 않는 것도 함께 |
| [#18 신고 UI](https://github.com/seonghoho/LitMood/issues/18)            | `targetId` 로는 컬렉션·사용자를 지정할 수 없음. 신고를 **볼 방법**이 없는 것이 더 큰 문제       |
| [#20 랜딩 + 인기 콘텐츠](https://github.com/seonghoho/LitMood/issues/20) | `/discover/popular` 이 순위 점수를 버림 + N+1. 클러스터에 Redis 가 없는 것이 선결               |

**③ 그다음**

| 이슈                                                                         | 이유                                                                          |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| [#2 OAuth2 소셜 로그인](https://github.com/seonghoho/LitMood/issues/2)       | P0인데 미완. 카카오 앱 키 발급이 선행돼야 합니다                              |
| [#8 회원 탈퇴](https://github.com/seonghoho/LitMood/issues/8)                | P1. 탈퇴자의 공개 컬렉션 처리 정책을 먼저 정해야 합니다                       |
| [#15 생성 호출 코드·훅 도입](https://github.com/seonghoho/LitMood/issues/15) | #4 의 남은 절반. 모델 타입만 옮겨 놨고 호출은 아직 수동입니다                 |
| [#23 리스트 ↔ 그리드 뷰](https://github.com/seonghoho/LitMood/issues/23)     | F-04-03(P1). 순수 프론트 작업이고 응답에 `coverUrl` 이 이미 있습니다          |
| [#24 아바타 삭제](https://github.com/seonghoho/LitMood/issues/24)            | 지울 수 없는 것도 문제지만, **교체할 때마다 이전 파일이 스토리지에 남습니다** |

**④ M5 운영 준비** — [09-milestone-5.md](09-milestone-5.md)에 상세 명세

### 판단이 필요한 열린 질문

작업을 이어받는 사람이 결정해야 할 것들입니다. 각 이슈에 배경을 적어 두었습니다.

1. **소셜 계정과 이메일 계정의 연결 정책** (#2) — 자동 연결은 편하지만 계정 탈취 경로가 될 수 있습니다
2. **react-query 를 올릴 것인가, orval 을 올릴 것인가** ([#15](https://github.com/seonghoho/LitMood/issues/15)) — 훅 생성이 이 버전 불일치에 막혀 있습니다. 어느 쪽을 움직일지는 확인이 필요합니다
3. **핸들 변경 허용 여부** (#3) — 공개 URL 이 바뀌면 공유된 링크가 깨집니다
4. **탈퇴자의 공개 컬렉션 처리** (#8) — 정책대로면 링크가 404 가 됩니다
5. **차단을 어디까지 적용할 것인가** ([#17](https://github.com/seonghoho/LitMood/issues/17)) — 지금은 피드·기록 단건·컬렉션 조회만 막힙니다. 공개 프로필과 무드·인기 집계는 그대로 보입니다
6. **신고 대상을 어떻게 주소 지정할 것인가** ([#18](https://github.com/seonghoho/LitMood/issues/18)) — 컬렉션 응답에 숫자 id 를 노출할지, `POST /collections/{slug}/report` 를 따로 둘지
7. **신고를 누가 본다는 것인가** ([#18](https://github.com/seonghoho/LitMood/issues/18), M5-6) — 관리자 권한 개념이 없습니다. 화면을 만들지, 당분간 DB 를 직접 보는 절차를 문서화할지
8. **Redis 를 클러스터 안에 둘 것인가** ([#20](https://github.com/seonghoho/LitMood/issues/20), M5-2) — DB 와 같은 결정입니다. 지금 `infra/k8s/base/` 에는 **둘 다 없습니다**
9. **별점 필터가 별점 없는 기록을 지워도 되는가** ([#22](https://github.com/seonghoho/LitMood/issues/22)) — `rating >= x` 라 null 인 행이 탈락합니다. "별점이 아니라 맥락으로 기록한다" 는 이 서비스의 전제와 정면으로 부딪힙니다
10. **기간 필터의 기준이 무엇인가** ([#22](https://github.com/seonghoho/LitMood/issues/22)) — 지금은 `created_at` 입니다. #6 에서 `startedAt`·`finishedAt` 을 받기 시작했으므로 사용자는 "언제 **봤는지**" 를 기대할 수 있습니다

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

2026-09-06 기준 엔드포인트 28개 중 **8개가 미배선**이었습니다. 파라미터 단위로도 어긋납니다 —
`GET /records/me` 는 `moods`·`minRating` 필터를 받는데 화면에는 없어서 F-04-02(P0)가
반쯤 비어 있습니다 ([#22](https://github.com/seonghoho/LitMood/issues/22)).

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
- **관리자 권한 개념이 없습니다.** 전원 `ROLE_USER` 라 `reports` 에 쌓인 신고를 볼 수단이 없습니다
