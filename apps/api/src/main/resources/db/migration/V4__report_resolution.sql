-- 신고 처리의 이중 방어 (#28).
--
-- "처리된 신고에는 처리 시각이 남는다" 는 Report.resolve() 가 지키는 규칙이지만,
-- 도메인 규칙은 DB 제약으로도 막는다 (CLAUDE.md). 배치·수기 UPDATE 로 상태만
-- 바꾸면 큐에서는 사라지고 언제 판단했는지는 알 수 없는 행이 남는다.
ALTER TABLE reports
    ADD CONSTRAINT ck_reports_resolution CHECK (
        (status = 'PENDING' AND resolved_at IS NULL)
        OR (status <> 'PENDING' AND resolved_at IS NOT NULL)
    );

-- 큐는 "같은 대상에 몇 건이 쌓였는가" 를 함께 보여준다. 목록 한 페이지마다
-- 대상별 집계가 따라붙으므로 (target_type, target_id) 로 바로 세도록 한다.
CREATE INDEX idx_reports_target ON reports (target_type, target_id);
