import { describe, expect, it } from 'vitest'
import { buildProfilePatch, validateAvatarFile, type ProfileDraft } from '../profile-form'

/**
 * 백엔드(UserService)와 같은 제약을 클라이언트에서도 확인한다.
 * presigned URL 을 받아 업로드까지 간 뒤 거절당하면 왕복이 두 번 낭비된다.
 */
describe('validateAvatarFile', () => {
  it('허용 형식은 통과시킨다', () => {
    expect(validateAvatarFile({ type: 'image/jpeg', size: 1024 })).toBeNull()
    expect(validateAvatarFile({ type: 'image/png', size: 1024 })).toBeNull()
    expect(validateAvatarFile({ type: 'image/webp', size: 1024 })).toBeNull()
  })

  it('허용하지 않는 형식은 사유를 돌려준다', () => {
    expect(validateAvatarFile({ type: 'image/gif', size: 1024 })).toBe(
      'JPG, PNG, WebP 이미지만 올릴 수 있습니다',
    )
  })

  it('5MB 를 넘으면 사유를 돌려준다', () => {
    expect(validateAvatarFile({ type: 'image/png', size: 5 * 1024 * 1024 })).toBeNull()
    expect(validateAvatarFile({ type: 'image/png', size: 5 * 1024 * 1024 + 1 })).toBe(
      '파일 크기는 5MB를 넘을 수 없습니다',
    )
  })
})

/**
 * PATCH 는 보낸 필드만 바꾼다. 바뀌지 않은 값까지 실어 보내면
 * 다른 탭에서 방금 수정한 값을 되돌려 덮어쓸 수 있다.
 */
describe('buildProfilePatch', () => {
  const saved: ProfileDraft = {
    nickname: '성호',
    bio: '기록합니다',
    defaultVisibility: 'PUBLIC',
    avatarUrl: null,
  }

  it('바뀐 필드만 담는다', () => {
    expect(buildProfilePatch(saved, { ...saved, nickname: '새이름' })).toEqual({
      nickname: '새이름',
    })
  })

  it('바뀐 것이 없으면 빈 객체를 돌려준다', () => {
    expect(buildProfilePatch(saved, { ...saved })).toEqual({})
  })

  it('소개를 지우면 빈 문자열로 보낸다 — 생략하면 지워지지 않는다', () => {
    expect(buildProfilePatch(saved, { ...saved, bio: '' })).toEqual({ bio: '' })
  })

  it('여러 필드가 바뀌면 모두 담는다', () => {
    expect(
      buildProfilePatch(saved, {
        nickname: '새이름',
        bio: '새 소개',
        defaultVisibility: 'FOLLOWERS',
        avatarUrl: 'http://localhost:9000/litmood/avatars/1/a.png',
      }),
    ).toEqual({
      nickname: '새이름',
      bio: '새 소개',
      defaultVisibility: 'FOLLOWERS',
      avatarUrl: 'http://localhost:9000/litmood/avatars/1/a.png',
    })
  })

  it('아바타를 새로 올린 경우에만 avatarUrl 을 담는다', () => {
    const withAvatar: ProfileDraft = { ...saved, avatarUrl: 'http://localhost:9000/litmood/a.png' }
    expect(buildProfilePatch(withAvatar, { ...withAvatar })).toEqual({})
  })
})

describe('buildProfilePatch — 아바타 지우기 (이슈 #24)', () => {
  const saved: ProfileDraft = {
    nickname: '나',
    bio: '',
    defaultVisibility: 'PUBLIC',
    avatarUrl: 'https://storage.example/litmood/avatars/1/a.webp',
  }

  it('아바타를 비우면 null 이 아니라 빈 문자열을 보낸다', () => {
    // null 을 보내면 서버가 "변경 없음"으로 읽어 아바타가 그대로 남는다
    expect(buildProfilePatch(saved, { ...saved, avatarUrl: null })).toEqual({ avatarUrl: '' })
  })

  it('아바타를 바꾸면 새 URL 을 보낸다', () => {
    const next = 'https://storage.example/litmood/avatars/1/b.webp'
    expect(buildProfilePatch(saved, { ...saved, avatarUrl: next })).toEqual({ avatarUrl: next })
  })

  it('아바타가 그대로면 실어 보내지 않는다', () => {
    expect(buildProfilePatch(saved, { ...saved, nickname: '새 이름' })).toEqual({
      nickname: '새 이름',
    })
  })
})
