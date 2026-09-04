package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 차단 (F-06-05).
 *
 * 차단은 <b>양방향</b>으로 가린다. 한쪽만 가리면 차단한 사람이 계속 상대 글을
 * 보게 되어 차단의 목적을 달성하지 못한다.
 */
@Entity
@Table(name = "blocks")
public class Block {

    @EmbeddedId
    private BlockId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Block() {}

    public static Block of(Long blockerId, Long blockedId) {
        Block block = new Block();
        block.id = new BlockId(blockerId, blockedId);
        return block;
    }

    @Embeddable
    public static class BlockId implements Serializable {

        @Column(name = "blocker_id")
        private Long blockerId;

        @Column(name = "blocked_id")
        private Long blockedId;

        protected BlockId() {}

        BlockId(Long blockerId, Long blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BlockId that)) {
                return false;
            }
            return Objects.equals(blockerId, that.blockerId) && Objects.equals(blockedId, that.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
