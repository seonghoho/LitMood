export type Visibility = 'PUBLIC' | 'FOLLOWERS' | 'PRIVATE'

export interface ProfileDraft {
  nickname: string
  bio: string
  defaultVisibility: Visibility
  avatarUrl: string | null
}

/** 백엔드 UserService 와 같은 제약 — 값이 갈라지면 클라이언트 검증이 무의미해진다. */
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_AVATAR_BYTES = 5 * 1024 * 1024

/** 통과하면 null, 아니면 사용자에게 보여줄 사유. */
export function validateAvatarFile(file: { type: string; size: number }): string | null {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
    return 'JPG, PNG, WebP 이미지만 올릴 수 있습니다'
  }
  if (file.size > MAX_AVATAR_BYTES) {
    return '파일 크기는 5MB를 넘을 수 없습니다'
  }
  return null
}

/**
 * 저장된 값과 달라진 필드만 담는다.
 *
 * 바뀌지 않은 값까지 보내면 다른 탭에서 방금 고친 내용을 되돌려 덮어쓸 수 있다.
 * 소개를 비운 경우는 빈 문자열로 보낸다 — 생략하면 서버가 "변경 없음"으로 읽는다.
 */
export function buildProfilePatch(saved: ProfileDraft, draft: ProfileDraft): Partial<ProfileDraft> {
  const patch: Partial<ProfileDraft> = {}

  if (draft.nickname !== saved.nickname) patch.nickname = draft.nickname
  if (draft.bio !== saved.bio) patch.bio = draft.bio
  if (draft.defaultVisibility !== saved.defaultVisibility) {
    patch.defaultVisibility = draft.defaultVisibility
  }
  if (draft.avatarUrl !== saved.avatarUrl) patch.avatarUrl = draft.avatarUrl

  return patch
}
