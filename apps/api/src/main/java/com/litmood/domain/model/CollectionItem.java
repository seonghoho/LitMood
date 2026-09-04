package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "collection_items")
public class CollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(nullable = false)
    private int position;

    /** 큐레이터 노트 — "이 곡은 3번 트랙부터" 같은 (F-05-03). */
    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CollectionItem() {}

    static CollectionItem of(Collection collection, Content content, int position, String note) {
        CollectionItem item = new CollectionItem();
        item.collection = collection;
        item.content = content;
        item.position = position;
        item.note = note;
        return item;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public void changeNote(String note) {
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Content getContent() {
        return content;
    }

    public int getPosition() {
        return position;
    }

    public String getNote() {
        return note;
    }
}
