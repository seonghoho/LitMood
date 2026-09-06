'use client'

import { useState } from 'react'
import { css } from 'styled-system/css'
import { ApiError } from '@litmood/api-client'
import { apiDelete, apiPost } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

interface LikeResponse {
  likeCount: number
  likedByMe: boolean
}

/**
 * 좋아요의 대상 (F-06-03 — 기록·컬렉션 둘 다).
 * 기록은 id 로, 컬렉션은 공유 주소인 slug 로 가리킨다.
 */
export type LikeTarget = { kind: 'record'; id: number } | { kind: 'collection'; slug: string }

function likePath(target: LikeTarget): string {
  return target.kind === 'record'
    ? `/api/v1/records/${target.id}/like`
    : `/api/v1/collections/${encodeURIComponent(target.slug)}/like`
}

/** F-06-03 — 좋아요. 서버 응답으로 카운트를 확정해 낙관적 갱신의 어긋남을 없앤다. */
export function LikeButton({
  target,
  initialCount,
  initialLiked,
}: {
  target: LikeTarget
  initialCount: number
  initialLiked: boolean
}) {
  const signedIn = useAuthStore((state) => state.user !== null)
  const [count, setCount] = useState(initialCount)
  const [liked, setLiked] = useState(initialLiked)
  const [busy, setBusy] = useState(false)

  const toggle = async () => {
    if (!signedIn || busy) return
    setBusy(true)
    try {
      const path = likePath(target)
      // 취소도 갱신된 개수를 돌려준다 — 직접 빼면 다른 사람이 그사이 누른 것을 놓친다
      const result = liked ? await apiDelete<LikeResponse>(path) : await apiPost<LikeResponse>(path)
      setCount(result.likeCount)
      setLiked(result.likedByMe)
    } catch (error) {
      // 이미 다른 탭에서 처리됐을 수 있다. 조용히 무시하고 상태를 그대로 둔다.
      if (!(error instanceof ApiError)) throw error
    } finally {
      setBusy(false)
    }
  }

  return (
    <button
      type="button"
      onClick={() => void toggle()}
      disabled={!signedIn || busy}
      aria-pressed={liked}
      aria-label={liked ? '좋아요 취소' : '좋아요'}
      className={css({
        display: 'inline-flex',
        alignItems: 'center',
        gap: '1',
        textStyle: 'caption',
        px: '2',
        py: '1',
        rounded: 'full',
        cursor: signedIn ? 'pointer' : 'default',
        bg: 'transparent',
        color: liked ? 'brand.default' : 'fg.muted',
        _disabled: { cursor: 'default' },
      })}
    >
      <span aria-hidden="true">{liked ? '♥' : '♡'}</span>
      {count > 0 && <span>{count}</span>}
    </button>
  )
}
