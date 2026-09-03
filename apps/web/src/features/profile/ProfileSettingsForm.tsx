'use client'

import { useEffect, useRef, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { apiGet, apiPatch, apiPost } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'
import {
  buildProfilePatch,
  validateAvatarFile,
  type ProfileDraft,
  type Visibility,
} from './profile-form'

interface MyProfile {
  id: number
  email: string
  handle: string
  nickname: string
  bio: string | null
  avatarUrl: string | null
  defaultVisibility: Visibility
}

interface AvatarUpload {
  uploadUrl: string
  publicUrl: string
}

const VISIBILITY_OPTIONS: { value: Visibility; label: string; hint: string }[] = [
  { value: 'PUBLIC', label: '전체 공개', hint: '누구나 볼 수 있습니다' },
  { value: 'FOLLOWERS', label: '팔로워만', hint: '나를 팔로우한 사람만 볼 수 있습니다' },
  { value: 'PRIVATE', label: '나만 보기', hint: '집계에도 포함되지 않습니다' },
]

export function ProfileSettingsForm() {
  const storeUser = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const signIn = useAuthStore((state) => state.signIn)
  const accessToken = useAuthStore((state) => state.accessToken)

  const [saved, setSaved] = useState<ProfileDraft | null>(null)
  const [draft, setDraft] = useState<ProfileDraft | null>(null)
  const [handle, setHandle] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [done, setDone] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!ready || !storeUser) return

    apiGet<MyProfile>('/api/v1/users/me')
      .then((profile) => {
        const loaded: ProfileDraft = {
          nickname: profile.nickname,
          bio: profile.bio ?? '',
          defaultVisibility: profile.defaultVisibility,
          avatarUrl: profile.avatarUrl,
        }
        setSaved(loaded)
        setDraft(loaded)
        setHandle(profile.handle)
      })
      .catch(() => setLoadError('프로필을 불러오지 못했습니다'))
  }, [ready, storeUser])

  if (!ready) return null
  if (!storeUser) {
    return <p className={css({ textStyle: 'body', color: 'fg.muted' })}>로그인이 필요합니다.</p>
  }
  if (loadError) {
    return (
      <p role="alert" className={css({ textStyle: 'body', color: 'danger.500' })}>
        {loadError}
      </p>
    )
  }
  if (!saved || !draft) {
    return <p className={css({ textStyle: 'body', color: 'fg.muted' })}>불러오는 중…</p>
  }

  const update = (patch: Partial<ProfileDraft>) => {
    setDraft({ ...draft, ...patch })
    setDone(false)
  }

  const pickAvatar = async (file: File) => {
    const invalid = validateAvatarFile(file)
    if (invalid) {
      setFieldErrors({ ...fieldErrors, avatarUrl: invalid })
      return
    }

    setUploading(true)
    setError(null)
    setFieldErrors({ ...fieldErrors, avatarUrl: '' })

    try {
      // 이미지 바이트는 API 서버를 거치지 않고 스토리지로 바로 올라간다
      const { uploadUrl, publicUrl } = await apiPost<AvatarUpload>('/api/v1/users/me/avatar', {
        contentType: file.type,
        contentLength: file.size,
      })

      const uploaded = await fetch(uploadUrl, {
        method: 'PUT',
        // 서명에 포함된 값과 정확히 같아야 스토리지가 받아준다
        headers: { 'Content-Type': file.type },
        body: file,
      })
      if (!uploaded.ok) throw new Error('upload failed')

      // 저장은 아래 "저장" 버튼의 PATCH 한 번으로 함께 반영된다
      update({ avatarUrl: publicUrl })
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '이미지를 올리지 못했습니다')
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    const patch = buildProfilePatch(saved, draft)
    if (Object.keys(patch).length === 0) {
      setDone(true)
      return
    }

    setSaving(true)
    setError(null)
    setFieldErrors({})

    try {
      const updated = await apiPatch<MyProfile>('/api/v1/users/me', patch)
      const next: ProfileDraft = {
        nickname: updated.nickname,
        bio: updated.bio ?? '',
        defaultVisibility: updated.defaultVisibility,
        avatarUrl: updated.avatarUrl,
      }
      setSaved(next)
      setDraft(next)
      setDone(true)

      // 헤더의 닉네임·아바타가 즉시 따라오게 한다
      if (accessToken) {
        signIn(accessToken, {
          id: updated.id,
          handle: updated.handle,
          nickname: updated.nickname,
          avatarUrl: updated.avatarUrl,
        })
      }
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.problem.title)
        const errors = e.problem.errors ?? []
        setFieldErrors(Object.fromEntries(errors.map((fe) => [fe.field, fe.reason])))
      } else {
        setError('저장하지 못했습니다')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className={stack({ gap: '6' })}>
      <div className={flex({ gap: '4', alignItems: 'center' })}>
        <Avatar url={draft.avatarUrl} nickname={draft.nickname} />
        <div className={stack({ gap: '1.5' })}>
          <button
            type="button"
            onClick={() => fileInput.current?.click()}
            disabled={uploading}
            className={css({
              textStyle: 'caption',
              fontWeight: '600',
              px: '3',
              py: '2',
              rounded: 'md',
              cursor: 'pointer',
              bg: 'bg.surface',
              color: 'fg.default',
              borderWidth: '1px',
              borderStyle: 'solid',
              borderColor: 'border.default',
              _disabled: { opacity: 0.6, cursor: 'not-allowed' },
            })}
          >
            {uploading ? '올리는 중…' : '사진 변경'}
          </button>
          <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            JPG · PNG · WebP, 5MB 이하
          </span>
          {fieldErrors.avatarUrl && (
            <span className={css({ textStyle: 'caption', color: 'danger.500' })}>
              {fieldErrors.avatarUrl}
            </span>
          )}
        </div>
        <input
          ref={fileInput}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) void pickAvatar(file)
          }}
          className={css({ display: 'none' })}
        />
      </div>

      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>아이디</span>
        <input
          value={handle}
          readOnly
          disabled
          aria-describedby="handle-hint"
          className={inputStyle}
        />
        <span id="handle-hint" className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          공개 주소(/@{handle})가 바뀌면 공유된 링크가 깨지므로 변경할 수 없습니다
        </span>
      </label>

      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>닉네임</span>
        <input
          value={draft.nickname}
          onChange={(event) => update({ nickname: event.target.value })}
          maxLength={50}
          required
          aria-invalid={fieldErrors.nickname ? true : undefined}
          className={inputStyle}
        />
        {fieldErrors.nickname && (
          <span className={css({ textStyle: 'caption', color: 'danger.500' })}>
            {fieldErrors.nickname}
          </span>
        )}
      </label>

      <label className={stack({ gap: '1.5' })}>
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>소개</span>
        <textarea
          value={draft.bio}
          onChange={(event) => update({ bio: event.target.value })}
          maxLength={200}
          rows={3}
          aria-invalid={fieldErrors.bio ? true : undefined}
          className={css({
            px: '3',
            py: '2.5',
            rounded: 'md',
            textStyle: 'body',
            bg: 'bg.surface',
            color: 'fg.default',
            borderWidth: '1px',
            borderStyle: 'solid',
            borderColor: 'border.default',
            resize: 'vertical',
            _focusVisible: {
              outlineWidth: '2px',
              outlineStyle: 'solid',
              outlineColor: 'brand.default',
            },
          })}
        />
        <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          {draft.bio.length} / 200
        </span>
      </label>

      <fieldset className={stack({ gap: '2' })}>
        <legend className={css({ textStyle: 'caption', color: 'fg.muted', mb: '1' })}>
          기록의 기본 공개 범위
        </legend>
        {VISIBILITY_OPTIONS.map((option) => (
          <label key={option.value} className={flex({ gap: '2', alignItems: 'baseline' })}>
            <input
              type="radio"
              name="defaultVisibility"
              value={option.value}
              checked={draft.defaultVisibility === option.value}
              onChange={() => update({ defaultVisibility: option.value })}
            />
            <span className={css({ textStyle: 'body', color: 'fg.default' })}>{option.label}</span>
            <span className={css({ textStyle: 'caption', color: 'fg.muted' })}>{option.hint}</span>
          </label>
        ))}
      </fieldset>

      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}
      {done && (
        <p role="status" className={css({ textStyle: 'caption', color: 'fg.muted' })}>
          저장했습니다
        </p>
      )}

      <button
        type="submit"
        disabled={saving || uploading}
        className={css({
          textStyle: 'body',
          fontWeight: '600',
          px: '4',
          py: '3',
          rounded: 'md',
          cursor: 'pointer',
          bg: 'brand.default',
          color: 'fg.onAccent',
          _disabled: { opacity: 0.6, cursor: 'not-allowed' },
        })}
      >
        {saving ? '저장 중…' : '저장'}
      </button>
    </form>
  )
}

