# 08. 인계 문서

> **다음 세션 · 다른 PC 에서 이어받는 사람을 위한 문서입니다.**
> 이 문서 하나로 지금까지의 상태를 파악하고 작업을 이어갈 수 있어야 합니다.

최종 갱신: **2026-09-07** (이슈 #17~#20 · #22 · #23 · #24 · #11 · #7 · #28 완료 시점)

---

## 1. 지금 어디까지 왔나

[로드맵](07-roadmap.md) 기준 **M0~M4 완료, M5 착수(신고 처리만)**.

| 마일스톤              | 상태 | 커밋                                                         |
| --------------------- | ---- | ------------------------------------------------------------ |
| M0 기반 구축          | ✅   | `9257a01`                                                    |
| M1 인증 + 콘텐츠 검색 | ✅   | `909e347`                                                    |
| M2 기록 (핵심 경로)   | ✅   | `3e45154`                                                    |
| M3 컬렉션 + 공유      | ✅   | `c28977d`                                                    |
| M4 소셜               | ✅   | `fc5a4e2`                                                    |
| M5 운영 준비          | 🟡   | M5-6 의 신고 처리만 ([09-milestone-5.md](09-milestone-5.md)) |

**동작하는 것**: 랜딩(인기 콘텐츠) → 가입 → 검색 → 기록 → 타임라인(무드·별점·기간 필터) →
공개 프로필 → 컬렉션 공유(OG 이미지) → 무드 탐색 → 팔로우 → 피드 → 좋아요(기록·컬렉션) →
차단 → 신고 → **신고 처리(운영자)**.

**M4 소셜의 미배선분을 모두 붙였습니다** — 차단([#17](https://github.com/seonghoho/LitMood/issues/17)),
신고([#18](https://github.com/seonghoho/LitMood/issues/18)),
컬렉션 좋아요([#19](https://github.com/seonghoho/LitMood/issues/19)),
랜딩·인기 콘텐츠([#20](https://github.com/seonghoho/LitMood/issues/20)).
넷 다 화면만으로 끝나지 않았고 응답 계약을 함께 고쳤습니다. 자세한 것은 3절에 있습니다.

**새 API 가 앞에 있던 둘도 끝났습니다** — 검색에서 기존 기록 열기
([#11](https://github.com/seonghoho/LitMood/issues/11)), 컬렉션 편집 보강
([#7](https://github.com/seonghoho/LitMood/issues/7)). 둘 다 백엔드를 새로 만들어야 했고,
`#7` 에서는 **기록에서 이미 고쳤던 부분 수정 버그가 컬렉션에 그대로 남아 있는 것**을 함께
고쳤습니다.

**첫 운영 권한이 생겼습니다** ([#28](https://github.com/seonghoho/LitMood/issues/28)).
`reports` 에 쌓이기만 하던 신고를 `/admin/reports` 에서 보고 처리합니다. 운영자는
**환경변수 `ADMIN_HANDLES`** 의 핸들 목록입니다 — 자세한 것은 3절에 있습니다.
M5 의 첫 항목이 이것이라 **M5 가 미착수는 아닙니다.**

**남은 5건은 전부 선행 조건이 있습니다** — 셋은 코드 밖 결정이나 외부 발급이고, 둘은
`#2`(소셜 로그인)와 버전 결정 뒤입니다. 아래 3절에 무엇이 막고 있는지 적었습니다.

**API 계약 파이프라인이 이어졌습니다** (ADR-008, [#4](https://github.com/seonghoho/LitMood/issues/4)).
프론트의 모델 타입은 이제 백엔드 DTO 에서 생성됩니다 — `features/*/types.ts` 는 재수출일 뿐이니
손으로 고치지 마세요. 호출 코드까지 옮기는 일은 [#15](https://github.com/seonghoho/LitMood/issues/15)
로 남아 있습니다.

**미완**: [GitHub Issues](https://github.com/seonghoho/LitMood/issues) 5건에 상세히 적어 두었습니다.
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

| 이슈                                                                   | 먼저 정하거나 받아야 하는 것                                             |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| [#1 네이버 키 재발급](https://github.com/seonghoho/LitMood/issues/1)   | 코드 작업이 아닙니다. **개발자센터에서 재발급**                          |
| [#2 OAuth2 소셜 로그인](https://github.com/seonghoho/LitMood/issues/2) | 카카오·구글 앱 키. 그리고 같은 이메일의 소셜/이메일 계정을 연결할지 말지 |
| [#8 회원 탈퇴](https://github.com/seonghoho/LitMood/issues/8)          | 탈퇴자의 공개 컬렉션 처리 정책. 30일 배치를 돌릴 스케줄러도 없습니다     |

**② P2 — 남은 둘. 순서가 정해져 있습니다**

`#2` 가 들어오면 로그인 경로가 하나 더 생깁니다. `#9`(E2E)와 `#15`(호출 코드 이관)는 인증
흐름을 정면으로 건드리므로 **`#2` 뒤에 하는 편이 다시 손대는 일을 줄입니다.**

`#2` 를 기다리지 않아도 되던 `#7`·`#11`·`#28` 은 끝났습니다. 남은 둘은
서로도 순서가 있습니다 — `#15` 가 먼저입니다.

| 이슈                                                                         | 앞에 있는 것                                                               |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| [#15 생성 호출 코드·훅 도입](https://github.com/seonghoho/LitMood/issues/15) | `#2`. 그리고 operationId 정리 → react-query 버전 정합 순서로 막혀 있습니다 |
| [#9 무한 스크롤 + E2E](https://github.com/seonghoho/LitMood/issues/9)        | `#15`. 뒤에 하면 `useInfiniteQuery` 가 생겨 일이 줄어듭니다                |

**③ M5 운영 준비** — [09-milestone-5.md](09-milestone-5.md)에 상세 명세

### 이번 라운드에 끝난 것

[#28 신고 처리 화면 + 관리자 권한](https://github.com/seonghoho/LitMood/issues/28) — 신고를
**접수하는** 길은 `#18` 에서 만들었지만, 쌓인 것을 볼 방법이 DB 직접 조회뿐이었습니다.

**관리자 임명은 환경변수 `ADMIN_HANDLES`(핸들 목록, 쉼표 구분)로 갔습니다** — 이슈에 열려
있던 결정입니다. `users.role` 컬럼은 권한 변경이 DB 직접 수정이라 흔적이 남지 않고, 별도
`admins` 테이블은 운영자가 한둘인 지금 과합니다. 환경변수는 시크릿과 같은 경로로 관리되고
권한이 코드에도 데이터에도 눌러앉지 않습니다. 대가는 둘입니다 — **부여·회수에 재배포가
필요하고**, 회수는 이미 발급된 access 토큰이 만료된 뒤(15분) 적용됩니다. 운영자 판정을
JWT 클레임이 아니라 **요청마다 설정에서** 하는 이유가 그것입니다. 클레임에 넣었다면 refresh
TTL(2주)만큼 권한이 살아남습니다.

**핸들 비교는 대소문자를 구분합니다.** 가입 규칙이 대소문자를 보존하므로 `admin` 과 `Admin`
은 서로 다른 계정이고, 느슨하게 맞추면 비슷한 핸들을 만든 사람에게 권한이 샙니다. 그리고
**없는 핸들을 적어 두면 기동 로그에 경고가 남습니다** — 핸들은 가입 순서로 임자가 정해지므로,
오타를 방치하면 그 이름으로 먼저 가입하는 사람이 운영자가 됩니다.

**운영자가 아니면 403 이 아니라 404 입니다** (저장소 규칙). 통제는 `SecurityConfig` 의
`/api/v1/admin/**` 한 줄이고, 그 403 을 `RestAuthenticationEntryPoint` 가 admin 경로에 한해
404 로 바꿉니다 — 컨트롤러에 검사를 하나 더 두면 관문이 둘이 되어 어긋날 자리가 생깁니다.

**목록은 신고 건별로 평면입니다**(이슈에 열려 있던 결정). 같은 대상의 신고를 묶으면 서로 다른
사유가 접혀 판단 근거가 사라지고, 상태 전이는 어차피 건별이라 묶음 버튼과 어긋납니다. 대신
각 건에 `sameTargetCount` 를 실어 "여러 사람이 같은 것을 신고했다"는 신호는 목록에서 바로
보이게 했습니다. 대상은 이름까지 채워 보냅니다 — `targetType` + `targetId` 만 주면 운영자가
대상마다 다시 조회해야 합니다. **지워진 대상도 큐에 남습니다**(`deleted: true`) — 조치할 것이
없다는 사실도 정보입니다.

**처리 결과에 따른 조치(기록 숨김·계정 정지)는 범위 밖으로 뒀습니다.** 정지는 상태·해제 경로·
이의 절차가 따라오는 별개의 기능이고, 지금 필요한 것은 큐를 읽고 비우는 길입니다.
`Report.resolve()` 는 **한 번만 처리한다**는 불변식을 지킵니다 — 이미 처리된 건을 뒤집으면
`resolvedAt` 이 "언제 판단했는가"를 더 이상 말하지 못합니다. DB 에도 같은 규칙을 CHECK 제약
으로 걸었습니다(`V4__report_resolution.sql`).

접수량은 `litmood.reports.received` 카운터로 나갑니다(M5-4). **Rate limit(M5-6)은 여전히
없습니다** — Redis 토큰 버킷을 공용으로 놓는 일이라 이 이슈에 끼워 넣지 않았습니다.

[#11 검색에서 기존 기록 열기](https://github.com/seonghoho/LitMood/issues/11) — 검색 화면의
"기록됨"은 **이번 세션에 만든 것만** 담는 로컬 `Set` 이었습니다. 예전에 기록한 콘텐츠는 눌러도
생성 모달이 열렸고, 저장하면 불변식 1 에 걸려 409 를 받은 뒤 "기존 기록을 수정해 주세요"라는
안내만 나왔습니다 — **그 기존 기록으로 가는 길은 화면에 없었습니다.** 검색 응답에 실을 수 없는
정보라(캐시로 샙니다) 화면이 자기 세션으로 따로 묻습니다. **배치 조회로 갔습니다** — 한 건씩
물었다면 결과 한 화면에 왕복 스무 번과 404 스무 번이 납니다. 기록이 없는 콘텐츠는 404 가 아니라
응답에서 빠집니다. 조회에 `resolveOrCreate` 를 쓰지 않은 이유는, 그랬다면 **검색 결과를 훑는
것만으로** 자체 DB 에 콘텐츠가 쌓이고 외부 provider 호출이 따라 붙기 때문입니다.

[#7 컬렉션 편집 보강](https://github.com/seonghoho/LitMood/issues/7) — 담은 이유는 담을 때만
넣을 수 있었습니다. 도메인의 `changeNote()` 는 M3 부터 있었고 **그 앞의 컨트롤러가 없었습니다.**
`PATCH /collections/{slug}/items/{contentId}` 를 열면서, `Collection.edit` 이 **넣지 않은 필드를
지우던 버그**를 함께 고쳤습니다 — 기록에서 이미 고쳤던 것과 같은 버그가 컬렉션에 남아 있어
제목만 고치는 요청이 설명과 커버를 날렸습니다. 이제 규칙이 둘 다 같습니다. 응답에 `coverPinned`
를 더한 이유는 `coverUrl` 만으로는 "자동(첫 아이템)"과 "첫 아이템 표지를 직접 고름"을 구분할 수
없어서입니다 — 값이 같아도 순서를 바꾸면 결과가 달라집니다. **드래그 정렬은 넣지 않기로
했습니다**(이슈에 열려 있던 결정): 키보드 조작(NFR-05), 터치 드래그와 스크롤 충돌, 번들 증가.
아이템이 스물을 넘기 시작하면 재검토합니다 — 이유를 코드 주석에 남겨 "빠뜨린 것"으로 읽히지
않게 했습니다.

### 앞선 라운드에 끝난 것

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

[#23 리스트 ↔ 그리드 뷰](https://github.com/seonghoho/LitMood/issues/23) — 표지가 곧 식별자인
콘텐츠라 커버 그리드가 "내가 뭘 봤더라"를 빨리 답해 줍니다. 선택은 `localStorage` 에 두고
타임라인·프로필이 함께 씁니다. 그리드에는 한줄평을 싣지 않아 **스포일러가 새어 나갈 일도
없습니다.** 화면에서 두 가지를 고쳤습니다 — 리셋의 `img { height: auto }` 가 유틸리티를 이겨
커버가 상자를 안 채우던 것(CLAUDE.md 함정 표에 추가), 죽은 커버 URL 이 깨진 이미지로 보이던 것.

[#24 아바타 삭제](https://github.com/seonghoho/LitMood/issues/24) — "지울 수 없다"는 문제의
절반이었습니다. **프론트도 비울 때 `null` 을 보내고 있었고, 서버는 `null` 을 "변경 없음"으로
읽습니다** — 백엔드만 고쳤으면 지운 것이 조용히 남았을 겁니다. 그리고 지울 때뿐 아니라
**교체할 때마다 이전 객체가 스토리지에 남고 있었습니다.** 정리 실패는 삼키고 warn 만 남깁니다
(MinIO 를 내려 두고 확인했습니다 — PATCH 는 200 으로 끝나고 로그에 남습니다).
주기적 청소로 가지 않은 이유는 스케줄러가 없어서입니다 — `#8` 이 부딪히는 것과 같은 벽입니다.

그보다 앞선 라운드에서 [#3 프로필 편집](https://github.com/seonghoho/LitMood/issues/3),
[#5 기록 수정·삭제](https://github.com/seonghoho/LitMood/issues/5),
[#6 소비 맥락·스포일러](https://github.com/seonghoho/LitMood/issues/6)를 끝냈습니다. 그때
**PATCH 가 넣지 않은 필드를 지우던 버그**를 고쳐 규칙이 하나가 됐습니다 — 넣지 않은 필드는
변경되지 않고, 지우려면 문자열은 빈 문자열, 별점은 `clearRating`, 날짜는 `clearStartedAt`·
`clearFinishedAt` 을 씁니다. 아바타는 `#24` 에서, 컬렉션은 `#7` 에서 이 규칙에 합류했습니다 —
어디에 아직 안 붙어 있는지는 4절의 "규칙을 한 곳에서 고쳐도 다른 곳에는 그대로 남아 있다"에
적어 뒀습니다.

### 판단이 필요한 열린 질문

작업을 이어받는 사람이 결정해야 할 것들입니다. 각 이슈에 배경을 적어 두었습니다.
이번 라운드에 닫힌 질문(관리자 임명 방식, 신고를 대상별로 묶을지, 단건 조회 대 배치 조회,
드래그 정렬 도입 여부, 커버 지정 방식)과
앞선 라운드의 것(차단 범위, 신고 대상 주소 지정, 신고 열람 경로, 별점 필터의 처리)은
결정한 내용과 함께 각 PR 에 남겼습니다.

1. **소셜 계정과 이메일 계정의 연결 정책** ([#2](https://github.com/seonghoho/LitMood/issues/2)) — 자동 연결은 편하지만 계정 탈취 경로가 될 수 있습니다
2. **탈퇴자의 공개 컬렉션 처리** ([#8](https://github.com/seonghoho/LitMood/issues/8)) — 정책대로면 링크가 404 가 됩니다
3. **Postgres·Redis 를 클러스터 안에 둘 것인가** ([#20](https://github.com/seonghoho/LitMood/issues/20), M5-2) — `infra/k8s/base/` 에는 **둘 다 없습니다.** 배포하면 인기 섹션이 빈 채로 보입니다
4. **react-query 를 올릴 것인가, orval 을 올릴 것인가** ([#15](https://github.com/seonghoho/LitMood/issues/15)) — 훅 생성이 이 버전 불일치에 막혀 있습니다
5. **기간 필터의 기준이 무엇인가** ([#22](https://github.com/seonghoho/LitMood/issues/22)) — 지금은 `created_at` 이라 라벨을 "기록한 날"로 정직하게 썼습니다. `startedAt`·`finishedAt` 기준으로 옮길지는 정해지지 않았습니다
6. **핸들 변경 허용 여부** ([#3](https://github.com/seonghoho/LitMood/issues/3)) — 공개 URL 이 바뀌면 공유된 링크가 깨집니다

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

### 규칙을 한 곳에서 고쳐도 다른 곳에는 그대로 남아 있다

PATCH 의 부분 수정 규칙("넣지 않은 필드는 변경되지 않는다, 지우려면 빈 문자열")을 기록에서
고쳤을 때([#5](https://github.com/seonghoho/LitMood/issues/5)) **컬렉션은 그대로였습니다.**
`Collection.edit` 은 `description`·`coverUrl` 을 그냥 대입하고 있어서 제목만 고치는 요청이
나머지를 날렸습니다. 편집 화면이 없어 아무도 밟지 않았을 뿐입니다
([#7](https://github.com/seonghoho/LitMood/issues/7) 에서 고쳤습니다).

같은 규칙이 **세 곳에 따로 구현돼 있습니다.** 규칙을 바꾸면 셋을 함께 봐야 합니다.

```bash
grep -rn "public void edit(\|updateProfile(" apps/api/src/main/java/com/litmood/domain/model/
```

- `Record.edit` — 엔티티가 규칙을 가짐 (`isBlank() ? null : value`)
- `Collection.edit` — 엔티티가 규칙을 가짐 (#7 에서 맞췄습니다)
- `User.updateProfile` — **규칙이 엔티티가 아니라 `UserService` 에 있습니다.** 서비스가
  `bio != null ? bio : user.getBio()` 로 합쳐 넘깁니다. 동작은 맞지만, 빈 문자열이 `null` 이
  아니라 `""` 로 저장되는 점이 나머지 둘과 다릅니다

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
- **아바타는 로컬 MinIO 로만 검증했습니다.** 실제 S3 나 프로덕션 버킷 정책은 확인한 적이 없습니다.
  특히 `#24` 로 **삭제**가 생겼는데 presign 은 되고 `s3:DeleteObject` 권한은 없는 구성이 흔합니다 —
  그 경우 조용히 실패하고(설계상 삼킵니다) 객체만 쌓입니다. 배포 전 버킷 정책을 확인하세요
- **성능·부하 테스트를 하지 않았습니다.** NFR-01(p95 < 300ms)은 측정되지 않은 목표입니다
- **`infra/k8s/base/` 에 Postgres 도 Redis 도 없습니다.** api·web 의 Deployment·Service·Ingress·HPA·
  ConfigMap 뿐입니다. 지금 상태로 배포하면 검색 캐시·리프레시 토큰·인기 랭킹이 갈 곳이 없습니다
- **Rate limit 구현이 0건입니다.** [05-api-spec.md](05-api-spec.md)에 정책(인증 600/min, 비인증
  60/min, 검색 30/min)만 있습니다. 신고·좋아요처럼 악용되기 쉬운 엔드포인트가 그대로 열려 있습니다.
  `#28` 에서도 끼워 넣지 않았습니다 — Redis 토큰 버킷을 공용으로 놓는 별도의 일입니다
- **운영 화면은 브라우저로만 확인했습니다.** 백엔드는 `AdminReportApiTest` 로 덮여 있지만,
  `/admin/reports` 자체는 컴포넌트 테스트가 없습니다 (프론트 테스트 부재와 같은 이유)
- **커스텀 메트릭이 하나뿐입니다.** `litmood.reports.received`(#28) 말고는 `@Timed`·`Counter`
  사용처가 없어 기본 메트릭만 나옵니다. 특히 `PopularityRanking` 은 실패를 조용히 삼키도록
  설계돼 있어(의도) **Redis 가 죽어도 아무도 모릅니다**. 로그도 아직 평문입니다
- **운영자가 할 수 있는 일은 신고 상태를 옮기는 것뿐입니다.** 기록 숨김·계정 정지 같은 조치가
  없어, 실제 조치는 대상으로 이동해 손으로 합니다. 그리고 **운영자 권한 변경에는 재배포가
  필요합니다**(`ADMIN_HANDLES`) — 급히 회수해야 하는 상황이라면 재배포 + access 토큰
  만료(15분)를 기다려야 합니다
- **검색 화면의 "이미 기록함" 조회에는 클라이언트 캐시가 없습니다.** 탭을 옮길 때마다 다시
  나갑니다 — 자체 DB 조회라 싸지만, [#15](https://github.com/seonghoho/LitMood/issues/15) 의
  react-query 훅이 들어오면 자연스럽게 캐시될 자리입니다
- **인기 랭킹은 Redis 를 재시작하면 사라집니다.** 인메모리이고 기록 생성 시점에만 쌓이므로,
  다시 채워질 때까지 랜딩의 인기 섹션이 비어 있습니다. 배포 직후에도 마찬가지입니다
