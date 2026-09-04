package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    /** 소셜 전용 계정은 null 이다 (F-01-02). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String handle;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 200)
    private String bio;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility", nullable = false, length = 20)
    private Visibility defaultVisibility = Visibility.PUBLIC;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {}

    private User(String email, String passwordHash, String handle, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.handle = handle;
        this.nickname = nickname;
    }

    /** 이메일 가입 (F-01-01). passwordHash 는 호출부에서 인코딩해 넘긴다. */
    public static User ofEmail(String email, String passwordHash, String handle, String nickname) {
        return new User(email, passwordHash, handle, nickname);
    }

    /** 소셜 가입 (F-01-02). 비밀번호가 없다. */
    public static User ofOAuth(String email, String handle, String nickname) {
        return new User(email, null, handle, nickname);
    }

    public void updateProfile(String nickname, String bio, Visibility defaultVisibility) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        this.bio = bio;
        if (defaultVisibility != null) {
            this.defaultVisibility = defaultVisibility;
        }
    }

    /** 아바타 교체. URL 이 우리 스토리지의 것인지는 호출부가 검증한다. */
    public void updateAvatar(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getHandle() {
        return handle;
    }

    public String getNickname() {
        return nickname;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Visibility getDefaultVisibility() {
        return defaultVisibility;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
