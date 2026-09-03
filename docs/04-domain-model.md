# 04. 도메인 모델

## ERD

```
users ──< records >── contents
  │         │
  │         └──< record_moods >── moods
  │
  ├──< collections ──< collection_items >── contents
  ├──< follows >── users (self)
  └──< likes >── (records | collections)
```

## 테이블 정의

### `users`

| 컬럼                    | 타입         | 제약                      | 비고                  |
| ----------------------- | ------------ | ------------------------- | --------------------- |
| id                      | bigserial    | PK                        |                       |
| email                   | varchar(255) | UNIQUE NOT NULL           |                       |
| password_hash           | varchar(60)  | NULL                      | 소셜 전용 계정은 NULL |
| handle                  | varchar(30)  | UNIQUE NOT NULL           | 공개 URL `/@handle`   |
| nickname                | varchar(50)  | NOT NULL                  |                       |
| bio                     | varchar(200) |                           |                       |
| avatar_url              | text         |                           | MinIO 키              |
| default_visibility      | varchar(20)  | NOT NULL DEFAULT 'PUBLIC' | 기록 기본 공개범위    |
| created_at / updated_at | timestamptz  | NOT NULL                  |                       |
| deleted_at              | timestamptz  | NULL                      | soft delete           |

### `oauth_accounts`

| 컬럼             | 타입         | 제약                               |
| ---------------- | ------------ | ---------------------------------- |
| id               | bigserial    | PK                                 |
| user_id          | bigint       | FK → users                         |
| provider         | varchar(20)  | `GOOGLE` / `KAKAO`                 |
| provider_user_id | varchar(255) |                                    |
|                  |              | UNIQUE(provider, provider_user_id) |

### `contents` — 외부 콘텐츠 스냅샷

| 컬럼        | 타입         | 제약                              | 비고                              |
| ----------- | ------------ | --------------------------------- | --------------------------------- |
| id          | bigserial    | PK                                |                                   |
| type        | varchar(10)  | NOT NULL                          | `BOOK` / `MOVIE` / `MUSIC`        |
| provider    | varchar(20)  | NOT NULL                          | `NAVER_BOOK` / `TMDB` / `SPOTIFY` |
| external_id | varchar(255) | NOT NULL                          |                                   |
| title       | varchar(500) | NOT NULL                          |                                   |
| creators    | text[]       |                                   | 저자 / 감독·출연 / 아티스트       |
| released_on | date         |                                   |                                   |
| cover_url   | text         |                                   |                                   |
| description | text         |                                   |                                   |
| metadata    | jsonb        | NOT NULL DEFAULT '{}'             | 타입별 고유 필드                  |
| synced_at   | timestamptz  | NOT NULL                          | 스냅샷 시각                       |
|             |              | **UNIQUE(provider, external_id)** |                                   |

`metadata` 예시

```jsonc
// BOOK
{ "isbn13": "9788937473135", "publisher": "민음사", "pageCount": 468 }
// MOVIE
{ "tmdbId": 296, "runtime": 137, "genres": ["드라마"], "originalTitle": "Norwegian Wood" }
// MUSIC
{ "album": "Kind of Blue", "durationMs": 545000, "isrc": "USSM15900026" }
```

### `records` — 핵심 엔티티

