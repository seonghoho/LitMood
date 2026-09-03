package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** 좋아요 (F-06-03). */
@Entity
@Table(name = "likes")
public class Like {

    @EmbeddedId
    private LikeId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Like() {}

    public static Like of(Long userId, LikeTarget targetType, Long targetId) {
        Like like = new Like();
        like.id = new LikeId(userId, targetType, targetId);
        return like;
    }

    @Embeddable
    public static class LikeId implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Enumerated(EnumType.STRING)
        @Column(name = "target_type", length = 20)
        private LikeTarget targetType;

        @Column(name = "target_id")
        private Long targetId;

        protected LikeId() {}

        LikeId(Long userId, LikeTarget targetType, Long targetId) {
            this.userId = userId;
            this.targetType = targetType;
            this.targetId = targetId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LikeId that)) {
                return false;
            }
            return Objects.equals(userId, that.userId)
                    && targetType == that.targetType
                    && Objects.equals(targetId, that.targetId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, targetType, targetId);
        }
    }
}
