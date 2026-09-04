package com.litmood.domain.model;

/** 공개 범위 (F-03-07). */
public enum Visibility {
    PUBLIC,
    FOLLOWERS,
    PRIVATE;

    /** 콘텐츠 상세의 평균 별점·무드 분포 집계 포함 여부 (도메인 불변식 3). */
    public boolean isAggregatable() {
        return this != PRIVATE;
    }
}
