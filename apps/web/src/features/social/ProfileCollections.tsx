'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { ApiError } from '@litmood/api-client'
import type { CollectionSummary } from '@/features/collection/types'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/**
 * 공개 프로필의 컬렉션 목록. ProfileRecords 와 같은 이유로 두 단계로 그린다 —
 * 서버가 공개 목록을 그리고, 로그인한 조회자에게는 자신의 세션으로 다시 조회한다.
 *
 * 재조회가 필요한 경우가 둘이다.
 * - 팔로워에게는 FOLLOWERS 컬렉션이, 본인에게는 비공개 컬렉션까지 보여야 한다
 * - 차단 관계면 서버가 404 를 주는데, 그때 공개 목록을 그대로 두면
 *   차단하고도 상대 컬렉션이 계속 보인다 (이슈 #17)
 */
export function ProfileCollections({
  handle,
  initialCollections,
}: {
  handle: string
  initialCollections: CollectionSummary[]
}) {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [collections, setCollections] = useState(initialCollections)
  const [blocked, setBlocked] = useState(false)

  useEffect(() => {
    if (!ready || !user) return

    apiGet<CollectionSummary[]>(`/api/v1/users/@${encodeURIComponent(handle)}/collections`)
      .then(setCollections)
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.problem.status === 404) {
          setBlocked(true)
          setCollections([])
        }
      })
  }, [ready, user, handle])

  // 차단 안내는 기록 쪽에서 한 번만 한다 — 같은 문구를 두 번 보일 이유가 없다
  if (blocked || collections.length === 0) return null

  return (
    <section className={stack({ gap: '3' })}>
      <h2 className={css({ textStyle: 'title' })}>
        컬렉션 <span className={css({ color: 'fg.muted' })}>{collections.length}</span>
      </h2>
      <div className={stack({ gap: '2' })}>
        {collections.map((collection) => (
          <Link
            key={collection.slug}
            href={`/collections/${collection.slug}`}
            className={css({
              display: 'block',
              p: '4',
              rounded: 'md',
              bg: 'bg.surface',
              borderWidth: '1px',
              borderStyle: 'solid',
              borderColor: 'border.default',
            })}
          >
            <span className={css({ textStyle: 'body', fontWeight: '600', color: 'fg.default' })}>
              {collection.title}
            </span>
            <span className={css({ textStyle: 'caption', color: 'fg.muted', ml: '2' })}>
              {collection.itemCount}개
            </span>
          </Link>
        ))}
      </div>
    </section>
  )
}
