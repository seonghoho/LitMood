package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** 팔로우 (F-06-01). 비대칭 관계 — 승인이 필요 없다. */
@Entity
@Table(name = "follows")
public class Follow {

    @EmbeddedId
    private FollowId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Follow() {}

    public static Follow of(Long followerId, Long followeeId) {
        Follow follow = new Follow();
        follow.id = new FollowId(followerId, followeeId);
        return follow;
    }

    public Long getFollowerId() {
        return id.getFollowerId();
    }

    public Long getFolloweeId() {
        return id.getFolloweeId();
    }

    @Embeddable
    public static class FollowId implements Serializable {

        @Column(name = "follower_id")
        private Long followerId;

        @Column(name = "followee_id")
        private Long followeeId;

        protected FollowId() {}

        FollowId(Long followerId, Long followeeId) {
            this.followerId = followerId;
            this.followeeId = followeeId;
        }

        Long getFollowerId() {
            return followerId;
        }

        Long getFolloweeId() {
            return followeeId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FollowId that)) {
                return false;
            }
            return Objects.equals(followerId, that.followerId) && Objects.equals(followeeId, that.followeeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(followerId, followeeId);
        }
    }
}