function Avatar({ url, nickname }: { url: string | null; nickname: string }) {
  const size = { width: '72px', height: '72px', rounded: 'full', flexShrink: 0 } as const

  if (!url) {
    return (
      <div
        aria-hidden
        className={flex({
          ...size,
          alignItems: 'center',
          justifyContent: 'center',
          bg: 'bg.muted',
          color: 'fg.muted',
          textStyle: 'title',
        })}
      >
        {nickname.slice(0, 1)}
      </div>
    )
  }

  // next/image 대신 img 를 쓴다 — 아바타는 사용자가 방금 올린 URL 이라
  // 빌드 타임 최적화 대상이 아니고, 업로드 직후 미리보기가 바로 떠야 한다
  return (
    <img
      src={url}
      alt={`${nickname}의 프로필 사진`}
      className={css({ ...size, objectFit: 'cover' })}
    />
  )
}

const inputStyle = css({
  px: '3',
  py: '2.5',
  rounded: 'md',
  textStyle: 'body',
  bg: 'bg.surface',
  color: 'fg.default',
  borderWidth: '1px',
  borderStyle: 'solid',
  borderColor: 'border.default',
  _disabled: { opacity: 0.7, cursor: 'not-allowed' },
  _focusVisible: {
    outlineWidth: '2px',
    outlineStyle: 'solid',
    outlineColor: 'brand.default',
  },
})
