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

/** F-06-03 — 좋아요. 서버 응답으로 카운트를 확정해 낙관적 갱신의 어긋남을 없앤다. */
export function LikeButton({
  recordId,
  initialCount,
  initialLiked,
}: {
  recordId: number
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
      const result = liked
        ? await apiDelete(`/api/v1/records/${recordId}/like`).then(
            () => ({ likeCount: count - 1, likedByMe: false }) as LikeResponse,
          )
        : await apiPost<LikeResponse>(`/api/v1/records/${recordId}/like`)
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
