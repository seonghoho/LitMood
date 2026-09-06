'use client'

import { useEffect, useState } from 'react'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'
import { LikeButton } from './LikeButton'

/**
 * 컬렉션 좋아요 (F-06-03).
 *
 * 컬렉션 공개 페이지는 SEO 를 위해 캐시되고 그 서버 렌더링은 인증 없이 수행된다.
 * 그래서 서버가 내려준 likedByMe 는 언제나 false 다 — 좋아요 <b>수</b>는 서버 값을
 * 그대로 쓰고, "내가 눌렀는지"만 조회자 자신의 세션으로 다시 확인한다
 * (docs/03-architecture.md "캐싱과 개인화의 경계", FollowButton 과 같은 패턴).
 */
export function CollectionLikeButton({
  slug,
  initialCount,
}: {
  slug: string
  initialCount: number
}) {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [liked, setLiked] = useState(false)
  // 서버가 준 개수는 캐시된 값이라 조금 뒤처질 수 있다. 어차피 조회하는 김에 같이 받아 갱신한다.
  const [count, setCount] = useState(initialCount)
  const [resolved, setResolved] = useState(false)

  useEffect(() => {
    if (!ready) return
    // 비로그인 조회자는 물어볼 것이 없다 — 눌린 적 없는 상태로 바로 그린다
    if (!user) {
      setResolved(true)
      return
    }
    apiGet<{ likedByMe: boolean; likeCount: number }>(
      `/api/v1/collections/${encodeURIComponent(slug)}`,
    )
      .then((collection) => {
        setLiked(collection.likedByMe)
        setCount(collection.likeCount)
      })
      .catch(() => undefined)
      .finally(() => setResolved(true))
  }, [ready, user, slug])

  // 확인 전에 그리면 ♡ 였다가 ♥ 로 바뀌어 깜빡인다
  if (!resolved) return null

  return (
    <LikeButton target={{ kind: 'collection', slug }} initialCount={count} initialLiked={liked} />
  )
}
