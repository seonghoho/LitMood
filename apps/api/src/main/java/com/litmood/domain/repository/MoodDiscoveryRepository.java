package com.litmood.domain.repository;

import com.litmood.domain.model.Content;
import java.util.List;

/**
 * 무드별 탐색 (F-07-01).
 *
 * "#새벽 을 쓴 사람들은 무엇을 봤는가" — 이 서비스의 차별점이 실제로 값을 하는 지점이다.
 */
public interface MoodDiscoveryRepository {

    List<ContentRanking> rankByMood(String normalizedMoodName, int limit);

    /** 콘텐츠와 그 무드로 기록된 횟수·평균 별점. */
    record ContentRanking(Content content, long recordCount, Double averageRating) {}
}