| 컬럼                     | 타입          | 제약                                                     | 비고                            |
| ------------------------ | ------------- | -------------------------------------------------------- | ------------------------------- |
| id                       | bigserial     | PK                                                       |                                 |
| user_id                  | bigint        | FK → users NOT NULL                                      |                                 |
| content_id               | bigint        | FK → contents NOT NULL                                   |                                 |
| status                   | varchar(10)   | NOT NULL                                                 | `WANT`/`DOING`/`DONE`/`DROPPED` |
| rating                   | numeric(2,1)  | CHECK (rating BETWEEN 0.5 AND 5.0)                       | NULL 허용                       |
| review                   | varchar(2000) |                                                          |                                 |
| is_spoiler               | boolean       | NOT NULL DEFAULT false                                   |                                 |
| visibility               | varchar(10)   | NOT NULL                                                 | `PUBLIC`/`FOLLOWERS`/`PRIVATE`  |
| context_note             | varchar(200)  |                                                          | 소비 맥락 메모                  |
| started_at / finished_at | date          |                                                          |                                 |
| repeat_count             | int           | NOT NULL DEFAULT 0                                       | 재소비 횟수                     |
| created_at / updated_at  | timestamptz   | NOT NULL                                                 |                                 |
| deleted_at               | timestamptz   | NULL                                                     |                                 |
|                          |               | **UNIQUE(user_id, content_id) WHERE deleted_at IS NULL** | 부분 유니크 인덱스              |

**인덱스**

```sql
CREATE INDEX idx_records_timeline
  ON records (user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_records_public
  ON records (content_id, created_at DESC) WHERE visibility='PUBLIC' AND deleted_at IS NULL;
```

### `moods` / `record_moods`

| moods        | 타입        | 비고                                   |
| ------------ | ----------- | -------------------------------------- |
| id           | bigserial   | PK                                     |
| name         | varchar(30) | UNIQUE, 정규화된 이름 (trim + lower)   |
| display_name | varchar(30) | 노출용                                 |
| color        | varchar(7)  | `#RRGGBB` — 무드별 색 정체성 (ADR-002) |
| is_curated   | boolean     | 사전 정의 태그 여부                    |
| usage_count  | bigint      | 랭킹용 비정규화 카운터                 |

`record_moods(record_id, mood_id)` — PK 복합. **기록당 최대 5개**는 애플리케이션 레벨에서 강제.

**초기 큐레이션 무드 (12종)**
`새벽` `비오는날` `설렘` `먹먹함` `몰입` `위로` `번아웃` `여행` `가을밤` `집중` `향수` `해방`

### `collections` / `collection_items`

| collections                                            | 타입        | 비고              |
| ------------------------------------------------------ | ----------- | ----------------- |
| id, user_id, title, description, cover_url, visibility |             |                   |
| slug                                                   | varchar(80) | UNIQUE — 공유 URL |
| item_count                                             | int         | 비정규화          |

| collection_items          | 타입         | 비고                              |
| ------------------------- | ------------ | --------------------------------- |
| collection_id, content_id |              | UNIQUE(collection_id, content_id) |
| position                  | int          | 정렬 순서                         |
| note                      | varchar(300) | 큐레이터 노트                     |

### `follows`

`(follower_id, followee_id)` PK 복합. `CHECK (follower_id <> followee_id)`

### `likes`

`(user_id, target_type, target_id)` PK 복합. `target_type ∈ {RECORD, COLLECTION}`

---

## 도메인 규칙 (불변식)

1. **한 사용자 · 한 콘텐츠 · 한 기록.** 재소비는 `repeat_count` 증가로 표현한다.
2. `status = WANT`인 기록에는 `rating`을 부여할 수 없다. (아직 소비하지 않았으므로)
3. `visibility = PRIVATE`인 기록은 콘텐츠 상세의 평균 별점·무드 분포 집계에서 제외한다.
4. 콘텐츠는 **삭제되지 않는다.** 기록이 0개가 되어도 스냅샷은 남는다 (재기록 시 재활용).
5. 무드 이름은 저장 시 `trim → 소문자 → 공백 제거`로 정규화한다. `#새벽`, `새벽 `, `새벽`은 동일 태그다.
6. 사용자 탈퇴 시 기록은 즉시 `PRIVATE`로 전환 후 30일 뒤 물리 삭제한다.
7. 차단 관계인 두 사용자는 서로의 기록·컬렉션·프로필 기록 목록을 볼 수 없고, 팔로우할 수 없다.
