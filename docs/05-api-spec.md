# 05. API 명세 (v1)

- Base URL: `/api/v1`
- 인증: `Authorization: Bearer {accessToken}` (Refresh는 HttpOnly 쿠키)
- 에러: RFC 9457 Problem Details (`docs/03-architecture.md` 참조)
- 페이지네이션: **커서 기반** (`cursor`, `limit`) — 타임라인은 삽입이 잦아 offset은 중복/누락이 발생한다

> 이 문서는 사람이 읽는 요약이다. **기계 판독 원본은 `apps/api` 빌드 시 생성되는 `openapi.json`** 이며,
> 여기서 Postman 컬렉션과 `packages/api-client` 타입이 파생된다 (ADR-008).

---

## Auth

| Method | Path                               | 설명                                            | 인증 |
| ------ | ---------------------------------- | ----------------------------------------------- | ---- |
| POST   | `/auth/signup`                     | 이메일 가입                                     | —    |
| POST   | `/auth/login`                      | 이메일 로그인 → access 반환 + refresh 쿠키 설정 | —    |
| POST   | `/auth/refresh`                    | 토큰 회전 재발급                                | 쿠키 |
| POST   | `/auth/logout`                     | refresh 무효화                                  | 쿠키 |
| GET    | `/auth/oauth2/{provider}`          | OAuth2 시작 (`google`\|`kakao`)                 | —    |
| GET    | `/auth/oauth2/{provider}/callback` | 콜백 → 프론트로 리다이렉트                      | —    |

**POST `/auth/login`**

```jsonc
// Request
{ "email": "a@b.com", "password": "••••••••" }
// 200
{ "accessToken": "eyJ...", "expiresIn": 900,
  "user": { "id": 1, "handle": "seongho", "nickname": "성호", "avatarUrl": null } }
// Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Max-Age=1209600
```

---

## Users

| Method | Path                           | 설명                               | 인증 |
| ------ | ------------------------------ | ---------------------------------- | ---- |
| GET    | `/users/me`                    | 내 정보                            | ✅   |
| PATCH  | `/users/me`                    | 프로필 수정                        | ✅   |
| GET    | `/users/@{handle}`             | 공개 프로필 + 통계                 | —    |
| GET    | `/users/@{handle}/records`     | 공개 기록 목록                     | —    |
| GET    | `/users/@{handle}/collections` | 공개 컬렉션 목록                   | —    |
| POST   | `/users/@{handle}/follow`      | 팔로우                             | ✅   |
| DELETE | `/users/@{handle}/follow`      | 언팔로우                           | ✅   |
| POST   | `/users/me/avatar`             | 아바타 업로드 (presigned URL 발급) | ✅   |

---

## Contents

| Method | Path                     | 설명                    | 인증 |
| ------ | ------------------------ | ----------------------- | ---- |
| GET    | `/contents/search`       | 통합 검색               | —    |
| GET    | `/contents/{id}`         | 콘텐츠 상세 + 집계      | —    |
| GET    | `/contents/{id}/records` | 해당 콘텐츠의 공개 기록 | —    |

**GET `/contents/search`**

```
?q=노르웨이의숲&types=BOOK,MOVIE,MUSIC&limit=10
```

```jsonc
// 200 — provider별 부분 실패를 명시적으로 노출한다
{
  "results": {
    "BOOK": [
      {
        "provider": "NAVER_BOOK",
        "externalId": "9788937473135",
        "title": "노르웨이의 숲",
        "creators": ["무라카미 하루키"],
        "releasedOn": "2017-08-25",
        "coverUrl": "https://...",
        "metadata": { "isbn13": "9788937473135", "publisher": "민음사" },
      },
    ],
    "MOVIE": [/* ... */],
    "MUSIC": [],
  },
  "failedProviders": ["SPOTIFY"], // 타임아웃/장애 시. 클라이언트는 해당 탭에 재시도 UI 노출
  "cached": true,
}
```

---

## Records — 핵심

| Method | Path                 | 설명                    | 인증          |
| ------ | -------------------- | ----------------------- | ------------- |
| POST   | `/records`           | 기록 생성               | ✅            |
| GET    | `/records/me`        | 내 타임라인 (필터·커서) | ✅            |
| GET    | `/records/{id}`      | 기록 단건               | 공개범위 따름 |
| PATCH  | `/records/{id}`      | 수정                    | ✅ 소유자     |
| DELETE | `/records/{id}`      | 삭제 (soft)             | ✅ 소유자     |
| GET    | `/records/feed`      | 팔로잉 피드             | ✅            |
| POST   | `/records/{id}/like` | 좋아요                  | ✅            |
| DELETE | `/records/{id}/like` | 좋아요 취소             | ✅            |

**POST `/records`**

```jsonc
// Request — provider + externalId로 지정. 콘텐츠가 DB에 없으면 서버가 스냅샷 생성
{
  "provider": "NAVER_BOOK",
  "externalId": "9788937473135",
  "status": "DONE", // 필수
  "rating": 4.5, // 선택 (status=WANT면 400)
  "moods": ["새벽", "먹먹함"], // 선택, 최대 5
  "review": "스무 살의 문장으로 읽었을 때와 전혀 다르다.",
  "isSpoiler": false,
  "visibility": "PUBLIC", // 생략 시 사용자 기본값
  "startedAt": "2026-08-20",
  "finishedAt": "2026-09-01",
}
// 201 Location: /api/v1/records/1024
// 409 RECORD_DUPLICATE — 이미 기록한 콘텐츠 (PATCH로 유도)
```

