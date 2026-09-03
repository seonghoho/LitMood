package com.litmood.domain.model;

/** 기록 상태 (F-03-02). */
public enum RecordStatus {
    /** 보고 싶다 — 아직 소비 전이므로 별점을 부여할 수 없다 (도메인 불변식 2). */
    WANT,
    /** 보는 중 */
    DOING,
    /** 다 봄 */
    DONE,
    /** 중단 */
    DROPPED;

    public boolean allowsRating() {
        return this != WANT;
    }
}
