'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import { RecordCard } from '@/features/record/RecordCard'
import type { RecordPage } from '@/features/record/types'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/** F-06-02 — 팔로잉 피드. */
export function FeedView() {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)

  const [items, setItems] = useState<RecordPage['items']>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (nextCursor: string | null, append: boolean) => {
    setLoading(true)
    setError(null)
    try {
      const params = new URLSearchParams()
      if (nextCursor) params.set('cursor', nextCursor)

      const page = await apiGet<RecordPage>(`/api/v1/records/feed?${params.toString()}`)
      setItems((prev) => (append ? [...prev, ...page.items] : page.items))
      setCursor(page.nextCursor)
    } catch (e) {
      setError(e instanceof ApiError ? e.problem.title : '피드를 불러오지 못했습니다')
    } finally {
      setLoading(false)
      setLoaded(true)
    }
  }, [])

  useEffect(() => {
    if (ready && user) {
      void load(null, false)
    }
  }, [ready, user, load])

  if (!ready) {
    return <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>불러오는 중…</p>
  }

  if (!user) {
    return (
      <div className={stack({ gap: '4', alignItems: 'flex-start' })}>
        <p className={css({ textStyle: 'body', color: 'fg.default' })}>
          피드를 보려면 로그인이 필요합니다.
        </p>
        <Link href="/login?next=/feed" className={primaryLink}>
          로그인
        </Link>
      </div>
    )
  }

  return (
    <div className={stack({ gap: '4' })}>
      {error && (
        <p role="alert" className={css({ textStyle: 'body', color: 'danger.500' })}>
          {error}
        </p>
      )}

      {loaded && items.length === 0 && !error && (
        <div className={stack({ gap: '3', alignItems: 'flex-start', py: '8' })}>
          <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
            아직 팔로우한 사람이 없습니다.
          </p>
          <p className={css({ textStyle: 'caption', color: 'fg.muted' })}>
            무드 페이지에서 같은 기분으로 기록한 사람들을 찾아보세요.
          </p>
          <Link href="/moods/새벽" className={primaryLink}>
            무드로 둘러보기
          </Link>
        </div>
      )}

      <div className={stack({ gap: '3' })}>
        {items.map((record) => (
          <RecordCard key={record.id} record={record} showAuthor />
        ))}
      </div>

      {cursor && (
        <button
          type="button"
          onClick={() => void load(cursor, true)}
          disabled={loading}
          className={css({
            textStyle: 'body',
            px: '4',
            py: '3',
            rounded: 'md',
            cursor: 'pointer',
            bg: 'bg.subtle',
            color: 'fg.default',
            _disabled: { opacity: 0.6 },
          })}
        >
          {loading ? '불러오는 중…' : '더 보기'}
        </button>
      )}
    </div>
  )
}

const primaryLink = css({
  textStyle: 'body',
  fontWeight: '600',
  px: '4',
  py: '2',
  rounded: 'md',
  bg: 'brand.default',
  color: 'fg.onAccent',
})