**GET `/records/me`**

```
?types=BOOK,MUSIC&status=DONE&moods=새벽&minRating=4&from=2026-01-01&cursor=eyJ...&limit=20
```

```jsonc
{
  "items": [
    {
      "id": 1024,
      "status": "DONE",
      "rating": 4.5,
      "moods": [{ "name": "새벽", "color": "#2A3B6B" }],
      "review": "...",
      "visibility": "PUBLIC",
      "content": { "id": 42, "type": "BOOK", "title": "노르웨이의 숲", "coverUrl": "..." },
      "likeCount": 3,
      "createdAt": "2026-09-01T04:12:00Z",
    },
  ],
  "nextCursor": "eyJpZCI6MTAwMH0", // null이면 마지막 페이지
  "totalCount": 137,
}
```

---

## Collections

| Method | Path                                    | 설명            | 인증          |
| ------ | --------------------------------------- | --------------- | ------------- |
| POST   | `/collections`                          | 생성            | ✅            |
| GET    | `/collections/{slug}`                   | 상세 (공개 URL) | 공개범위 따름 |
| PATCH  | `/collections/{slug}`                   | 수정            | ✅ 소유자     |
| DELETE | `/collections/{slug}`                   | 삭제            | ✅ 소유자     |
| POST   | `/collections/{slug}/items`             | 콘텐츠 추가     | ✅ 소유자     |
| PATCH  | `/collections/{slug}/items/order`       | 순서 일괄 변경  | ✅ 소유자     |
| DELETE | `/collections/{slug}/items/{contentId}` | 제거            | ✅ 소유자     |

---

## Moods / Discover

| Method | Path                     | 설명                                | 인증 |
| ------ | ------------------------ | ----------------------------------- | ---- |
| GET    | `/moods`                 | 무드 목록 (큐레이션 우선, 사용량순) | —    |
| GET    | `/moods/{name}/contents` | 해당 무드로 기록된 콘텐츠 랭킹      | —    |
| GET    | `/discover/popular`      | 인기 콘텐츠 (`period=week\|month`)  | —    |

---

## Admin — 운영

운영자만 접근한다. **운영자가 아니면 403 이 아니라 404** 다 — 403 은 관리자 화면이
존재한다는 사실을 알려준다.

| Method | Path                  | 설명                                  | 인증      |
| ------ | --------------------- | ------------------------------------- | --------- |
| GET    | `/admin/reports`      | 신고 큐 (`status`, `cursor`, `limit`) | ✅ 운영자 |
| PATCH  | `/admin/reports/{id}` | 처리 (`REVIEWED` / `DISMISSED`)       | ✅ 운영자 |

**운영자 임명은 환경변수** `ADMIN_HANDLES` 의 핸들 목록이다 (쉼표 구분, 대소문자 구분).
`users` 테이블에 권한 컬럼을 두지 않아 DB 직접 수정 없이 배포로 바뀌고, 시크릿과 같은
경로로 관리된다. 대신 부여·회수에 재배포가 필요하고, 회수는 이미 발급된 access 토큰이
만료된 뒤(기본 15분) 적용된다. 운영자가 늘어 이력이 필요해지면 별도 테이블로 옮긴다.

목록은 **신고 건별**로 평면이다. 같은 대상의 신고를 묶지 않는 대신 각 항목이
`sameTargetCount` 로 "이 대상에 몇 건이 쌓였는가" 를 알려준다. 신고는 한 번만 처리되며,
이미 처리된 건을 다시 바꾸려 하면 409 다.

---

## 공통 규약

**커서 형식**: `base64({ "id": <lastId>, "ts": "<lastCreatedAt>" })` — 불투명 문자열. 클라이언트는 파싱하지 않는다.

**에러 코드 카탈로그**

| code                      | status | 상황                                                     |
| ------------------------- | ------ | -------------------------------------------------------- |
| `VALIDATION_FAILED`       | 400    | 요청 검증 실패 (`errors[]`에 필드별 사유)                |
| `RATING_NOT_ALLOWED`      | 400    | `status=WANT`에 별점 부여 시도                           |
| `MOOD_LIMIT_EXCEEDED`     | 400    | 무드 6개 이상                                            |
| `UNAUTHORIZED`            | 401    | 토큰 없음/만료                                           |
| `TOKEN_REUSE_DETECTED`    | 401    | refresh 재사용 감지 → 전 세션 무효화됨                   |
| `FORBIDDEN`               | 403    | 소유자 아님 / 공개범위 위반                              |
| `NOT_FOUND`               | 404    |                                                          |
| `RECORD_DUPLICATE`        | 409    | 동일 콘텐츠 기록 존재                                    |
| `HANDLE_TAKEN`            | 409    | 핸들 중복                                                |
| `REPORT_ALREADY_RESOLVED` | 409    | 이미 처리된 신고를 다시 처리 시도                        |
| `PROVIDER_UNAVAILABLE`    | 502    | 외부 API 전체 실패 (부분 실패는 200 + `failedProviders`) |
| `RATE_LIMITED`            | 429    |                                                          |

**Rate Limit**: 인증 사용자 600 req/min, 비인증 60 req/min, 검색 30 req/min (Redis 토큰 버킷)
