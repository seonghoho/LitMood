'use client'

import { useEffect, useState } from 'react'
import { css } from 'styled-system/css'
import { stack } from 'styled-system/patterns'
import { RecordCard } from '@/features/record/RecordCard'
import type { RecordPage, RecordResponse } from '@/features/record/types'
import { apiGet } from '@/shared/lib/api'
import { useAuthStore } from '@/shared/store/auth'

/**
 * 공개 프로필의 기록 목록.
 *
 * 서버가 그린 공개 목록을 그대로 쓰다가, 로그인한 사용자에게는
 * 자신의 세션으로 다시 조회해 교체한다. 팔로워라면 FOLLOWERS 기록이,
 * 본인이라면 비공개 기록까지 포함된 목록이 온다 (F-03-07).
 *
 * 이렇게 나눈 이유: 프로필 페이지는 검색 유입 경로라 캐시된 서버 렌더링이
 * 필요한데(NFR-06), 캐시된 응답에 조회자별 내용을 담을 수는 없다.
 */
export function ProfileRecords({
  handle,
  initialRecords,
  initialTotal,
}: {
  handle: string
  initialRecords: RecordResponse[]
  initialTotal: number
}) {
  const user = useAuthStore((state) => state.user)
  const ready = useAuthStore((state) => state.ready)
  const [records, setRecords] = useState(initialRecords)
  const [total, setTotal] = useState(initialTotal)

  useEffect(() => {
    if (!ready || !user) return

    apiGet<RecordPage>(`/api/v1/users/@${encodeURIComponent(handle)}/records?limit=20`)
      .then((page) => {
        setRecords(page.items)
        setTotal(page.totalCount)
      })
      .catch(() => undefined) // 실패하면 서버가 그린 공개 목록을 유지한다
  }, [ready, user, handle])

  return (
    <div className={stack({ gap: '3' })}>
      {/* 개수와 목록은 반드시 같은 출처에서 나와야 한다.
          제목을 서버 값으로, 목록을 클라이언트 값으로 두면 "기록 2"인데
          3개가 보이는 어긋남이 생긴다. */}
      <h2 className={css({ textStyle: 'title' })}>
        기록 <span className={css({ color: 'fg.muted' })}>{total}</span>
      </h2>

      {records.length === 0 ? (
        <p className={css({ textStyle: 'body', color: 'fg.muted' })}>
          아직 공개된 기록이 없습니다.
        </p>
      ) : (
        records.map((record) => <RecordCard key={record.id} record={record} />)
      )}
    </div>
  )
}
