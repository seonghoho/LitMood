'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { ApiError } from '@litmood/api-client'
import { apiDelete, apiGet, apiPost } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/**
 * F-06-01 — 팔로우. 비대칭 관계라 승인 대기 상태가 없다.
 *
 * 프로필 페이지는 SEO 를 위해 캐시되고, 그 서버 렌더링은 인증 없이 수행된다.
 * 따라서 서버가 내려준 followedByMe 는 언제나 false 다 —
 * 실제 상태는 이 컴포넌트가 자신의 세션으로 다시 확인한다.
 */
export function FollowButton({ handle }: { handle: string }) {
  const router = useRouter()
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [following, setFollowing] = useState(false)
  const [resolved, setResolved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isSelf = user?.handle === handle
  const shouldResolve = ready && user !== null && !isSelf

  useEffect(() => {
    if (!shouldResolve) return
    apiGet<{ followedByMe: boolean }>(`/api/v1/users/@${encodeURIComponent(handle)}`)
      .then((profile) => setFollowing(profile.followedByMe))
      .catch(() => undefined)
      .finally(() => setResolved(true))
  }, [shouldResolve, handle])

  // 세션 복구 전, 비로그인, 본인 프로필에서는 버튼을 그리지 않는다.
  // 상태 확인 전에 그리면 "팔로우"였다가 "팔로잉"으로 바뀌어 깜빡인다.
  if (!shouldResolve || !resolved) {
    return null
  }

  const toggle = async () => {
    setBusy(true)
    setError(null)
    try {
      if (following) {
        await apiDelete(`/api/v1/users/@${encodeURIComponent(handle)}/follow`)
        setFollowing(false)
      } else {
        await apiPost(`/api/v1/users/@${encodeURIComponent(handle)}/follow`)
        setFollowing(true)
      }
      // 팔로우 상태가 바뀌면 FOLLOWERS 공개 기록이 보이거나 사라진다
      router.refresh()
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '처리하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={css({ display: 'inline-flex', flexDirection: 'column', gap: '1' })}>
      <button
        type="button"
        onClick={() => void toggle()}
        disabled={busy}
        className={css({
          textStyle: 'caption',
          fontWeight: '600',
          px: '4',
          py: '2',
          rounded: 'full',
          cursor: 'pointer',
          borderWidth: '1px',
          borderStyle: 'solid',
          borderColor: following ? 'border.default' : 'brand.default',
          bg: following ? 'bg.surface' : 'brand.default',
          color: following ? 'fg.muted' : 'fg.onAccent',
          _disabled: { opacity: 0.6 },
        })}
      >
        {following ? '팔로잉' : '팔로우'}
      </button>
      {error && (
        <span role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </span>
      )}
    </div>
  )
}
