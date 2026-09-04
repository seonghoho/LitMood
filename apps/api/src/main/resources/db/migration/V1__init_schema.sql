-- =============================================================
-- V1. 초기 스키마 (docs/04-domain-model.md)
-- =============================================================

-- ── users ────────────────────────────────────────────────────
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(60),                         -- 소셜 전용 계정은 NULL
    handle              VARCHAR(30)  NOT NULL,               -- 공개 URL /@handle
    nickname            VARCHAR(50)  NOT NULL,
    bio                 VARCHAR(200),
    avatar_url          TEXT,
    default_visibility  VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT ck_users_visibility CHECK (default_visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE'))
);

-- 탈퇴한 사용자의 이메일/핸들은 재사용 가능해야 하므로 부분 유니크로 건다
CREATE UNIQUE INDEX uq_users_email  ON users (lower(email))  WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_handle ON users (lower(handle)) WHERE deleted_at IS NULL;

-- ── oauth_accounts ───────────────────────────────────────────
CREATE TABLE oauth_accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_oauth_provider CHECK (provider IN ('GOOGLE', 'KAKAO')),
    CONSTRAINT uq_oauth_provider_user UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_oauth_user ON oauth_accounts (user_id);

-- ── contents ─────────────────────────────────────────────────
-- 외부 API 응답의 스냅샷. 외부 provider 가 죽어도 기록은 살아남아야 한다 (NFR-03).
CREATE TABLE contents (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(10)  NOT NULL,
    provider    VARCHAR(20)  NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    title       VARCHAR(500) NOT NULL,
    creators    TEXT[]       NOT NULL DEFAULT '{}',          -- 저자 / 감독·출연 / 아티스트
    released_on DATE,
    cover_url   TEXT,
    description TEXT,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- 타입별 고유 필드 (ADR-004)
    synced_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_contents_type     CHECK (type IN ('BOOK', 'MOVIE', 'MUSIC')),
    CONSTRAINT ck_contents_provider CHECK (provider IN ('NAVER_BOOK', 'TMDB', 'SPOTIFY')),
    CONSTRAINT uq_contents_external UNIQUE (provider, external_id)
);
CREATE INDEX idx_contents_type_title ON contents (type, title);
CREATE INDEX idx_contents_metadata   ON contents USING GIN (metadata jsonb_path_ops);
CREATE INDEX idx_contents_creators   ON contents USING GIN (creators);

-- ── moods ────────────────────────────────────────────────────
CREATE TABLE moods (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(30) NOT NULL,                        -- 정규화된 이름 (불변식 5)
    display_name VARCHAR(30) NOT NULL,
    color        VARCHAR(7),                                  -- #RRGGBB — 무드별 색 정체성 (ADR-002)
    is_curated   BOOLEAN     NOT NULL DEFAULT false,
    usage_count  BIGINT      NOT NULL DEFAULT 0,              -- 랭킹용 비정규화 카운터
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_moods_name UNIQUE (name)
);
CREATE INDEX idx_moods_ranking ON moods (is_curated DESC, usage_count DESC);

-- ── records ──────────────────────────────────────────────────
CREATE TABLE records (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content_id   BIGINT        NOT NULL REFERENCES contents (id),
    status       VARCHAR(10)   NOT NULL,
    rating       NUMERIC(2, 1),
    review       VARCHAR(2000),
    is_spoiler   BOOLEAN       NOT NULL DEFAULT false,
    visibility   VARCHAR(10)   NOT NULL,
    context_note VARCHAR(200),
    started_at   DATE,
    finished_at  DATE,
    repeat_count INT           NOT NULL DEFAULT 0,            -- 재소비는 별도 기록이 아닌 카운터 (불변식 1)
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ck_records_status     CHECK (status IN ('WANT', 'DOING', 'DONE', 'DROPPED')),
    CONSTRAINT ck_records_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE')),
    -- 0.5 단위 별점만 허용
    CONSTRAINT ck_records_rating     CHECK (rating IS NULL OR (rating BETWEEN 0.5 AND 5.0 AND (rating * 2) = floor(rating * 2))),
    -- 불변식 2: 아직 소비하지 않은 상태에는 별점을 부여할 수 없다
    CONSTRAINT ck_records_want_no_rating CHECK (status <> 'WANT' OR rating IS NULL),
    CONSTRAINT ck_records_period     CHECK (started_at IS NULL OR finished_at IS NULL OR started_at <= finished_at),
    CONSTRAINT ck_records_repeat     CHECK (repeat_count >= 0)
);

-- 불변식 1: 한 사용자 · 한 콘텐츠 · 한 기록 (삭제된 기록은 제외)
CREATE UNIQUE INDEX uq_records_user_content ON records (user_id, content_id) WHERE deleted_at IS NULL;
-- 내 타임라인 (F-04-01)
CREATE INDEX idx_records_timeline ON records (user_id, created_at DESC) WHERE deleted_at IS NULL;
-- 콘텐츠 상세의 공개 기록 목록 (F-02-03)
CREATE INDEX idx_records_public ON records (content_id, created_at DESC) WHERE visibility = 'PUBLIC' AND deleted_at IS NULL;

-- ── record_moods ─────────────────────────────────────────────
CREATE TABLE record_moods (
    record_id BIGINT NOT NULL REFERENCES records (id) ON DELETE CASCADE,
    mood_id   BIGINT NOT NULL REFERENCES moods (id),
    PRIMARY KEY (record_id, mood_id)
);
-- 무드별 탐색 (F-07-01)
CREATE INDEX idx_record_moods_mood ON record_moods (mood_id, record_id DESC);

-- ── collections ──────────────────────────────────────────────
CREATE TABLE collections (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    slug        VARCHAR(80)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    cover_url   TEXT,
    visibility  VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC',
    item_count  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_collections_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE'))
);
CREATE UNIQUE INDEX uq_collections_slug ON collections (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_collections_user ON collections (user_id, created_at DESC) WHERE deleted_at IS NULL;

-- ── collection_items ─────────────────────────────────────────
CREATE TABLE collection_items (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT       NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    content_id    BIGINT       NOT NULL REFERENCES contents (id),
    position      INT          NOT NULL,
    note          VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_collection_items UNIQUE (collection_id, content_id)
);
CREATE INDEX idx_collection_items_order ON collection_items (collection_id, position);

-- ── follows ──────────────────────────────────────────────────
CREATE TABLE follows (
    follower_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
);
-- 팔로워 목록 조회용 역방향 인덱스
CREATE INDEX idx_follows_followee ON follows (followee_id, created_at DESC);

-- ── likes ────────────────────────────────────────────────────
CREATE TABLE likes (
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
    target_id   BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, target_type, target_id),
    CONSTRAINT ck_likes_target CHECK (target_type IN ('RECORD', 'COLLECTION'))
);
CREATE INDEX idx_likes_target ON likes (target_type, target_id);
