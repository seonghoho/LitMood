-- =============================================================
-- V3. 소셜 (F-06) — 차단·신고 테이블과 좋아요 카운터
-- follows / likes 는 V1 에 이미 있다.
-- =============================================================

-- ── 좋아요 카운터 (비정규화) ──────────────────────────────────
-- 피드와 타임라인은 매 렌더마다 N개의 기록을 보여준다.
-- 항목마다 count(*) 를 돌면 N+1 이 되므로 카운터를 들고 있는다.
-- 쓰기는 좋아요 트랜잭션 안에서 함께 증감시켜 정합을 맞춘다.
ALTER TABLE records     ADD COLUMN like_count INT NOT NULL DEFAULT 0;
ALTER TABLE collections ADD COLUMN like_count INT NOT NULL DEFAULT 0;

ALTER TABLE records     ADD CONSTRAINT ck_records_like_count     CHECK (like_count >= 0);
ALTER TABLE collections ADD CONSTRAINT ck_collections_like_count CHECK (like_count >= 0);

-- ── blocks ───────────────────────────────────────────────────
-- 차단은 양방향 가림이다: 차단하면 서로의 콘텐츠가 보이지 않는다.
-- 한쪽만 가리면 차단한 사람이 계속 상대 글을 보게 되어 의미가 없다.
CREATE TABLE blocks (
    blocker_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_blocks_not_self CHECK (blocker_id <> blocked_id)
);
-- "나를 차단한 사람" 조회용 역방향 인덱스
CREATE INDEX idx_blocks_blocked ON blocks (blocked_id);

-- ── reports ──────────────────────────────────────────────────
-- 공개 서비스 운영의 최소 요건 (F-06-05).
CREATE TABLE reports (
    id           BIGSERIAL PRIMARY KEY,
    reporter_id  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type  VARCHAR(20)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    reason       VARCHAR(30)  NOT NULL,
    detail       VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    CONSTRAINT ck_reports_target CHECK (target_type IN ('RECORD', 'COLLECTION', 'USER')),
    CONSTRAINT ck_reports_reason CHECK (reason IN ('SPAM', 'ABUSE', 'SEXUAL', 'SPOILER', 'COPYRIGHT', 'OTHER')),
    CONSTRAINT ck_reports_status CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED')),
    -- 같은 대상을 반복 신고해 큐를 채우는 것을 막는다
    CONSTRAINT uq_reports_once UNIQUE (reporter_id, target_type, target_id)
);
CREATE INDEX idx_reports_queue ON reports (status, created_at DESC);

-- ── 피드 조회용 인덱스 ────────────────────────────────────────
-- 팔로우한 사용자들의 공개 기록을 시간 역순으로 훑는다.
-- V1 의 idx_records_timeline 은 user_id 단일 값 전제라 IN 절에는 덜 효율적이다.
CREATE INDEX idx_records_feed
    ON records (created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND visibility IN ('PUBLIC', 'FOLLOWERS');
