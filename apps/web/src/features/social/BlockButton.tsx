'use client'

import { useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { flex, stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { apiDelete, apiGet, apiPost } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/**
 * 차단 / 차단 해제 (F-06-05).
 *
 * 프로필 페이지는 캐시된 서버 렌더링이라 blockedByMe 가 언제나 false 로 내려온다.
 * 실제 상태는 이 컴포넌트가 자신의 세션으로 다시 확인한다 (FollowButton 과 같은 패턴).
 *
 * 서버의 blockedByMe 는 <b>내가 건 차단</b>만 가리킨다. 가림 판정은 양방향이지만,
 * 상대가 나를 차단한 것을 내가 풀 수는 없으므로 버튼은 단방향으로 그린다.
 */
export function BlockButton({ handle }: { handle: string }) {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [blocked, setBlocked] = useState(false)
  const [resolved, setResolved] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isSelf = user?.handle === handle
  const shouldResolve = ready && user !== null && !isSelf

  useEffect(() => {
    if (!shouldResolve) return
    apiGet<{ blockedByMe: boolean }>(`/api/v1/users/@${encodeURIComponent(handle)}`)
      .then((profile) => setBlocked(profile.blockedByMe))
      .catch(() => undefined)
      .finally(() => setResolved(true))
  }, [shouldResolve, handle])

  if (!shouldResolve || !resolved) return null

  const apply = async (next: boolean) => {
    setBusy(true)
    setError(null)
    try {
      const path = `/api/v1/users/@${encodeURIComponent(handle)}/block`
      if (next) {
        await apiPost(path)
      } else {
        await apiDelete(path)
      }
      // 차단은 페이지 전체가 보여줄 내용을 바꾼다 — 기록·컬렉션 목록도 다시 판정해야 한다.
      // 각 목록이 스스로 다시 조회하게 두는 것보다 한 번 새로 그리는 편이 어긋날 여지가 없다.
      window.location.reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '처리하지 못했습니다')
      setBusy(false)
    }
  }

  if (confirming) {
    return (
      <div className={stack({ gap: '2', alignItems: 'flex-start' })}>
        <p className={css({ textStyle: 'caption', color: 'fg.default' })}>
          @{handle} 님을 차단할까요? 서로의 기록이 보이지 않게 되고 팔로우가 해제됩니다.
        </p>
        <div className={flex({ gap: '2' })}>
          <button
            type="button"
            onClick={() => void apply(true)}
            disabled={busy}
            className={css({
              textStyle: 'caption',
              px: '3',
              py: '1.5',
              rounded: 'md',
              cursor: 'pointer',
              bg: 'danger.500',
              color: 'fg.onAccent',
              _disabled: { opacity: 0.6 },
            })}
          >
            {busy ? '차단하는 중…' : '차단'}
          </button>
          <button
            type="button"
            onClick={() => setConfirming(false)}
            disabled={busy}
            className={secondaryStyle}
          >
            취소
          </button>
        </div>
        {error && (
          <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
            {error}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className={stack({ gap: '1', alignItems: 'flex-start' })}>
      <button
        type="button"
        onClick={() => (blocked ? void apply(false) : setConfirming(true))}
        disabled={busy}
        className={secondaryStyle}
      >
        {blocked ? '차단 해제' : '차단'}
      </button>
      {error && (
        <p role="alert" className={css({ textStyle: 'caption', color: 'danger.500' })}>
          {error}
        </p>
      )}
    </div>
  )
}

const secondaryStyle = css({
  textStyle: 'caption',
  px: '3',
  py: '1.5',
  rounded: 'md',
  cursor: 'pointer',
  bg: 'bg.subtle',
  color: 'fg.muted',
  _disabled: { opacity: 0.6 },
})
